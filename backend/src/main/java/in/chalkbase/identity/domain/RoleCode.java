package in.chalkbase.identity.domain;

import java.util.regex.Pattern;

/**
 * Derives a role's stable {@code code} from the name an admin typed.
 *
 * <p>A school-created role has no natural code the way a shipped template does ({@code PRINCIPAL},
 * {@code CLASS_TEACHER}): nobody types one, because {@code CreateRoleRequest} asks only for a name, description and
 * permission list. Deriving it keeps the column meaningful —
 * {@code role.code} stays a short, uppercase, machine-stable identifier rather than a second copy of
 * the name — without asking an admin to invent one on a form that already has three fields.
 */
public final class RoleCode {

    /** {@code role.code} is {@code varchar(40)}. */
    private static final int MAX_LENGTH = 40;

    private static final Pattern NOT_ALLOWED = Pattern.compile("[^A-Z0-9]+");
    private static final Pattern REPEATED_UNDERSCORE = Pattern.compile("_{2,}");

    private RoleCode() {}

    /**
     * The base candidate, before uniqueness is checked. Never empty: a name with no letters or
     * digits at all (unusual, but not rejected by {@code CreateRoleRequest}'s validation) falls back
     * to {@code ROLE}.
     */
    public static String deriveFrom(String name) {
        String upper = name.trim().toUpperCase(java.util.Locale.ROOT);
        String replaced = NOT_ALLOWED.matcher(upper).replaceAll("_");
        String collapsed = REPEATED_UNDERSCORE.matcher(replaced).replaceAll("_");
        String trimmed = strip(collapsed);
        if (trimmed.isEmpty()) {
            return "ROLE";
        }
        return trimmed.length() > MAX_LENGTH ? strip(trimmed.substring(0, MAX_LENGTH)) : trimmed;
    }

    /**
     * A later candidate once {@code base} (or an earlier suffixed attempt) is taken. Reserves room
     * for the suffix so the result never exceeds the column width.
     */
    public static String withSuffix(String base, int suffix) {
        String suffixText = "_" + suffix;
        int roomForBase = MAX_LENGTH - suffixText.length();
        String truncatedBase = base.length() > roomForBase ? base.substring(0, roomForBase) : base;
        return strip(truncatedBase) + suffixText;
    }

    private static String strip(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '_') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '_') {
            end--;
        }
        return value.substring(start, end);
    }
}
