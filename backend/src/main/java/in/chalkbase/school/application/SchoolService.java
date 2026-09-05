package in.chalkbase.school.application;

import in.chalkbase.platform.error.NotFoundException;
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

    public SchoolService(SchoolRepository schools) {
        this.schools = schools;
    }

    public List<SchoolResponse> findAll() {
        return schools.findAll().stream().map(SchoolResponse::from).toList();
    }

    public SchoolResponse findById(UUID id) {
        return schools.findById(id).map(SchoolResponse::from).orElseThrow(() -> new NotFoundException("School", id));
    }

    @Transactional
    public SchoolResponse create(CreateSchoolRequest request) {
        School school = new School(request.code(), request.name(), request.board(), request.city(), request.state());
        return SchoolResponse.from(schools.save(school));
    }
}
