package in.chalkbase.school.application;

import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.ChalkbaseException;
import in.chalkbase.platform.error.NotFoundException;
import in.chalkbase.platform.tenancy.TenantContext;
import in.chalkbase.school.api.SchoolProfileResponse;
import in.chalkbase.school.api.UpdateSchoolProfileRequest;
import in.chalkbase.school.domain.School;
import in.chalkbase.school.domain.SchoolErrorCode;
import in.chalkbase.school.domain.SchoolProfile;
import in.chalkbase.school.infrastructure.SchoolProfileRepository;
import in.chalkbase.school.infrastructure.SchoolRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading and editing the current school's own profile.
 *
 * <p>"The current school" is the tenant bound to this request, and it is the only school this
 * service can reach. There is no id parameter anywhere here, and adding one would be the beginning
 * of a way for one school to edit another's details.
 *
 * <p>Each call touches two schemas, and it is worth being clear about which and why. The profile
 * itself lives in the tenant schema and is reached through {@code search_path}. The registry row in
 * {@code public.school} is written too, because it keeps a copy of the name, board and town so the
 * platform can list schools without binding a tenant — leaving that copy stale would make the
 * school register disagree with the school. Both writes share one transaction and one connection.
 *
 * <p>This is the first endpoint in the system that changes anything, so it is the first to audit
 * (ADR-0018). The audit row joins this transaction: if the save rolls back the log does not claim
 * an edit the database never took.
 */
@Service
@Transactional(readOnly = true)
public class SchoolProfileService {

    private final SchoolRepository schools;
    private final SchoolProfileRepository profiles;
    private final AuditService audit;

    public SchoolProfileService(SchoolRepository schools, SchoolProfileRepository profiles, AuditService audit) {
        this.schools = schools;
        this.profiles = profiles;
        this.audit = audit;
    }

    /**
     * The profile of the school this session belongs to.
     *
     * <p>A school that has never saved one is not an error: the row simply does not exist yet, and
     * the answer is the registry's own details with the rest empty and {@code configured: false}.
     * A 404 here would be read by a client as "no such school", which is the opposite of true.
     */
    public SchoolProfileResponse currentProfile() {
        return SchoolProfileResponse.of(
                currentSchool(), profiles.findFirstByOrderByCreatedAtAsc().orElse(null));
    }

    /**
     * Replaces the profile, creating it the first time.
     *
     * <p>An upsert rather than separate create and update endpoints: from the school's point of
     * view there is one profile that always exists conceptually, and asking a client to know
     * whether the row has been written yet is asking it to know something about our schema.
     */
    @Transactional
    public SchoolProfileResponse update(UpdateSchoolProfileRequest request) {
        School school = currentSchool();
        rejectAnAttemptToChangeIdentity(school, request);

        SchoolProfile profile = profiles.findFirstByOrderByCreatedAtAsc().orElse(null);
        boolean creating = profile == null;
        Set<String> changed = creating ? suppliedFields(request) : changedFields(profile, school, request);
        if (creating) {
            profile = new SchoolProfile(
                    request.addressLine1(),
                    blankToNull(request.addressLine2()),
                    request.city(),
                    request.state(),
                    request.pincode(),
                    request.principalName(),
                    request.phone(),
                    request.email(),
                    blankToNull(request.website()),
                    blankToNull(request.affiliationNumber()),
                    request.board());
        } else {
            profile.apply(
                    request.addressLine1(),
                    blankToNull(request.addressLine2()),
                    request.city(),
                    request.state(),
                    request.pincode(),
                    request.principalName(),
                    request.phone(),
                    request.email(),
                    blankToNull(request.website()),
                    blankToNull(request.affiliationNumber()),
                    request.board());
        }
        SchoolProfile saved = profiles.saveAndFlush(profile);

        school.updateRegistryDetails(request.name(), request.board(), request.city(), request.state());
        schools.saveAndFlush(school);

        // Nothing recorded when nothing changed. A row saying "the profile was updated, no fields
        // differed" is noise in the one log that has to stay worth reading, and a form resubmitted
        // unchanged is the commonest way to produce one.
        if (creating || !changed.isEmpty()) {
            audit.recordChange(
                    creating ? AuditAction.ENTITY_CREATED : AuditAction.ENTITY_UPDATED,
                    "SCHOOL_PROFILE",
                    school.getCode(),
                    changed);
        }

        return SchoolProfileResponse.of(school, saved);
    }

