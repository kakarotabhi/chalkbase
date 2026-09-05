package in.chalkbase.school.api;

import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.school.application.SchoolService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schools")
public class SchoolController {

    private final SchoolService schoolService;

    public SchoolController(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @GetMapping
    public ApiResponse<List<SchoolResponse>> list() {
        return ApiResponse.success(schoolService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<SchoolResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(schoolService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SchoolResponse>> create(@Valid @RequestBody CreateSchoolRequest request) {
        SchoolResponse created = schoolService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/schools/" + created.id()))
                .body(ApiResponse.success(created));
    }
}
