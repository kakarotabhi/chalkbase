package in.chalkbase.communication.api;

import in.chalkbase.communication.application.CircularRecipientService;
import in.chalkbase.communication.application.CircularService;
import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.platform.api.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Composing, publishing and reading circulars, and their per-recipient status.
 *
 * <p>The permission strings are literals, for the same reason they are on every other controller in
 * the codebase: an annotation needs a compile-time constant, and {@code ControllerAuthorizationTests}
 * is what catches a typo against {@code CommunicationPermissions}.
 */
@RestController
@RequestMapping("/api/communication/circulars")
public class CircularController {

    private static final int DEFAULT_PAGE_SIZE = 25;

    private final CircularService circulars;
    private final CircularRecipientService recipients;

    public CircularController(CircularService circulars, CircularRecipientService recipients) {
        this.circulars = circulars;
        this.recipients = recipients;
    }

    /** Every circular, newest first. */
    @PreAuthorize("hasAuthority('communication:circular:read')")
    @GetMapping
    public ApiResponse<PageResponse<CircularSummary>> list(
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "createdAt") Pageable pageable) {
        return ApiResponse.success(circulars.list(pageable));
    }

    /** One circular in full. */
    @PreAuthorize("hasAuthority('communication:circular:read')")
    @GetMapping("/{id}")
    public ApiResponse<CircularDetail> get(@PathVariable UUID id) {
        return ApiResponse.success(circulars.get(id));
    }

    /**
     * How many actively enrolled students one candidate class or section would reach — the
     * composer's own preview, before a circular names it as a target.
     */
    @PreAuthorize("hasAuthority('communication:circular:read')")
    @GetMapping("/target-preview")
    public ApiResponse<TargetPreviewResponse> targetPreview(
            @RequestParam UUID classId, @RequestParam(required = false) UUID sectionId) {
        return ApiResponse.success(circulars.targetPreview(classId, sectionId));
    }

    /** Composes a circular in {@code DRAFT}, with every target given. */
    @PreAuthorize("hasAuthority('communication:circular:manage')")
    @PostMapping
    public ApiResponse<CircularDetail> create(@Valid @RequestBody CreateCircularRequest request) {
        return ApiResponse.success(circulars.create(request));
    }

    /** Publishes a circular: locks it and generates its recipients. */
    @PreAuthorize("hasAuthority('communication:circular:manage')")
    @PostMapping("/{id}/publish")
    public ApiResponse<CircularDetail> publish(@PathVariable UUID id) {
        return ApiResponse.success(circulars.publish(id));
    }

    /** A published circular's own recipient list. */
    @PreAuthorize("hasAuthority('communication:circular:read')")
    @GetMapping("/{id}/recipients")
    public ApiResponse<PageResponse<CircularRecipientResponse>> recipients(
            @PathVariable UUID id, @PageableDefault(size = DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ApiResponse.success(recipients.list(id, pageable));
    }

    /** Records that a recipient's family has acknowledged the circular, on their behalf. */
    @PreAuthorize("hasAuthority('communication:circular:acknowledge')")
    @PostMapping("/{id}/recipients/{recipientId}/acknowledge")
    public ApiResponse<CircularRecipientResponse> acknowledge(
            @PathVariable UUID id,
            @PathVariable UUID recipientId,
            @Valid @RequestBody AcknowledgeRecipientRequest request) {
        return ApiResponse.success(recipients.acknowledge(id, recipientId, request));
    }
}
