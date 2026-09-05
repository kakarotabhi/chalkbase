package in.chalkbase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Chalkbase application entry point.
 *
 * <p>Each direct sub-package of {@code in.chalkbase} is a Spring Modulith application module.
 * Modules talk to each other only through a module's named interfaces or domain events; the
 * boundary is enforced by {@code ModularityTests}.
 */
@Modulithic(systemName = "Chalkbase")
@SpringBootApplication
public class ChalkbaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChalkbaseApplication.class, args);
    }
}
