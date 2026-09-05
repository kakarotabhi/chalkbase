package in.chalkbase.identity;

import static org.assertj.core.api.Assertions.assertThat;

import in.chalkbase.identity.application.SessionAttributes;
import in.chalkbase.identity.infrastructure.SessionTenantFilter;
import in.chalkbase.platform.tenancy.TenantContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/**
 * The filter that turns a session cookie into a bound tenant.
 *
 * <p>Two failures matter here and neither produces an error at runtime: not binding at all (every
 * query silently reads {@code public}), and not unbinding (the next request on this thread reads
 * the previous school's rows).
 */
class SessionTenantFilterTests {

    private final SessionTenantFilter filter = new SessionTenantFilter();

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    void bindsTheSchemaTheSessionCarriesAndClearsItAfterwards() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAttributes.SCHEMA, "riverdale");
        request.setSession(session);

        List<String> seenInsideTheChain = new ArrayList<>();
        filter.doFilter(request, new MockHttpServletResponse(), recording(seenInsideTheChain));

        assertThat(seenInsideTheChain).containsExactly("riverdale");
        assertThat(TenantContext.currentSchema()).isEmpty();
    }

    @Test
    void bindsNothingWhenThereIsNoSession() throws Exception {
        List<String> seenInsideTheChain = new ArrayList<>();
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), recording(seenInsideTheChain));

        assertThat(seenInsideTheChain).containsExactly("<unbound>");
    }

    /** A session attribute is not a schema name until it has been validated as one. */
    @Test
    void refusesToBindSomethingThatIsNotAUsableSchemaName() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAttributes.SCHEMA, "public; drop schema riverdale");
        request.setSession(session);

        List<String> seenInsideTheChain = new ArrayList<>();
        filter.doFilter(request, new MockHttpServletResponse(), recording(seenInsideTheChain));

        assertThat(seenInsideTheChain).containsExactly("<unbound>");
    }

    /** A request that blows up must still leave the thread with no tenant bound. */
    @Test
    void unbindsEvenWhenTheRequestFails() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAttributes.SCHEMA, "riverdale");
        request.setSession(session);

        MockFilterChain exploding = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                throw new IllegalStateException("boom");
            }
        };

        try {
            filter.doFilter(request, new MockHttpServletResponse(), exploding);
        } catch (Exception expected) {
            // the point is the finally block, not the exception
        }

        assertThat(TenantContext.currentSchema()).isEmpty();
    }

    private static MockFilterChain recording(List<String> seen) {
        return new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.add(TenantContext.currentSchema().orElse("<unbound>"));
            }
        };
    }
}
