package in.chalkbase.fee.infrastructure;

import in.chalkbase.fee.domain.FeeConcessionType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeConcessionTypeRepository extends JpaRepository<FeeConcessionType, UUID> {

    List<FeeConcessionType> findAllByOrderByNameAsc();
}
