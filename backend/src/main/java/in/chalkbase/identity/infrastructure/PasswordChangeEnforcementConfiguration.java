package in.chalkbase.identity.infrastructure;

import in.chalkbase.identity.application.UserAccountService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires {@link PasswordChangeRequiredFilter} into the API security chain.
 *
 * <p>The filter is a {@code platform.config.AuthenticatedApiFilter}, which is how a feature module
 * puts a rule inside a chain that {@code platform} builds without {@code platform} having to name
 * an identity class.
 */
@Configuration(proxyBeanMethods = false)
class PasswordChangeEnforcementConfiguration {

    @Bean
    PasswordChangeRequiredFilter passwordChangeRequiredFilter(UserAccountService users, JsonMapper jsonMapper) {
        return new PasswordChangeRequiredFilter(users, jsonMapper);
    }

    /**
     * Stops Boot from also registering the filter directly with the servlet container.
     *
     * <p>Every {@code Filter} bean is auto-registered, which would put a second copy in front of
     * Spring Security's chain — and because {@code OncePerRequestFilter} runs only once per
     * request, that copy would be the <em>only</em> one that runs. It would see the raw request,
     * before {@code StrictHttpFirewall} and before the path is parsed, and it would run before the
     * security context has been restored, so it would find no principal and let everything through.
     * A silent, total bypass of the thing this filter exists to enforce.
     */
    @Bean
    FilterRegistrationBean<PasswordChangeRequiredFilter> passwordChangeFilterNotRegisteredWithTheServletContainer(
            PasswordChangeRequiredFilter filter) {
        FilterRegistrationBean<PasswordChangeRequiredFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
