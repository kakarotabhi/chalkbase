package in.chalkbase;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Guards the module boundaries. This test failing means a module reached into another module's
 * internals — fix the dependency, do not relax the test.
 */
class ModularityTests {

    static final ApplicationModules MODULES = ApplicationModules.of(ChalkbaseApplication.class);

    @Test
    void verifiesModularStructure() {
        MODULES.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(MODULES).writeDocumentation();
    }
}
