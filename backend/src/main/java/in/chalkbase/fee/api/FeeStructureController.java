package in.chalkbase.fee.api;

import in.chalkbase.fee.application.FeeStructureService;
import in.chalkbase.platform.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The live fee structure for each class, per academic session (ADR-0012 rule 6, ADR-0033).
 *
 * <p><strong>There is no DELETE and there is no PATCH.</strong> {@code PUT} here never edits a row
 * in place — see {@link FeeStructureService#save} and {@code FeeStructure}'s own Javadoc for what
 * it does instead and why. Reading a superseded version has no endpoint in this lane: nothing yet
 * needs "what did version 2 say" the way a filed structure eventually will once {@code fee_demand}
 * pins a charge to one, and adding that read now would be speculative surface ADR-0006 warns
 * against.
 *
 * <p>The permission strings are literals, following {@code SchoolClassController}: an annotation
 * needs a compile-time constant, and {@code ControllerAuthorizationTests} is what catches a typo.
 */
@RestController
@RequestMapping("/api/fees/structures")
public class FeeStructureController {

    private final FeeStructureService structures;

    public FeeStructureController(FeeStructureService structures) {
        this.structures = structures;
    }

    /** Every class's current structure for one session — the classes with none yet are simply absent. */
    @PreAuthorize("hasAuthority('fee:structure:read')")
    @GetMapping
    public ApiResponse<List<FeeStructureResponse>> list(@RequestParam UUID sessionId) {
        return ApiResponse.success(structures.currentStructures(sessionId));
    }

    /** One class's current structure for one session. 404 if that class has never had one in this session. */
    @PreAuthorize("hasAuthority('fee:structure:read')")
    @GetMapping("/{sessionId}/{classId}")
    public ApiResponse<FeeStructureResponse> get(@PathVariable UUID sessionId, @PathVariable UUID classId) {
        return ApiResponse.success(structures.currentForClass(sessionId, classId));
    }

    /**
     * Writes a whole new version of this class's structure for this session.
     *
     * <p>Refused with {@code FEE_009} once the session has already run its course — see
     * {@link FeeStructureService#save} for exactly what that means. There is nothing to refuse for
     * the very first version a class ever gets in a session: backfilling a past session's structure,
     * once, for the record, is allowed even after the fact.
     */
    @PreAuthorize("hasAuthority('fee:structure:manage')")
    @PutMapping("/{sessionId}/{classId}")
    public ApiResponse<FeeStructureResponse> save(
            @PathVariable UUID sessionId,
            @PathVariable UUID classId,
            @Valid @RequestBody SaveFeeStructureRequest request) {
        return ApiResponse.success(structures.save(sessionId, classId, request));
    }

    /**
     * Copies every class's current structure from one session into another, skipping any class
     * that already has one in the destination — never a silent overwrite. See
     * {@link CopyFeeStructureRequest} for the argument against both alternatives.
     */
    @PreAuthorize("hasAuthority('fee:structure:manage')")
    @PostMapping("/copy")
    public ApiResponse<CopyFeeStructureResponse> copy(@Valid @RequestBody CopyFeeStructureRequest request) {
        return ApiResponse.success(structures.copyFromPreviousSession(request));
    }
}
