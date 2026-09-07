package in.chalkbase.student.infrastructure;

import in.chalkbase.student.domain.StudentCompliance;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** One student's UDISE+/board identifiers and statutory categories — present only once entered (FR-029). */
public interface StudentComplianceRepository extends JpaRepository<StudentCompliance, UUID> {}
