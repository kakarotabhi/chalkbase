package in.chalkbase.platform.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SchemaNameTests {

    @ParameterizedTest
    @ValueSource(strings = {"greenfield", "gps_s12", "a_b", "school_2026"})
    void acceptsUsableNames(String name) {
        assertThat(SchemaName.requireValid(name)).isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "public", "pg_catalog", "pg_toast", "information_schema",
                "auth", "storage", "realtime", "vault"
            })
    void rejectsReservedSchemas(String name) {
        assertThatThrownBy(() -> SchemaName.requireValid(name)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Greenfield", // uppercase folds in PostgreSQL and would not round-trip
                "1school", // must not start with a digit
                "ab", // too short to be deliberate
                "school-12", // a hyphen would need quoting everywhere
                "school 12",
                "school;drop",
                "school\"; select 1 --"
            })
    void rejectsUnusableOrHostileNames(String name) {
        assertThatThrownBy(() -> SchemaName.requireValid(name)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNamesPostgresWouldTruncate() {
        assertThat(SchemaName.isValid("s".repeat(SchemaName.MAX_LENGTH))).isTrue();
        assertThat(SchemaName.isValid("s".repeat(SchemaName.MAX_LENGTH + 1))).isFalse();
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> SchemaName.requireValid(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
