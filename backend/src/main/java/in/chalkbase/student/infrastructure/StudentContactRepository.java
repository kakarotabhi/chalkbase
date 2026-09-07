package in.chalkbase.student.infrastructure;

import in.chalkbase.student.domain.StudentContact;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** One student's own address, phone and email — present only once entered (FR-028). */
public interface StudentContactRepository extends JpaRepository<StudentContact, UUID> {}