    /**
     * Which fields this request actually changes — names only, never values (ADR-0014).
     *
     * <p>Compared before the entity is mutated, because afterwards there is nothing to compare
     * against. The registry-backed {@code name} is included: it is part of what the caller edited
     * on this screen even though it is stored in {@code public.school} rather than in the profile.
     */
    private static Set<String> changedFields(SchoolProfile before, School school, UpdateSchoolProfileRequest request) {
        Set<String> changed = new LinkedHashSet<>();
        record Field(String name, Object before, Object after) {}
        for (Field field : new Field[] {
            new Field("name", school.getName(), request.name()),
            new Field("board", before.getBoard(), request.board()),
            new Field("addressLine1", before.getAddressLine1(), request.addressLine1()),
            new Field("addressLine2", before.getAddressLine2(), blankToNull(request.addressLine2())),
            new Field("city", before.getCity(), request.city()),
            new Field("state", before.getState(), request.state()),
            new Field("pincode", before.getPincode(), request.pincode()),
            new Field("principalName", before.getPrincipalName(), request.principalName()),
            new Field("phone", before.getPhone(), request.phone()),
            new Field("email", before.getEmail(), request.email()),
            new Field("website", before.getWebsite(), blankToNull(request.website())),
            new Field("affiliationNumber", before.getAffiliationNumber(), blankToNull(request.affiliationNumber()))
        }) {
            if (!Objects.equals(field.before(), field.after())) {
                changed.add(field.name());
            }
        }
        return changed;
    }

    /** On the first save there is nothing to diff against, so every field that was filled in counts. */
    private static Set<String> suppliedFields(UpdateSchoolProfileRequest request) {
        Set<String> supplied = new LinkedHashSet<>(
                Set.of("name", "board", "addressLine1", "city", "state", "pincode", "principalName", "phone", "email"));
        if (blankToNull(request.addressLine2()) != null) {
            supplied.add("addressLine2");
        }
        if (blankToNull(request.website()) != null) {
            supplied.add("website");
        }
        if (blankToNull(request.affiliationNumber()) != null) {
            supplied.add("affiliationNumber");
        }
        return supplied;
    }

    /**
     * The registry row for the schema this request is bound to.
     *
     * <p>An unbound request cannot get here in practice — both endpoints require a session, and the
     * session is what binds the tenant — so reaching the platform schema means something upstream
     * is wrong, and answering with {@code public}'s non-existent school is better than answering
     * with somebody's.
     */
    private School currentSchool() {
        String schema = TenantContext.currentSchemaOrPlatform();
        if (TenantContext.PLATFORM.equals(schema)) {
            throw new NotFoundException("School profile for schema", schema);
        }
        return schools.findBySchemaName(schema).orElseThrow(() -> new NotFoundException("School for schema", schema));
    }

    /**
     * Refuses an update that would rename the tenant.
     *
     * <p>Both fields address the school rather than describe it: the code is typed on the sign-in
     * form and the schema name is where every row this school owns lives. Ignoring a changed value
     * would be worse than refusing it — the client would be told the save succeeded and go on
     * believing the school now has a different code.
     */
    private static void rejectAnAttemptToChangeIdentity(School school, UpdateSchoolProfileRequest request) {
        Map<String, String> offending = new LinkedHashMap<>();
        if (request.code() != null && !request.code().equals(school.getCode())) {
            offending.put("code", "The school code is fixed at onboarding and cannot be changed");
        }
        if (request.schemaName() != null && !request.schemaName().equals(school.getSchemaName())) {
            offending.put("schemaName", "The schema name is fixed at onboarding and cannot be changed");
        }
        if (!offending.isEmpty()) {
            throw new ChalkbaseException(
                    SchoolErrorCode.IMMUTABLE_IDENTITY, SchoolErrorCode.IMMUTABLE_IDENTITY.defaultMessage(), offending);
        }
    }

    /**
     * An empty optional field is absent, not empty.
     *
     * <p>A browser sends {@code ""} for a text box nobody typed in. Storing that leaves the
     * database with two ways to say "no website", and every later query having to test for both.
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
