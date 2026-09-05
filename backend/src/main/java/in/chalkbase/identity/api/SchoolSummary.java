package in.chalkbase.identity.api;

/** The school the caller has just signed in to, so the client can show it without a second call. */
public record SchoolSummary(String code, String name) {}
