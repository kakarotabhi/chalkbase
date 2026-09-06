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
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Hides school onboarding behind a shared secret on the {@code prod} profile.
 *
 * <p><strong>This is a stopgap, not the design.</strong> {@code /api/schools/**} is
 * {@code permitAll} because onboarding a campus happens before any account exists inside it, and
 * there are no platform-operator accounts yet — the {@code TODO(identity)} in
 * {@link SecurityConfig} and {@code SchoolController} is the real fix. What made the gap urgent is
 * only that the application now has a public URL: {@code POST /api/schools} creates a PostgreSQL
 * schema, so anyone who finds the endpoint can fill the database with junk schemas. A shared
 * header buys time until the authorization model of ADR-0005 covers platform operators. Delete
 * this class in that change.
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

    private final byte[] expected;
    private final JsonMapper jsonMapper;

    SetupKeyFilter(String setupKey, JsonMapper jsonMapper) {
        this.expected = setupKey.getBytes(StandardCharsets.UTF_8);
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !ONBOARDING.matches(request);
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
