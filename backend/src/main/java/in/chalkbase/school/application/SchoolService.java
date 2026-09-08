package in.chalkbase.school.application;

import in.chalkbase.platform.error.ChalkbaseException;
import in.chalkbase.platform.error.NotFoundException;
import in.chalkbase.platform.reference.ReferenceItemResponse;
import in.chalkbase.platform.tenancy.FirstAdminAccount;
import in.chalkbase.platform.tenancy.FirstAdminProvisioner;
import in.chalkbase.platform.tenancy.SchoolProvisioning;
import in.chalkbase.platform.tenancy.TenantContext;
import in.chalkbase.school.api.BootstrapSchoolRequest;
import in.chalkbase.school.api.CreateSchoolRequest;
import in.chalkbase.school.api.SchoolBootstrapResponse;
import in.chalkbase.school.api.SchoolLookup;
import in.chalkbase.school.api.SchoolRef;
import in.chalkbase.school.api.SchoolResponse;
import in.chalkbase.school.domain.Board;
import in.chalkbase.school.domain.School;
import in.chalkbase.school.domain.SchoolErrorCode;
import in.chalkbase.school.infrastructure.SchoolRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SchoolService implements SchoolLookup {

    private final SchoolRepository schools;
    private final SchoolProvisioning provisioning;
    private final FirstAdminProvisioner firstAdminProvisioner;

    public SchoolService(
            SchoolRepository schools, SchoolProvisioning provisioning, FirstAdminProvisioner firstAdminProvisioner) {
        this.schools = schools;
        this.provisioning = provisioning;
        this.firstAdminProvisioner = firstAdminProvisioner;
    }

    public List<SchoolResponse> findAll() {
        return schools.findAll().stream().map(SchoolResponse::from).toList();
    }

    /**
     * Implements {@link SchoolLookup} for other modules. Reads {@code public.school}, so it must be
     * called with no tenant bound — identity calls it precisely to find out which one to bind.
     */
    @Override
    public Optional<SchoolRef> byCode(String code) {
        return schools.findByCodeAndActiveIsTrue(code)
                .map(school -> new SchoolRef(
                        school.getCode(), school.getName(), school.getSchemaName(), school.getTimezone()));
    }

    public SchoolResponse findById(UUID id) {
        return schools.findById(id).map(SchoolResponse::from).orElseThrow(() -> new NotFoundException("School", id));
    }

    /**
     * Every board a school may affiliate to, labelled the way Indian schools actually name them
     * (ADR-0029). Enum declaration order, which is already the order the school-profile form
     * presented them in before this existed.
     */
    public List<ReferenceItemResponse> boards() {
        return Arrays.stream(Board.values())
                .map(board -> new ReferenceItemResponse(board.name(), board.label()))
                .toList();
    }

    /**
     * Registers a school and brings its schema online.
     *
     * <p>The registry row is committed before the schema is provisioned, deliberately: Flyway runs
     * its own transactions and DDL cannot join this one, so doing it the other way round would leave
     * an orphaned schema if the insert failed. A registered school whose schema is missing is
     * recoverable — the next startup migrates it — while an unregistered schema is invisible.
     */
    @Transactional
    public SchoolResponse create(CreateSchoolRequest request) {
        School school = new School(
                request.code(), request.name(), request.schemaName(), request.board(), request.city(), request.state());
        SchoolResponse saved = SchoolResponse.from(schools.saveAndFlush(school));
        provisioning.provision(school.getSchemaName());
        return saved;
    }

    /**
     * Brings one school online and creates its first administrator (ADR-0024) — the only path that
     * turns an empty deployment into one somebody can sign in to over HTTP.
     *
     * <p><strong>Not one database transaction, and deliberately so.</strong> The registry row lives
     * in {@code public}; the account lives inside the new tenant's schema; and Hibernate's
     * multi-tenant session picks its schema when a transaction opens, not per statement — the same
     * constraint {@code identity.application.AuthenticationService} documents for login. Wrapping
     * both halves in one {@code @Transactional} here would silently run the account write against
     * {@code public} instead of the school's schema. Each half is transactional on its own instead:
     * the registry write through {@link SchoolRepository}'s own per-call transaction, the schema
     * through {@link SchoolProvisioning#provision} (idempotent by its own contract), and the account
     * through {@link FirstAdminProvisioner#provisionFirstAdmin}, entered only once the tenant is
     * bound below.
     *
     * <p><strong>Idempotent up to the point that matters, then refuses.</strong> Retrying with the
     * same {@code code} reuses the already-registered school rather than failing on the unique
     * constraint, and re-provisioning an existing schema is a no-op past the first call. What does
     * not repeat is the account: {@link FirstAdminProvisioner} throws {@code AUTH_014} once the
     * schema already holds one, so two <em>sequential</em> calls for the same school never produce
     * two administrators — the second one fails instead, which is the whole point (ADR-0024).
     *
     * <p>Synchronizes on {@link #provisioning} — the same object {@link SchoolProvisioning#provision}
     * locks on internally — so two <em>concurrent</em> calls for the same school are serialised
     * rather than both reading "no account yet" before either has written one. This is a single-JVM
     * guarantee, the same scope {@code SchoolProvisioning} already accepts for provisioning itself
     * (ADR-0011 notes replicas are not otherwise coordinated); a multi-replica deployment would need
     * a database-level lock to close the same race across instances, which is not a shape this
     * deployment runs today.
     *
     * <p>{@code Propagation.NEVER} is not decorative. This class carries
     * {@code @Transactional(readOnly = true)} at the class level for its query methods, which would
     * otherwise wrap this method in one ambient transaction too — opened before {@link TenantContext}
     * is bound below, which is precisely the failure mode the two paragraphs above describe. Failing
     * loudly if this is ever called from inside a transaction is safer than silently inheriting one.
     */
    @Transactional(propagation = Propagation.NEVER)
    public SchoolBootstrapResponse bootstrap(BootstrapSchoolRequest request) {
        synchronized (provisioning) {
            School school = schools.findByCodeAndActiveIsTrue(request.code()).orElseGet(() -> registerSchool(request));
            if (!school.getSchemaName().equals(request.schemaName())) {
                throw new ChalkbaseException(SchoolErrorCode.BOOTSTRAP_SCHEMA_MISMATCH);
            }

            provisioning.provision(school.getSchemaName());

            FirstAdminAccount admin = inSchema(
                    school.getSchemaName(),
                    () -> firstAdminProvisioner.provisionFirstAdmin(
                            school.getSchemaName(), request.adminUsername(), request.adminDisplayName()));

            return new SchoolBootstrapResponse(
                    SchoolResponse.from(school), admin.accountId(), admin.username(), admin.temporaryPassword(), true);
        }
    }

    private School registerSchool(BootstrapSchoolRequest request) {
        return schools.saveAndFlush(new School(
                request.code(),
                request.name(),
                request.schemaName(),
                request.board(),
                request.city(),
                request.state()));
    }

    /** Mirrors {@code AuthenticationService#inSchool} and {@code AuditService}'s own tenant-binding helper. */
    private static <T> T inSchema(String schema, Callable<T> work) {
        try {
            return TenantContext.callWith(schema, work);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Bootstrap failed for schema " + schema, ex);
        }
    }
}
