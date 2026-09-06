package in.chalkbase.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.chalkbase.TestcontainersConfiguration;
import in.chalkbase.platform.api.PageResponse;
import in.chalkbase.platform.tenancy.SchoolProvisioning;
import in.chalkbase.platform.tenancy.TenantContext;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The pair of tests that prove the two transaction semantics of ADR-0018, which are the decision.
 *
 * <p>A data change is audited in the caller's transaction (§3): roll the change back and the audit
 * row goes with it, so the log can always be reconciled against the data. A security event is
 * audited in its own (§4): it survives the surrounding rollback, because a failed sign-in must be
 * recorded <em>because</em> it failed.
 *
 * <p>Someone will eventually try to merge {@code recordChange} and {@code recordSecurityEvent} into
 * one method. These two tests are what stops that being a silent change.
 *
 * <p>Deliberately not {@code @Transactional}: a test that rolls everything back at the end cannot
 * tell a committed row from an uncommitted one, which is the only thing being asserted here.
 *
 * <p>Every fixture is an invented school. Never real student data.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, AuditTransactionSemanticsTests.AuditedWorkConfiguration.class})
class AuditTransactionSemanticsTests {

    private static final String BROOKFIELD = "brookfield";
    private static final String MARIGOLD = "marigold";

    /** What a field name may look like. The structural half of "values are never recorded". */
    private static final Pattern FIELD_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*$");

    @Autowired
    SchoolProvisioning provisioning;

    @Autowired
    AuditedWork work;

    @Autowired
    AuditReader reader;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void provisionAndClear() {
        for (String schema : List.of(BROOKFIELD, MARIGOLD)) {
            provisioning.provision(schema);
            jdbc.sql("delete from " + schema + ".audit_event").update();
        }
    }

    // ── The two semantics ────────────────────────────────────────────────────────────────────

