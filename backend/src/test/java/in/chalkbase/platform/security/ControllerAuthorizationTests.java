package in.chalkbase.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import in.chalkbase.TestcontainersConfiguration;
import in.chalkbase.identity.api.AuthController;
import in.chalkbase.identity.api.LoginRequest;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The test ADR-0005 calls non-negotiable.
 *
 * <p>Every controller method needs an explicit authorization decision. At sixteen modules that is
 * not something review catches — an endpoint added on a Friday with no annotation is authenticated
 * but otherwise unguarded, and nothing about the code looks wrong. This enumerates what Spring
 * actually routes to and fails the build instead.
 *
 * <p>The allow-list is short, explicit, and resolved by reflection so that a method it names must
 * still exist with that exact signature. Renaming or deleting one of those methods fails this test
 * rather than quietly leaving a hole open for its replacement.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ControllerAuthorizationTests {

    /** Anything that decides who may call a method. {@code permitAll()} is a decision too. */
    private static final List<Class<? extends Annotation>> AUTHORIZATION_ANNOTATIONS = List.of(
            PreAuthorize.class, PostAuthorize.class, Secured.class, RolesAllowed.class, PermitAll.class, DenyAll.class);

    /**
     * The genuinely public endpoints.
     *
     * <p>Signing in cannot require a session, because establishing one is what it does. Signing out
     * must work when the session has already expired, or a client is left unable to do the one thing
     * it asked for. Nothing else belongs here: onboarding is open too, but it says so at the method
     * with {@code @PreAuthorize("permitAll()")} and a TODO, which is a decision recorded in the
     * code rather than in a list somewhere else.
     */
    private static final Set<Method> PUBLIC_BY_DESIGN = Set.of(
            method(
                    AuthController.class,
                    "login",
                    LoginRequest.class,
                    HttpServletRequest.class,
                    HttpServletResponse.class),
            method(AuthController.class, "logout", HttpServletRequest.class));

    private static final Pattern HAS_AUTHORITY = Pattern.compile("has(?:Any)?Authority\\(([^)]*)\\)");
    private static final Pattern QUOTED = Pattern.compile("'([^']*)'");

    /**
     * Qualified by name: the actuator contributes a second {@code RequestMappingHandlerMapping} for
     * its own endpoints, and this test is about the application's controllers.
     */
    @Autowired
    @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;

    @Autowired
    PermissionCatalog catalog;

    @Test
    void everyEndpointDecidesWhoMayCallIt() {
        List<String> unguarded = ourHandlers().stream()
                .filter(handler -> !PUBLIC_BY_DESIGN.contains(handler.getMethod()))
                .filter(handler -> authorizationAnnotation(handler) == null)
                .map(ControllerAuthorizationTests::describe)
                .sorted()
                .toList();

        assertThat(unguarded).as("""
                        Every controller method needs an explicit authorization annotation (ADR-0005).
                        Add @PreAuthorize("hasAuthority('<module>:<resource>:<action>')") — or, if the endpoint is
                        genuinely public, @PreAuthorize("permitAll()") with a comment saying why.
                        Do not add it to PUBLIC_BY_DESIGN unless it is login or logout.""").isEmpty();
    }

    /**
     * An allow-list entry for a method that no longer exists would silently stop protecting
     * anything. Resolving the entries by reflection is what prevents that, and this asserts the
     * list is actually reaching live handlers rather than dead ones.
     */
    @Test
    void everyAllowListedMethodIsStillARoutedEndpoint() {
        Set<Method> routed = ourHandlers().stream()
                .map(HandlerMethod::getMethod)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        assertThat(routed).as("allow-listed methods are still mapped endpoints").containsAll(PUBLIC_BY_DESIGN);
    }

    /**
     * A {@code @PreAuthorize} naming a permission no module declares can never be satisfied, so the
     * endpoint is unreachable — and looks perfectly guarded while being so. A typo in one of these
     * strings is the most likely way to produce that.
     */
    @Test
    void everyPermissionNamedInAnAnnotationExistsInTheCatalogue() {
        List<String> unknown = new ArrayList<>();
        for (HandlerMethod handler : ourHandlers()) {
            PreAuthorize preAuthorize =
                    AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), PreAuthorize.class);
            if (preAuthorize == null) {
                continue;
            }
            for (String code : authoritiesNamedIn(preAuthorize.value())) {
                if (!catalog.contains(code)) {
                    unknown.add(describe(handler) + " requires '" + code + "'");
                }
            }
        }

        assertThat(unknown)
                .as("permissions are code: a @PreAuthorize may only name one a module declares")
                .isEmpty();
    }

    /** Sanity: the enumeration is finding real endpoints, not an empty list that would pass anything. */
    @Test
    void findsTheEndpointsThisApplicationActuallyServes() {
        assertThat(ourHandlers().stream().map(ControllerAuthorizationTests::describe))
                .contains(
                        "AuthController#login",
                        "AuthController#logout",
                        "AuthController#changePassword",
                        "AccessController#roles",
                        "SchoolController#create");
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    /** Our controllers only: springdoc, the actuator and Boot's error controller are not ours to annotate. */
    private List<HandlerMethod> ourHandlers() {
        return handlerMapping.getHandlerMethods().values().stream()
                .filter(handler -> handler.getBeanType().getName().startsWith("in.chalkbase."))
                .distinct()
                .toList();
    }

    private static Annotation authorizationAnnotation(HandlerMethod handler) {
        for (Class<? extends Annotation> type : AUTHORIZATION_ANNOTATIONS) {
            Annotation onMethod = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), type);
            if (onMethod != null) {
                return onMethod;
            }
            // A class-level annotation covers every method on the controller, which is a legitimate
            // way to guard one whose endpoints all need the same permission.
            Annotation onClass = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), type);
            if (onClass != null) {
                return onClass;
            }
        }
        return null;
    }

    private static List<String> authoritiesNamedIn(String expression) {
        List<String> codes = new ArrayList<>();
        Matcher calls = HAS_AUTHORITY.matcher(expression);
        while (calls.find()) {
            Matcher arguments = QUOTED.matcher(calls.group(1));
            while (arguments.find()) {
                codes.add(arguments.group(1));
            }
        }
        return codes;
    }

    private static String describe(HandlerMethod handler) {
        return handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName();
    }

    private static Method method(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException ex) {
            throw new AssertionError(
                    "The allow-list names " + type.getSimpleName() + "#" + name + ", which no longer exists with that"
                            + " signature. Remove the entry, or restore the method.",
                    ex);
        }
    }
}
