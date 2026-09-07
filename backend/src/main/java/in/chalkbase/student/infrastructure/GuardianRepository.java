package in.chalkbase.student.infrastructure;

import in.chalkbase.student.domain.Guardian;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/**
 * This school's guardians, as people rather than as somebody's parent.
 *
 * <p>Scoped by {@code search_path} like everything else (ADR-0011).
 *
 * <p>No delete: a guardian survives being detached from a child, which is the whole point of the
 * one {@code DELETE} this module has (ADR-0020 §6).
 */
public interface GuardianRepository extends JpaRepository<Guardian, UUID>, JpaSpecificationExecutor<Guardian> {

    /**
     * Every guardian with a phone number on file, for the bulk import to match against (ADR-0021).
     *
     * <p>One query for the whole import rather than one per row or one per file-side phone group —
     * the same "two queries rather than two per row" shape {@code StudentImportService} already uses
     * for admission numbers. A school has a few thousand guardians at most, which is the same
     * reasoning {@code guardian.phone_digits} already accepts a sequential scan for, and it runs once
     * per import rather than once per keystroke.
     *
     * <p>Guardians with no phone at all are excluded rather than returned with a blank
     * {@code phoneDigits}: an import can only match on a number, and a school's paper-only guardian
     * records must not all collide on the empty string.
     */
    @Query("select g from Guardian g where g.phoneDigits is not null and g.phoneDigits <> ''")
    List<Guardian> findAllWithPhone();
}
