package in.chalkbase.platform.reference;

/**
 * One row of {@link IndianStates#ALL}.
 *
 * @param code a short, permanent identifier this project assigns once (see {@code
 *     V2026_09_07_1900__platform_create_state.sql}). Not an external standard and not asserted to
 *     match one — its only job is to survive a rename of {@code name}.
 * @param name the name a school sees and picks, e.g. {@code "Maharashtra"}.
 */
public record IndianState(String code, String name) {}
