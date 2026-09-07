package in.chalkbase.document.infrastructure;

import in.chalkbase.platform.navigation.NavigationItem;
import in.chalkbase.platform.navigation.NavigationProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Where this module's screen appears in the menu (ADR-0008).
 *
 * <p>Declared here, at the top level, under its dotted id {@code students.documents}: the catalogue
 * reads that and places it beneath {@code students}, the container {@code StudentNavigation} owns —
 * the same mechanism {@code SchoolNavigation} uses to place {@code settings.profile} under
 * {@code identity}'s {@code settings} container. Declaring it inside {@code StudentNavigation}
 * instead would put a document screen behind another module's boundary, which is exactly the file
 * this wave's brief says not to edit.
 *
 * <p><strong>No frontend screen exists yet.</strong> That is expected, not a bug here: an id the
 * frontend does not recognise is dropped with a log line rather than rendered, the same way
 * {@code identity:role:manage}'s {@code settings.access} shipped its navigation before its screen
 * did. This entry is the label-key contract for whichever screen is built against
 * {@code /api/documents} next.
 */
@Configuration
public class DocumentNavigation {

    /** The student document list, nested under the {@code students} section. */
    public static final String STUDENTS_DOCUMENTS = "students.documents";

    @Bean
    NavigationProvider documentNavigationProvider() {
        return () -> List.of(new NavigationItem(
                STUDENTS_DOCUMENTS, "nav.students.documents", "document", 30, DocumentPermissions.DOCUMENT_READ));
    }
}
