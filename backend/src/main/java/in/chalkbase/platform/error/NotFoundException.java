package in.chalkbase.platform.error;

/** Thrown when a resource does not exist, or is not visible to the current tenant. */
public class NotFoundException extends ChalkbaseException {

    public NotFoundException(String resource, Object id) {
        super(PlatformErrorCode.NOT_FOUND, "%s %s was not found".formatted(resource, id));
    }
}
