package in.chalkbase.student.infrastructure;

import in.chalkbase.student.domain.StudentTransfer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** One student's previous school and transfer certificate — present only once entered (FR-033). */
public interface StudentTransferRepository extends JpaRepository<StudentTransfer, UUID> {}
