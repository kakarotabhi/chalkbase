package in.chalkbase.school.application;

import in.chalkbase.platform.error.NotFoundException;
import in.chalkbase.platform.tenancy.SchoolProvisioning;
import in.chalkbase.school.api.CreateSchoolRequest;
import in.chalkbase.school.api.SchoolResponse;
import in.chalkbase.school.domain.School;
import in.chalkbase.school.infrastructure.SchoolRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SchoolService {

    private final SchoolRepository schools;
    private final SchoolProvisioning provisioning;

    public SchoolService(SchoolRepository schools, SchoolProvisioning provisioning) {
        this.schools = schools;
        this.provisioning = provisioning;
    }

    public List<SchoolResponse> findAll() {
        return schools.findAll().stream().map(SchoolResponse::from).toList();
    }

    public SchoolResponse findById(UUID id) {
        return schools.findById(id).map(SchoolResponse::from).orElseThrow(() -> new NotFoundException("School", id));
    }

    /**
     * Registers a school and brings its schema online.
     *
     * <p>The registry row is committed before the schema is provisioned, deliberately: Flyway runs
     * its own transactions and DDL cannot join this one, so doing it the other way round would leave
     * an orphaned schema if the insert failed. A registered school whose schema is missing is
     * recoverable — the next startup migrates it — while an unregistered schema is invisible.
     */
    @Transactional
    public SchoolResponse create(CreateSchoolRequest request) {
        School school = new School(
                request.code(), request.name(), request.schemaName(), request.board(), request.city(), request.state());
        SchoolResponse saved = SchoolResponse.from(schools.saveAndFlush(school));
        provisioning.provision(school.getSchemaName());
        return saved;
    }
}
