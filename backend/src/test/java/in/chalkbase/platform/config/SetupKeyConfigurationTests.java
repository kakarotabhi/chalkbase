package in.chalkbase.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

/**
 * The startup half of the setup key: what happens before a single request is served.
 *
 * <p>Deliberately not a {@code @SpringBootTest}. The thing under test is that a context <em>fails
 * to build</em>, and a full application context on the {@code prod} profile has a dozen other
 * reasons to fail — no datasource, no Supabase — any one of which would let this pass while proving
 * nothing. {@link ApplicationContextRunner} builds only {@link SetupKeyConfiguration}, so a failure
 * here has exactly one possible cause, and the assertion on the message says which.
 */
class SetupKeyConfigurationTests {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withUserConfiguration(SetupKeyConfiguration.class);

    /**
     * The requirement this whole class exists for: a prod deployment that forgets the variable must
     * not come up. Falling back to "no key required" would leave onboarding open on a public URL
     * while every health check reported green, which is a worse outcome than a service that plainly
     * will not boot.
     */
    @Test
    void refusesToStartOnProdWithNoKey() {
        contexts.withPropertyValues("spring.profiles.active=prod").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context)
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CHALKBASE_SETUP_KEY");
        });
    }

    /**
     * Blank is not a key. An empty environment variable is what you get from a deployment template
     * whose value was never filled in — the likeliest way to arrive here, and indistinguishable in
     * effect from having set nothing.
     */
    @Test
    void refusesToStartOnProdWithABlankKey() {
        contexts.withPropertyValues("spring.profiles.active=prod", "chalkbase.setup-key=   ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure().rootCause().hasMessageContaining("CHALKBASE_SETUP_KEY");
                });
    }

    @Test
    void startsOnProdWhenTheKeyIsSet() {
        contexts.withPropertyValues("spring.profiles.active=prod", "chalkbase.setup-key=a-real-key")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(SetupKeyFilter.class));
    }

    /**
     * The condition that keeps local development and the test suite unchanged. If this fails, every
     * developer and every {@code @SpringBootTest} suddenly needs a secret to onboard a school —
     * and {@code SchoolApiTests}, which was not touched by this change, is the thing that would
     * start failing.
     */
    @Test
    void requiresNoKeyOffTheProdProfile() {
        contexts.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(SetupKeyFilter.class));

        contexts.withPropertyValues("spring.profiles.active=local")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(SetupKeyFilter.class));
    }
}
