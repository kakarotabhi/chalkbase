package in.chalkbase.platform.security;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Every permission the running build knows about, collected from the modules at startup.
 *
 * <p>This is the source of truth for permissions. The {@code permission} table in each school's
 * schema is a seeded copy, present so a role can carry a foreign key and a principal can read a
 * real list in a UI — never the other way round (ADR-0005).
 *
 * <p>Validation happens here, once, at startup rather than at the first insert: a malformed code or
 * a code claimed by two modules stops the application instead of reaching a school's database. The
 * length limits mirror the columns in {@code V2026_09_05_2000__identity_create_role_and_grant.sql}
 * so seeding cannot fail on a value the catalogue already accepted.
 */
@Component
public class PermissionCatalog {

    /** Mirrors {@code ck_permission_code}. Change one and you must change the other. */
    public static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*:[a-z][a-z0-9_]*:[a-z][a-z0-9_]*$");

    private static final int MAX_CODE_LENGTH = 80;
    private static final int MAX_MODULE_LENGTH = 40;
    private static final int MAX_LABEL_LENGTH = 120;
    private static final int MAX_DESCRIPTION_LENGTH = 400;

    private static final Logger log = LoggerFactory.getLogger(PermissionCatalog.class);

    /** Sorted, so the catalogue reads the same way in a UI, a log line and a test. */
    private final Map<String, PermissionDefinition> byCode = new TreeMap<>();

    public PermissionCatalog(List<PermissionProvider> providers) {
        Map<String, String> declaredBy = new LinkedHashMap<>();
        for (PermissionProvider provider : providers) {
            String source = provider.getClass().getName();
            for (PermissionDefinition permission : provider.permissions()) {
                validate(permission);
                String clash = declaredBy.putIfAbsent(permission.code(), source);
                if (clash != null) {
                    throw new IllegalStateException("Permission " + permission.code() + " is declared twice: by "
                            + clash + " and by " + source);
                }
                byCode.put(permission.code(), permission);
            }
        }
        log.info("Registered {} permission(s) from {} module registr(ies)", byCode.size(), providers.size());
    }

    /** Every declared permission, in code order. */
    public List<PermissionDefinition> all() {
        return List.copyOf(byCode.values());
    }

    public boolean contains(String code) {
        return byCode.containsKey(code);
    }

    /**
     * @throws IllegalStateException if no module declares {@code code} — a role template or a
     *     {@code @PreAuthorize} referring to a permission that does not exist is a bug in this
     *     build, not a runtime condition to recover from.
     */
    public PermissionDefinition require(String code) {
        PermissionDefinition permission = byCode.get(code);
        if (permission == null) {
            throw new IllegalStateException("No module declares the permission " + code);
        }
        return permission;
    }

    /** Fails with every unknown code at once, rather than one per run. */
    public void requireAll(Collection<String> codes) {
        List<String> unknown = codes.stream()
                .filter(code -> !byCode.containsKey(code))
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalStateException("No module declares the permission(s) " + unknown);
        }
    }

    private static void validate(PermissionDefinition permission) {
        if (permission == null) {
            throw new IllegalStateException("A PermissionProvider returned a null permission");
        }
        if (permission.code() == null
                || !CODE_PATTERN.matcher(permission.code()).matches()) {
            throw new IllegalStateException("Not a usable permission code: " + permission.code()
                    + " (expected <module>:<resource>:<action> matching " + CODE_PATTERN.pattern() + ")");
        }
        if (permission.module() == null || !permission.code().startsWith(permission.module() + ":")) {
            throw new IllegalStateException("Permission " + permission.code() + " claims module " + permission.module()
                    + ", but its code says otherwise. The module is the first segment of the code.");
        }
        if (permission.label() == null || permission.label().isBlank()) {
            throw new IllegalStateException("Permission " + permission.code()
                    + " has no label. A principal reads this list in a UI; every entry needs a name.");
        }
        tooLong(permission.code(), "code", permission.code(), MAX_CODE_LENGTH);
        tooLong(permission.code(), "module", permission.module(), MAX_MODULE_LENGTH);
        tooLong(permission.code(), "label", permission.label(), MAX_LABEL_LENGTH);
        tooLong(permission.code(), "description", permission.description(), MAX_DESCRIPTION_LENGTH);
    }

    private static void tooLong(String code, String field, String value, int max) {
        if (value != null && value.length() > max) {
            throw new IllegalStateException("Permission " + code + " has a " + field + " longer than the " + max
                    + " characters the column holds");
        }
    }
}
