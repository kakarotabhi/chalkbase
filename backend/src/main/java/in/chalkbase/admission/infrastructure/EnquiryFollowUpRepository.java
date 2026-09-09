package in.chalkbase.admission.infrastructure;

import in.chalkbase.admission.domain.EnquiryFollowUp;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnquiryFollowUpRepository extends JpaRepository<EnquiryFollowUp, UUID> {

    /** One enquiry's whole follow-up history, newest first — the detail screen's own read. */
    List<EnquiryFollowUp> findByEnquiryIdOrderByRecordedAtDesc(UUID enquiryId);
}
