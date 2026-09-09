package in.chalkbase.fee.api;

import in.chalkbase.fee.application.FeeHeadService;
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
 * The school's catalogue of fee heads (Phase 0 §4, ADR-0012).
 *
 * <p>Not per session, like {@code SubjectController}: a head is a fact about what this school
 * calls a charge, and it is the fee structure that says what it costs in a given year.
 *
 * <p><strong>There is no DELETE.</strong> A head already named by a fee structure must not point
 * at nothing; a head created by mistake is retired.
 */
@RestController
@RequestMapping("/api/fees/heads")
public class FeeHeadController {

    private final FeeHeadService heads;

    public FeeHeadController(FeeHeadService heads) {
        this.heads = heads;
    }

    /** Every fee head, active and retired alike, ordered by name. Not paged: a school's price list is a few dozen rows at most. */
    @PreAuthorize("hasAuthority('fee:head:read')")
    @GetMapping
    public ApiResponse<List<FeeHeadResponse>> list() {
        return ApiResponse.success(heads.list());
    }

    @PreAuthorize("hasAuthority('fee:head:manage')")
    @PostMapping
    public ResponseEntity<ApiResponse<FeeHeadResponse>> create(@Valid @RequestBody SaveFeeHeadRequest request) {
        FeeHeadResponse created = heads.create(request);
        return ResponseEntity.created(URI.create("/api/fees/heads/" + created.id()))
                .body(ApiResponse.success(created));
    }

    @PreAuthorize("hasAuthority('fee:head:manage')")
    @PutMapping("/{id}")
    public ApiResponse<FeeHeadResponse> update(@PathVariable UUID id, @Valid @RequestBody SaveFeeHeadRequest request) {
        return ApiResponse.success(heads.update(id, request));
    }
}