    /**
     * ADR-0018 §3. An audit log that records changes which did not happen is worse than one with
     * gaps, because it cannot be reconciled against the data it claims to describe.
     */
    @Test
    void aChangeAuditedInsideATransactionThatRollsBackLeavesNoAuditRow() {
        String studentId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> inTenant(BROOKFIELD, () -> {
                    work.auditAChangeThenFail(studentId);
                    return null;
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("the change did not happen");

        assertThat(rowsAbout(BROOKFIELD, studentId))
                .as("a rolled-back change takes its audit row with it")
                .isZero();
    }

    /**
     * ADR-0018 §4, and the reason the two methods exist separately. The same surrounding failure,
     * the opposite outcome — because what a security event answers is "what did someone attempt",
     * and the attempts worth recording are exactly the ones that did not work.
     */
    @Test
    void aSecurityEventSurvivesTheSurroundingTransactionRollingBack() {
        String studentId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> inTenant(BROOKFIELD, () -> {
                    work.auditASecurityEventThenFail(studentId);
                    return null;
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("the export failed");

        assertThat(rowsAbout(BROOKFIELD, studentId))
                .as("a security event is recorded whether or not the surrounding work succeeded")
                .isEqualTo(1);
        assertThat(actionOf(BROOKFIELD, studentId)).isEqualTo(AuditAction.DATA_EXPORTED);
    }

    /** The committing case, so the rollback tests are not passing because nothing is ever written. */
    @Test
    void aChangeAuditedInATransactionThatCommitsLeavesExactlyOneRow() {
        String studentId = UUID.randomUUID().toString();

        inTenant(BROOKFIELD, () -> {
            work.auditAChange(studentId, List.of("section"));
            return null;
        });

        assertThat(rowsAbout(BROOKFIELD, studentId)).isEqualTo(1);
        assertThat(actionOf(BROOKFIELD, studentId)).isEqualTo(AuditAction.ENTITY_UPDATED);
    }

    // ── Values are never recorded (ADR-0014) ─────────────────────────────────────────────────

    /**
     * The representative update. Three fields changed on a student; the log says which three and
     * says nothing whatsoever about what they became.
     *
     * <p>Asserted structurally rather than by looking for a particular value, because a test that
     * greps for "9876543210" only catches the value the test author thought of. Every entry has to
     * be a field name and nothing else — no separator, no arrow, no quote, no digit-bearing value
     * hiding after an equals sign.
     */
    @Test
    void changedFieldsNeverContainsAValue() {
        String studentId = UUID.randomUUID().toString();

        inTenant(BROOKFIELD, () -> {
            work.auditAChange(studentId, List.of("section", "guardian.phone", "displayName"));
            return null;
        });

        String stored = jdbc.sql("select changed_fields from " + BROOKFIELD + ".audit_event where entity_id = ?")
                .param(studentId)
                .query(String.class)
                .single();

        // Sorted and comma-separated, so two schools' rows for the same edit read identically.
        assertThat(stored).isEqualTo("displayName,guardian.phone,section");
        assertThat(stored)
                .as("no separator that could carry a value alongside a name")
                .doesNotContain("=", ":", "->", " ", "\"", "'");
        for (String field : stored.split(",")) {
            assertThat(FIELD_NAME.matcher(field).matches())
                    .as("%s is a field name and nothing else", field)
                    .isTrue();
        }

        // And nothing anywhere else on the row carries one either.
        assertThat(jdbc.sql("select coalesce(actor_name, '') || coalesce(entity_type, '') from " + BROOKFIELD
                                + ".audit_event where entity_id = ?")
                        .param(studentId)
                        .query(String.class)
                        .single())
                .doesNotContain("=");
    }

    /**
     * The enforcement half. ADR-0014 is a rule that has to survive every future change made by
     * someone who has not read it, so writing a value into {@code changedFields} is refused rather
     * than reviewed — and refused before anything is written, not after.
     */
    @Test
    void refusesAValueDisguisedAsAChangedFieldName() {
        String studentId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> inTenant(BROOKFIELD, () -> {
                    work.auditAChange(studentId, List.of("section", "phone=9876543210"));
                    return null;
                }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field NAMES, never values");

        assertThat(rowsAbout(BROOKFIELD, studentId))
                .as("nothing is written when the call is refused")
                .isZero();
    }

    // ── Tenancy ──────────────────────────────────────────────────────────────────────────────

    /**
     * The audit log is the school's record of itself and lives in the school's schema (ADR-0018
     * §5). One school's events are not another school's business, and there is no {@code school_id}
     * to get wrong — the schema is the boundary (ADR-0011).
     */
    @Test
    void anEventRecordedForOneSchoolIsInvisibleFromAnother() {
        String studentId = UUID.randomUUID().toString();

        inTenant(BROOKFIELD, () -> {
            work.auditAChange(studentId, List.of("section"));
            return null;
        });

        PageResponse<AuditEventResponse> atBrookfield =
                inTenant(BROOKFIELD, () -> reader.search(AuditQuery.all(), PageRequest.of(0, 25)));
        PageResponse<AuditEventResponse> atMarigold =
                inTenant(MARIGOLD, () -> reader.search(AuditQuery.all(), PageRequest.of(0, 25)));

        assertThat(atBrookfield.content())
                .extracting(AuditEventResponse::entityId)
                .contains(studentId);
        assertThat(atMarigold.totalElements())
                .as("marigold cannot see brookfield's audit log at all")
                .isZero();
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private int rowsAbout(String schema, String entityId) {
        return jdbc.sql("select count(*) from " + schema + ".audit_event where entity_id = ?")
                .param(entityId)
                .query(Integer.class)
                .single();
    }

    private String actionOf(String schema, String entityId) {
        return jdbc.sql("select action from " + schema + ".audit_event where entity_id = ?")
                .param(entityId)
                .query(String.class)
                .single();
    }

    private <T> T inTenant(String schema, Callable<T> work) {
        try {
            return TenantContext.callWith(schema, work);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * A stand-in for the service methods other modules will write: something that audits and then
     * decides the work did not happen after all.
     *
     * <p>A real bean rather than a call to {@code AuditService} directly, because the whole point is
     * the transaction the caller opened — and {@code @Transactional} only means anything across a
     * proxy boundary.
     */
    static class AuditedWork {

        private final AuditService audit;

        AuditedWork(AuditService audit) {
            this.audit = audit;
        }

        @Transactional
        public void auditAChange(String studentId, List<String> changedFields) {
            audit.recordChange(AuditAction.ENTITY_UPDATED, "STUDENT", studentId, changedFields);
        }

        @Transactional
        public void auditAChangeThenFail(String studentId) {
            audit.recordChange(AuditAction.ENTITY_UPDATED, "STUDENT", studentId, List.of("section"));
            throw new IllegalStateException("the change did not happen");
        }

        @Transactional
        public void auditASecurityEventThenFail(String studentId) {
            audit.recordSecurityEvent(AuditAction.DATA_EXPORTED, AuditOutcome.SUCCESS, "STUDENT", studentId);
            throw new IllegalStateException("the export failed after the attempt was made");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AuditedWorkConfiguration {

        @Bean
        AuditedWork auditedWork(AuditService audit) {
            return new AuditedWork(audit);
        }
    }
}
