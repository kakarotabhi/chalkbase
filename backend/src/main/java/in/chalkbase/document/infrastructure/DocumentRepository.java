package in.chalkbase.document.infrastructure;

import in.chalkbase.document.domain.Document;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /** Every document a student has, newest first — bounded per student, so unpaged ({@code DocumentController}). */
    List<Document> findByStudentIdOrderByCreatedAtDesc(UUID studentId);
}
