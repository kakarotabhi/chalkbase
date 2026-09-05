package in.chalkbase.platform.tenancy;

import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires Hibernate's schema multi-tenancy to the beans in this package. */
@Configuration
public class TenancyConfiguration {

    @Bean
    HibernatePropertiesCustomizer multiTenancyCustomizer(
            SchemaMultiTenantConnectionProvider connectionProvider, TenantIdentifierResolver resolver) {
        return properties -> {
            properties.put(MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
        };
    }

    /**
     * Nothing may touch the database before the migrations have run. Spring Boot applies the same
     * ordering to its own Flyway initializer; ours is disabled, so we declare it ourselves.
     */
    @Bean
    static EntityManagerFactoryDependsOnPostProcessor entityManagerFactoryDependsOnTenantMigrations() {
        return new EntityManagerFactoryDependsOnPostProcessor("tenantMigrationRunner");
    }
}
