package in.chalkbase.student.infrastructure;

import in.chalkbase.student.domain.StudentMedical;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** One student's health record — present only once entered (FR-034). */
public interface StudentMedicalRepository extends JpaRepository<StudentMedical, UUID> {}
