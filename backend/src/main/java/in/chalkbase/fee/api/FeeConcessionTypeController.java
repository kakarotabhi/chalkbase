package in.chalkbase.fee.api;

import in.chalkbase.fee.application.FeeConcessionTypeService;
import in.chalkbase.platform.api.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The school's catalogue of concession types (FR-078) — sibling, staff-child, management quota,
 * RTE/EWS, scholarship, other.
 *
 * <p><strong>Defining a type here grants nothing to anyone.</strong> Applying a concession to a
 * student's actual charge is fee demand's work, once that lane exists — see
 * {@code in.chalkbase.fee.package-info}.
 *
 * <p>There is no DELETE, for the same reason {@code FeeHeadController} has none.
 */
@RestController
@RequestMapping("/api/fees/concession-types")
public class FeeConcessionTypeController {

    private final FeeConcessionTypeService concessionTypes;

    public FeeConcessionTypeController(FeeConcessionTypeService concessionTypes) {
        this.concessionTypes = concessionTypes;
    }

    @PreAuthorize("hasAuthority('fee:concession_type:read')")
    @GetMapping
    public ApiResponse<List<FeeConcessionTypeResponse>> list() {
        return ApiResponse.success(concessionTypes.list());
    }

    @PreAuthorize("hasAuthority('fee:concession_type:manage')")
    @PostMapping
    public ResponseEntity<ApiResponse<FeeConcessionTypeResponse>> create(
            @Valid @RequestBody SaveFeeConcessionTypeRequest request) {
        FeeConcessionTypeResponse created = concessionTypes.create(request);
        return ResponseEntity.created(URI.create("/api/fees/concession-types/" + created.id()))
                .body(ApiResponse.success(created));
    }

    @PreAuthorize("hasAuthority('fee:concession_type:manage')")
    @PutMapping("/{id}")
    public ApiResponse<FeeConcessionTypeResponse> update(
            @PathVariable UUID id, @Valid @RequestBody SaveFeeConcessionTypeRequest request) {
        return ApiResponse.success(concessionTypes.update(id, request));
    }
}
