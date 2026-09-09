package in.chalkbase.admission.infrastructure;

import in.chalkbase.admission.domain.Enquiry;
import in.chalkbase.admission.domain.EnquiryStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * This school's enquiries.
 *
 * <p>Scoped by the connection's {@code search_path} (ADR-0011): there is no tenant filter to write
 * or to forget, and an enquiry id from another school is not in this schema — a request naming one
 * is a 404, not a leak.
 *
 * <p>The list is a {@link JpaSpecificationExecutor}, the same choice {@code StudentRepository}
 * makes for the same reason: several optional filters over one entity is several derived methods
 * and grows combinatorially the moment a filter joins them. See {@code EnquiryQueries}.
 */
public interface EnquiryRepository extends JpaRepository<Enquiry, UUID>, JpaSpecificationExecutor<Enquiry> {

    /**
     * The due-date queue this module exists to provide, for the whole school: every open enquiry
     * whose next follow-up date has arrived or passed, oldest due date first.
     *
     * <p>Two methods rather than one taking a nullable {@code counsellorId} — {@code AuditEventRepository}
     * states the reason this codebase avoids a JPQL {@code (:param is null or ...)} branch on an
     * optional filter, and a due-or-not-due toggle is exactly one such filter, not several, so a
     * second method is cheaper than reaching for a {@link org.springframework.data.jpa.domain.Specification}
     * the way {@code EnquiryQueries} does for the list's several.
     *
     * @param today compared against {@code next_follow_up_date}, passed in rather than computed
     *     here so a test can name a fixed date instead of racing the clock
     * @param closedStatuses excluded outright — a closed enquiry's {@code next_follow_up_date} is
     *     always null anyway (see {@code Enquiry.applyFollowUp}), but naming the statuses here
     *     keeps the query correct even if that invariant is ever violated by a direct write
     */
    @Query("select e from Enquiry e where e.nextFollowUpDate is not null and e.nextFollowUpDate <= :today"
            + " and e.status not in :closedStatuses"
            + " order by e.nextFollowUpDate asc, e.createdAt asc")
    Page<Enquiry> findDueFollowUps(
            @Param("today") LocalDate today,
            @Param("closedStatuses") Collection<EnquiryStatus> closedStatuses,
            Pageable pageable);

    /** As {@link #findDueFollowUps}, narrowed to one counsellor's own enquiries — a counsellor's own queue. */
    @Query("select e from Enquiry e where e.nextFollowUpDate is not null and e.nextFollowUpDate <= :today"
            + " and e.status not in :closedStatuses and e.assignedCounsellorId = :counsellorId"
            + " order by e.nextFollowUpDate asc, e.createdAt asc")
    Page<Enquiry> findDueFollowUpsForCounsellor(
            @Param("today") LocalDate today,
            @Param("closedStatuses") Collection<EnquiryStatus> closedStatuses,
            @Param("counsellorId") UUID counsellorId,
            Pageable pageable);
}
