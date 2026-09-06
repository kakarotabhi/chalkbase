package in.chalkbase.student.api;

import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.student.application.StudentImportService;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * A school's existing roll, uploaded as one CSV file (ADR-0021).
 *
 * <p><strong>Two endpoints, not one with a flag.</strong> A flag defaults to something, and the
 * wrong default here writes six hundred rows nobody has looked at. {@code /import/validate} cannot
 * write by construction; {@code /import} cannot be reached by omission. Both answer with the same
 * {@link ImportReport}, so the screen that shows one shows the other.
 *
 * <p><strong>Both answer 200 with a report, even when nothing could be imported.</strong> A file
 * with twenty-seven bad rows is not a malformed request — it is a request understood perfectly whose
 * answer is a list of twenty-seven things to fix, and the ADR-0007 error envelope has a code and a
 * sentence and nowhere to put that list. A 4xx is reserved for a file that could not be read as a
 * file at all: empty, no header, a repeated column, over the row cap, or a workbook.
 *
 * <p><strong>The academic year is a form field rather than something inferred.</strong> Guessing
 * the school's current session would make the most consequential thing about an import — which year
 * six hundred children land in — the one thing the person doing it never states. A school setting up
 * next year in February would silently import into the wrong one.
 *
 * <p>Both need {@code student:student:manage}, validation included: telling a caller which admission
 * numbers already exist is a read of the student register by another name (ADR-0021).
 *
 * <p>The uploaded file is the most sensitive object this product handles — several hundred
 * children's names and dates of birth in one place (ADR-0014). It is parsed in the request, never
 * written to disk, never attached to the audit event, and never logged. Nothing it contains appears
 * in any message this endpoint returns.
 *
 * <p>Separate from {@code StudentController} because the two share a path prefix and nothing else:
 * one is the register, this is a bulk operation on it with its own permissions story, its own error
 * codes and its own report shape.
 */
@RestController
@RequestMapping("/api/students/import")
public class StudentImportController {

    private final StudentImportService imports;

    public StudentImportController(StudentImportService imports) {
        this.imports = imports;
    }

    /**
     * Everything wrong with the file. Writes nothing, whatever it finds.
     *
     * <p>{@code imported} is always 0 here. Not because the import failed — because this endpoint
     * does not import.
     */
    @PreAuthorize("hasAuthority('student:student:manage')")
    @PostMapping(path = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportReport> validate(
            @RequestPart("file") MultipartFile file, @RequestParam UUID academicSessionId) {
        return ApiResponse.success(imports.validate(file, academicSessionId));
    }

    /**
     * The same parse and the same checks, and then the whole file or none of it (ADR-0021 §2).
     *
     * <p>Answers 200 rather than 201 even when it creates six hundred students, because what comes
     * back is a report on a file rather than a resource at an address — there is no {@code Location}
     * that would mean anything, and a client that followed one would find a single student.
     */
    @PreAuthorize("hasAuthority('student:student:manage')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportReport> importStudents(
            @RequestPart("file") MultipartFile file, @RequestParam UUID academicSessionId) {
        return ApiResponse.success(imports.importStudents(file, academicSessionId));
    }
}
