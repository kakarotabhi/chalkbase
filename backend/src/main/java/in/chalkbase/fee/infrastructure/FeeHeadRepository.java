package in.chalkbase.fee.infrastructure;

import in.chalkbase.fee.domain.FeeHead;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeHeadRepository extends JpaRepository<FeeHead, UUID> {

    List<FeeHead> findAllByOrderByNameAsc();
}
