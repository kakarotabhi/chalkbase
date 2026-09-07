package in.chalkbase.document.infrastructure;

import in.chalkbase.platform.security.PermissionDefinition;
import in.chalkbase.platform.security.PermissionProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What this module lets someone do (ADR-0005).
 *
 * <p>One resource, two actions, the same split every other Phase 1 module uses: {@code read} for
 * seeing a student's documents and downloading one, {@code manage} for uploading, editing and
 * deleting. Verification status is edited under {@code manage} too rather than a permission of its
 * own — ADR-0025 says why a school that wants upload and verification to be different jobs is
 * exactly the kind of split a school configures for itself by copying and narrowing a role, not
 * something this build should force with a third permission today.
 */
@Configuration
public class DocumentPermissions {

    /** Seeing a student's documents, their metadata, and downloading their content. */
    public static final String DOCUMENT_READ = "document:document:read";

    /** Uploading, editing, verifying and deleting a student's documents. */
    public static final String DOCUMENT_MANAGE = "document:document:manage";

    @Bean
    PermissionProvider documentPermissionProvider() {
        return () -> List.of(
                new PermissionDefinition(
                        DOCUMENT_READ,
                        "document",
                        "View documents",
                        "See a student's certificates, photo and other documents, and download them."),
                new PermissionDefinition(
                        DOCUMENT_MANAGE,
                        "document",
                        "Manage documents",
                        "Upload, edit, verify and delete a student's documents."));
    }
}
