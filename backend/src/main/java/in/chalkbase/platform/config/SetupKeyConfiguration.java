package in.chalkbase.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires {@link SetupKeyFilter} — on the {@code prod} profile only.
 *
 * <p>The profile condition is the whole of the behavioural difference. On {@code local} and
 * {@code test} no bean is created, no header is required, and onboarding behaves exactly as it did
 * before this class existed; {@code SchoolApiTests} is the proof and needed no change. Anything
 * that makes a local developer or the test suite supply a key means the condition is wrong.
 *
 * <p><strong>A blank key fails the context, and that is deliberate.</strong> Defaulting to "no key
 * required" would mean a deployment that forgets the environment variable comes up looking healthy
 * with onboarding wide open — the exact state this exists to prevent, arrived at silently. Refusing
 * to start is loud, happens before the port is bound, and is trivially diagnosed from the one line
 * below. Note that this also applies to the Coolify deployment, which is the other consumer of the
 * {@code prod} profile: see {@code ops/coolify/.env.example}.
 *
 * <p>TODO(identity): delete this class, {@link SetupKeyFilter} and the {@code CHALKBASE_SETUP_KEY}
 * variable in the change that gives {@code /api/schools/**} a real platform-operator principal.
 */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
class SetupKeyConfiguration {

    @Bean
    SetupKeyFilter setupKeyFilter(@Value("${chalkbase.setup-key:}") String setupKey, JsonMapper jsonMapper) {
        if (!StringUtils.hasText(setupKey)) {
            // The message names the variable and not the value, and there is no value to name yet.
            throw new IllegalStateException("CHALKBASE_SETUP_KEY must be set on the prod profile. It is the only thing"
                    + " standing in front of POST /api/schools, which creates a database schema. Generate one"
                    + " (openssl rand -base64 32) and set it in the deployment's environment.");
        }
        return new SetupKeyFilter(setupKey, jsonMapper);
    }

    /**
     * Stops Boot from also registering the filter directly with the servlet container.
     *
     * <p>Every {@code Filter} bean is auto-registered, which would put a second copy of this filter
     * ahead of Spring Security's chain — and {@code OncePerRequestFilter} would then let that copy
     * be the only one that runs. That copy sees the raw request, before {@code StrictHttpFirewall}
     * and before the path is parsed, so it and Spring Security would no longer agree on which
     * requests are "under {@code /api/schools}". {@link SecurityConfig} adds the single instance
     * that matters, inside the chain, where the request has already been normalised.
     */
    @Bean
    FilterRegistrationBean<SetupKeyFilter> setupKeyFilterNotRegisteredWithTheServletContainer(SetupKeyFilter filter) {
        FilterRegistrationBean<SetupKeyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
