package in.chalkbase.platform.error;

/** Thrown when a requested resource does not exist or is not visible to the current tenant. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Object id) {
        super("%s %s not found".formatted(resource, id));
    }
}
