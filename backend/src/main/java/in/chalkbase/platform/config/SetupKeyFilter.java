package in.chalkbase.platform.config;

import in.chalkbase.platform.api.ApiError;
import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.platform.error.PlatformErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Hides school onboarding behind a shared secret on the {@code prod} profile.
 *
 * <p><strong>A second lock, not the only one, and permanent rather than provisional.</strong>
 * {@code /api/schools/**} is {@code permitAll} because {@code POST /api/schools/bootstrap} happens
 * before any account exists to authenticate (ADR-0024) — a platform-operator account was considered
 * for this and rejected as the larger fix for a smaller problem. That endpoint creates a PostgreSQL
 * schema, so anyone who finds it could fill the database with junk schemas; this filter is what
 * stops a stranger reaching it. Bootstrap adds its own second refusal on top ({@code AUTH_014}, once
 * a school already has an account), which is why losing this filter would be a nuisance rather than
 * a full reopening of the chicken-and-egg ADR-0024 closes.
 *
 * <p><strong>A missing or wrong key is a 404, not a 401.</strong> A 401 confirms that the endpoint
 * exists and that a credential would open it, which turns an unauthenticated prober into an
 * authenticated one who only needs the secret. The response is byte-for-byte what this application
 * returns for any address it does not serve ({@code NF_002}), so scanning {@code /api/schools}
 * tells an attacker exactly as much as scanning {@code /api/nonsense}: nothing. The cost is that an
 * operator who mistypes their own key sees "No such endpoint" rather than "wrong key" — which is
 * the whole point, and is why it is written down here.
 *
 * <p>The comparison is {@link MessageDigest#isEqual} over UTF-8 bytes rather than
 * {@link String#equals}, which returns at the first differing character and so leaks the length of
 * the matching prefix to anyone who can time the response.
 *
 * <p><strong>The key is never logged, at any level.</strong> Not the value, not a prefix, not its
 * length, not whether the request carried one. There is deliberately no logger in this class: a
 * rejection is indistinguishable in the logs from a request to any other unmapped address, which
 * is the same property the 404 gives the caller.
 *
 * <p><strong>One exemption: {@code GET /api/schools/boards}.</strong> It is not an onboarding
 * action — it discloses nothing about which schools exist, only the fixed list of curriculum boards
 * {@code Board} declares — and {@code SchoolController} already requires {@code isAuthenticated()}
 * on it independently of this filter, the same treatment {@code /api/reference/states} gets in
 * {@code platform}. It sits under {@code /api/schools/**} only because {@code Board} is
 * {@code school.domain}'s own type and {@code platform} must not import it the other way round
 * (ADR-0029), not because it is part of the platform-operator registry this filter exists to hide.
 * Before this exemption existed, a browser signed in to a real school 404'd this read on {@code
 * prod} exactly as it would a stranger's write — the school-profile form's Board picker could not
 * load, and the byte-identical {@code NF_002} this filter is designed to produce made it look like
 * ordinary routing rather than a bug. {@code SetupKeyFilterTests} pins this on the {@code prod}
 * profile, which is the only profile where it was ever wrong.
 */
class SetupKeyFilter extends OncePerRequestFilter {

    /**
     * The header carrying the key. Not {@code Authorization}: this is not a credential belonging to
     * a principal, and putting it there would invite a client to treat it as one.
     */
    static final String HEADER = "X-Chalkbase-Setup-Key";

    /**
     * The same matcher type Spring Security uses for its own {@code /api/schools/**} rules, so the
     * two cannot disagree about what a path is. A hand-rolled {@code startsWith} on
     * {@code getRequestURI()} would differ from Spring's parsed path over trailing slashes, path
     * parameters and encoding — and every such difference is a way past this filter into an
     * endpoint Spring Security still believes is guarded.
     */
    private static final RequestMatcher ONBOARDING =
            PathPatternRequestMatcher.withDefaults().matcher("/api/schools/**");

    /** See the class Javadoc's "One exemption" paragraph. Named, not folded into {@link #ONBOARDING}. */
    private static final RequestMatcher BOARDS_READ =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/schools/boards");

    private final byte[] expected;
    private final JsonMapper jsonMapper;

    SetupKeyFilter(String setupKey, JsonMapper jsonMapper) {
        this.expected = setupKey.getBytes(StandardCharsets.UTF_8);
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !ONBOARDING.matches(request) || BOARDS_READ.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented == null || !MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8))) {
            notFound(response);
            return;
        }
        chain.doFilter(request, response);
    }

    /** The application's ordinary "no such endpoint" response, produced here so it is identical. */
    private void notFound(HttpServletResponse response) throws IOException {
        PlatformErrorCode code = PlatformErrorCode.NO_SUCH_ENDPOINT;
        response.setStatus(code.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter()
                .write(jsonMapper.writeValueAsString(
                        ApiResponse.error(ApiError.of(code.code(), code.defaultMessage()))));
    }
}
