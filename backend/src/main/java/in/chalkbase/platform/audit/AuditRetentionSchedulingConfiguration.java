package in.chalkbase.platform.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Turns on Spring's {@code @Scheduled} machinery, for {@link AuditRetentionPurgeJob}.
 *
 * <p><strong>This is the first {@link TaskScheduler} bean in the application.</strong> Nothing in
 * this codebase used {@code @Scheduled} before the retention purge, so there was nothing to wire —
 * {@code docs/status.md} named the absence explicitly. {@code @EnableScheduling} is application-wide
 * once declared anywhere the component scan reaches, not scoped to this package: a second
 * {@code @Scheduled} method added anywhere in the app, in any module, now runs too, against
 * whichever {@link TaskScheduler} bean it resolves to.
 *
 * <p>One thread is enough for the one job that exists. A second scheduled job with materially
 * different timing needs — something that must not be delayed behind a slow run of this one, or
 * vice versa — should bring its own named {@link TaskScheduler} rather than share this one; sharing
 * a single-thread pool across unrelated jobs is how one job's overrun silently delays another's next
 * tick. Named explicitly, rather than left to Spring Boot's unnamed default scheduler, so a thread
 * dump identifies it.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class AuditRetentionSchedulingConfiguration {

    @Bean
    TaskScheduler auditRetentionTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("chalkbase-scheduler-");
        return scheduler;
    }
}
