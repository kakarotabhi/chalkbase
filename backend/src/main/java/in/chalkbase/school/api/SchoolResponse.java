package in.chalkbase.school.api;

import in.chalkbase.school.domain.Board;
import in.chalkbase.school.domain.School;
import java.util.UUID;

/** Public read model of a school. Never expose the JPA entity across a module or HTTP boundary. */
public record SchoolResponse(
        UUID id, String code, String name, String schemaName, Board board, String city, String state, boolean active) {

    public static SchoolResponse from(School school) {
        return new SchoolResponse(
                school.getId(),
                school.getCode(),
                school.getName(),
                school.getSchemaName(),
                school.getBoard(),
                school.getCity(),
                school.getState(),
                school.isActive());
    }
}
