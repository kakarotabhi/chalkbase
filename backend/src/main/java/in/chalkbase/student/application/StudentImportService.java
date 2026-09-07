package in.chalkbase.student.application;

import in.chalkbase.academics.api.AcademicSessionRef;
import in.chalkbase.academics.api.AcademicsLookup;
import in.chalkbase.academics.api.SchoolClassRef;
import in.chalkbase.academics.api.SectionRef;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.ChalkbaseException;
import in.chalkbase.student.api.ImportError;
import in.chalkbase.student.api.ImportReport;
import in.chalkbase.student.domain.Gender;
import in.chalkbase.student.domain.Guardian;
import in.chalkbase.student.domain.GuardianRelation;
import in.chalkbase.student.domain.Student;
import in.chalkbase.student.domain.StudentAudit;
import in.chalkbase.student.domain.StudentEnrolment;
import in.chalkbase.student.domain.StudentErrorCode;
import in.chalkbase.student.domain.StudentGuardianLink;
import in.chalkbase.student.domain.StudentStatus;
import in.chalkbase.student.infrastructure.CsvReader;
import in.chalkbase.student.infrastructure.CsvRow;
import in.chalkbase.student.infrastructure.GuardianRepository;
import in.chalkbase.student.infrastructure.PhoneDigits;
import in.chalkbase.student.infrastructure.StudentEnrolmentRepository;
import in.chalkbase.student.infrastructure.StudentGuardianRepository;
import in.chalkbase.student.infrastructure.StudentRepository;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * A school's existing roll, arriving as one CSV file (ADR-0021).
 *
 * <p>Two entry points and they are not interchangeable — {@link #validate} parses and checks and
 * <strong>writes nothing</strong>; {@link #importStudents} does the same parse and the same checks
 * and then commits. Two methods behind two endpoints rather than one with a flag, because a flag
 * defaults to something and the wrong default here writes six hundred rows nobody has looked at.
 *
 * <p><strong>The commit is all or nothing</strong> (ADR-0021 §2). One bad row imports nobody. The
 * friendlier alternative — import the good rows, report the bad — leaves the school with a database
 * in a state nobody planned and a file that can no longer be re-uploaded without either duplicating
 * five hundred children or failing on every one of them.
 *
 * <p><strong>Nothing out of the file is ever logged or put in a message</strong> (ADR-0014,
 * ADR-0021 §6). The file is the largest concentration of children's data this product handles: a few
 * hundred names and dates of birth in one object. It is never written to disk, never attached to the
 * audit event, and the report names rows and columns rather than values. Where a message must
 * explain why something did not match, it lists the <em>school's own</em> classes and sections,
 * which are Internal under ADR-0014.
 *
 * <p><strong>Duplicates inside the file matter as much as duplicates against the database</strong>
 * (ADR-0021). Two rows claiming one admission number, or one roll number in one section, are found
 * here and reported against the second of the two — the database constraint would catch it at
 * commit time, by which point the failure is attributed to whichever row happened to be flushed
 * second and the report would send the school to the wrong line of their spreadsheet.
 */
@Service
@Transactional(readOnly = true)
public class StudentImportService {

    /**
     * The most rows one import may carry (ADR-0021 §6). Above any single Indian school's intake,
     * and low enough that a mistaken upload cannot exhaust memory. The multipart size limits in
     * {@code application.yml} are set to match, and they are the guard that runs first.
     */
    static final int MAX_ROWS = 2_000;

    /**
     * The most errors a report will carry. Everything found is counted — {@code totalErrors} is
     * honest — but a file where every row is wrong produces thousands, and a report nobody can read
     * is not more useful than one they can.
     */
    static final int MAX_REPORTED_ERRORS = 200;

    /**
     * What {@link ImportError#column()} says when the problem is the row's rather than a cell's.
     *
     * <p>The empty string and not null, so a screen can group these under a heading of their own
     * with a comparison rather than a null check — see {@code ImportError}.
     */
    private static final String THE_ROW_ITSELF = "";

    /** How many of the school's own class names an error message lists before giving up. */
    private static final int NAMES_IN_A_MESSAGE = 12;

    private static final String ADMISSION_NUMBER = "admission_number";
    private static final String FULL_NAME = "full_name";
    private static final String DATE_OF_BIRTH = "date_of_birth";
    private static final String GENDER = "gender";
    private static final String STATUS = "status";
    private static final String ADMITTED_ON = "admitted_on";
    private static final String CLASS = "class";
    private static final String SECTION = "section";
    private static final String ROLL_NUMBER = "roll_number";

    // ── Guardian columns (ADR-0021 §4) ──────────────────────────────────────────────────────
    //
    // One guardian per row, not two — a father's columns and a mother's would double every one of
    // them for a relationship most schools will fill in for one parent and leave blank for the
    // other. A student needing a second guardian on record gets one the way any student does today:
    // GuardiansApi search-and-link, after the import, from that child's own record. All five columns
    // here are optional as a group: a row that names none of them simply has no guardian yet.
    private static final String GUARDIAN_NAME = "guardian_name";
    private static final String GUARDIAN_PHONE = "guardian_phone";
    private static final String GUARDIAN_RELATION = "guardian_relation";
    private static final String GUARDIAN_EMAIL = "guardian_email";
    private static final String GUARDIAN_PRIMARY = "guardian_primary";

    /** Without these the file cannot describe a child at all. */
    private static final List<String> REQUIRED_COLUMNS =
            List.of(ADMISSION_NUMBER, FULL_NAME, DATE_OF_BIRTH, GENDER, CLASS, SECTION);

    /**
     * Recognised but not required. {@code status} defaults to {@code ACTIVE}, the guardian columns
     * are a family that is either all absent or names one person (ADR-0021 §4), and the rest are
     * genuinely unknown for a record migrated off a paper register (ADR-0020).
     */
    private static final List<String> OPTIONAL_COLUMNS = List.of(
            STATUS,
            ADMITTED_ON,
            ROLL_NUMBER,
            GUARDIAN_NAME,
            GUARDIAN_PHONE,
            GUARDIAN_RELATION,
            GUARDIAN_EMAIL,
            GUARDIAN_PRIMARY);

    /** Anything else in the file is ignored, so a school's own columns need not be stripped first. */
    private static final Set<String> KNOWN_COLUMNS = Set.copyOf(concat(REQUIRED_COLUMNS, OPTIONAL_COLUMNS));

    /** {@code yyyy-MM-dd} and nothing else. {@code 14/06/2015} is a date; it is not this one. */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /** The first bytes of a zip container, which is what every {@code .xlsx} and {@code .ods} is. */
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    /** The first bytes of the OLE2 compound document that a pre-2007 {@code .xls} is. */
    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0};

    /**
     * A loose shape check for {@code guardian_email}, matching {@code SaveGuardianRequest}'s
     * {@code @Email} so an imported guardian is held to the same bar as one typed in by hand rather
     * than a laxer one because the value arrived from a file.
     */
    private static final Pattern LOOKS_LIKE_AN_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /**
     * Counts only. Never a name, never a date of birth, never a cell — see the class javadoc, and
     * note that {@code StudentImportApiTests} asserts it by capturing the log during an import.
     */
    private static final Logger log = LoggerFactory.getLogger(StudentImportService.class);

    private final StudentRepository students;
    private final StudentEnrolmentRepository enrolments;
    private final GuardianRepository guardians;
    private final StudentGuardianRepository guardianLinks;
    private final AcademicsLookup academics;
    private final AuditService audit;

    public StudentImportService(
            StudentRepository students,
            StudentEnrolmentRepository enrolments,
            GuardianRepository guardians,
            StudentGuardianRepository guardianLinks,
            AcademicsLookup academics,
            AuditService audit) {
        this.students = students;
        this.enrolments = enrolments;
        this.guardians = guardians;
        this.guardianLinks = guardianLinks;
        this.academics = academics;
        this.audit = audit;
    }

    /**
     * Everything wrong with the file, and nothing written.
     *
     * <p>{@code readOnly}, which is the mechanical half of "writes nothing": the transaction cannot
     * flush a change even if some future edit to the checks below created an entity by accident.
     */
    public ImportReport validate(MultipartFile file, UUID academicSessionId) {
        Examination examined = examine(file, requireSession(academicSessionId));
        log.info(
                "Validated a student import: {} row(s), {} valid, {} problem(s)",
                examined.totalRows(),
                examined.validRows(),
                examined.errors().count());
        // Zero for every guardian count too — see importStudents for why these, like `imported`,
        // report what was actually written rather than what a clean file would go on to do.
        return examined.report(0, 0, 0, 0, 0);
    }

    /**
     * The same parse and the same checks, and then the whole file or none of it.
     *
     * <p>The unique indexes are still the backstop and are not made redundant by the checks above:
     * two clerks importing overlapping files at the same instant is a race no read can close, and it
     * ends as a {@code DataIntegrityViolationException}, a mapped conflict, and a transaction that
     * rolled back — which is the all-or-nothing outcome anyway.
     */
    @Transactional
    public ImportReport importStudents(MultipartFile file, UUID academicSessionId) {
        AcademicSessionRef session = requireSession(academicSessionId);
        Examination examined = examine(file, session);

        if (!examined.errors().isEmpty()) {
            // Nothing has been written, because nothing writes before this point. ADR-0021 §2: one
            // bad row imports nobody, so that fixing a typo and re-uploading is always safe. That
            // includes the guardian side: an ambiguous phone match is exactly the kind of problem
            // this gate exists to hold the whole file for.
            log.info(
                    "Refused a student import: {} row(s), {} problem(s). Nothing was written.",
                    examined.totalRows(),
                    examined.errors().count());
            return examined.report(0, 0, 0, 0, 0);
        }

        // Guardians first, one write per distinct phone in the whole file rather than one per row —
        // this is the dedupe ADR-0021 §4 promises: four rows naming the same father produce one
        // guardian here and four links below, whether or not he was already in the directory.
        Map<String, UUID> resolvedGuardianIds = new HashMap<>();
        int guardiansCreated = 0;
        for (Map.Entry<String, GuardianPlan> entry : examined.guardianPlans().entrySet()) {
            GuardianPlan plan = entry.getValue();
            UUID guardianId;
            if (plan.existingId() != null) {
                guardianId = plan.existingId();
            } else {
                Guardian created = guardians.save(new Guardian(plan.fullName(), plan.phone(), plan.email(), null));
                guardianId = created.getId();
                guardiansCreated++;
            }
            resolvedGuardianIds.put(entry.getKey(), guardianId);
        }
        guardians.flush();

        int guardianLinksCreated = 0;
        int studentsLinkedToExistingGuardians = 0;
        for (ImportRow row : examined.rows()) {
            Student student = students.save(new Student(
                    row.admissionNumber(),
                    row.fullName(),
                    row.dateOfBirth(),
                    row.gender(),
                    row.status(),
                    row.admittedOn()));
            enrolments.save(new StudentEnrolment(student, session.id(), row.sectionId(), row.rollNumber()));

            if (row.guardianKey() != null) {
                UUID guardianId = resolvedGuardianIds.get(row.guardianKey());
                guardianLinks.save(new StudentGuardianLink(
                        student,
                        guardians.getReferenceById(guardianId),
                        row.guardianRelation(),
                        row.guardianPrimary()));
                guardianLinksCreated++;
                if (examined.guardianPlans().get(row.guardianKey()).existingId() != null) {
                    // This student's link is the number ADR-0021 §4 is really about: not how many
                    // people the school ended up with, but how many were spared a duplicate.
                    studentsLinkedToExistingGuardians++;
                }
            }
        }
        students.flush();
        enrolments.flush();
        guardianLinks.flush();

        int guardiansMatched = examined.guardianPlans().size() - guardiansCreated;

        // ONE row for the whole import (ADR-0021 §7). Six hundred ENTITY_CREATED rows would bury
        // every other thing that happened that day in the one log a principal reads to find out
        // what happened that day. The entityId is the academic session's — the import is a fact
        // about the year it loaded, and there is no single child it is about (StudentAudit).
        //
        // The count rides on `record_count`, a column of its own rather than a number squeezed into
        // changedFields. A count is a property of the event, not a value of a field, so ADR-0018 §2
        // is untouched — and encoding it as `imported_600` would have passed the field-name check
        // while being precisely the smuggling that check exists to stop.
        //
        // The guardian-link field names ride along on this same event rather than one of their own:
        // every link created here was created in the same act as the student it belongs to, so it is
        // one fact about this import, not a second bulk fact needing a second row.
        audit.recordBulkChange(
                StudentAudit.STUDENTS_IMPORTED,
                StudentAudit.STUDENT_IMPORT,
                session.id().toString(),
                guardianLinksCreated > 0
                        ? List.of(
                                "admissionNumber",
                                "fullName",
                                "dateOfBirth",
                                "gender",
                                "status",
                                "admittedOn",
                                "academicSessionId",
                                "sectionId",
                                "rollNumber",
                                "guardianId",
                                "guardianRelation",
                                "guardianPrimary")
                        : List.of(
                                "admissionNumber",
                                "fullName",
                                "dateOfBirth",
                                "gender",
                                "status",
                                "admittedOn",
                                "academicSessionId",
                                "sectionId",
                                "rollNumber"),
                examined.rows().size());

        // A second, separate bulk row — see StudentAudit#GUARDIANS_IMPORTED — and only when it
        // actually happened: a file whose guardians all matched the directory wrote no new person,
        // and an audit row for zero would be worse than no row at all.
        if (guardiansCreated > 0) {
            audit.recordBulkChange(
                    StudentAudit.GUARDIANS_IMPORTED,
                    StudentAudit.GUARDIAN,
                    session.id().toString(),
                    List.of("fullName", "phone", "email"),
                    guardiansCreated);
        }

        log.info(
                "Imported {} student(s) into one academic session, with {} new guardian(s) and {} already on file",
                examined.rows().size(),
                guardiansCreated,
                guardiansMatched);
        return examined.report(
                examined.rows().size(),
                guardiansCreated,
                guardiansMatched,
                guardianLinksCreated,
                studentsLinkedToExistingGuardians);
    }

    // ── Reading and checking the file ────────────────────────────────────────────────────────

    /**
     * Parses the file and finds everything wrong with it. Writes nothing, whichever caller asked.
     *
     * <p>The order matters and is not arbitrary: whole-file failures are raised as error codes
     * before a single row is looked at, because a school that uploaded a workbook or a file with no
     * header needs one sentence, not four hundred row errors saying the same thing.
     */
    private Examination examine(MultipartFile file, AcademicSessionRef session) {
        Ladder ladder = new Ladder(academics.classes(), academics.sections());
        ladder.requireSomethingToImportInto();
        Errors errors = new Errors();
        List<ImportRow> rows = new ArrayList<>();
        Seen seen = new Seen();
        int totalRows = 0;

        try (InputStream stream = openAsCsv(file);
                CsvReader csv = new CsvReader(stream)) {

            CsvRow first = csv.next();
            if (first == null || first.blank()) {
                throw new ChalkbaseException(StudentErrorCode.IMPORT_FILE_EMPTY);
            }
            Header header = Header.of(first);

            CsvRow row;
            while ((row = csv.next()) != null) {
                if (row.blank()) {
                    // A trailing newline, or a blank line where a class ended. Not a mistake, and
                    // not something to tell anybody about — but it still occupies a row number,
                    // because it still occupies a row of their spreadsheet.
                    continue;
                }
                if (++totalRows > MAX_ROWS) {
                    throw new ChalkbaseException(
                            StudentErrorCode.IMPORT_TOO_MANY_ROWS,
                            "That file has more than the " + MAX_ROWS
                                    + " rows one import may carry. Split it and upload the parts.",
                            Map.of("maxRows", String.valueOf(MAX_ROWS)));
                }
                ImportRow parsed = read(row, header, ladder, seen, errors);
                if (parsed != null) {
                    rows.add(parsed);
                }
            }
        } catch (IOException e) {
            // The upload itself could not be read — a dropped connection, not a wrong file. The
            // exception is deliberately not passed on: nothing in it is diagnostic, and a stream
            // over a school's roll is not something to widen the surface of.
            log.warn("A student import upload could not be read to the end");
            throw new ChalkbaseException(StudentErrorCode.IMPORT_FILE_UNREADABLE);
        }

        if (totalRows == 0) {
            throw new ChalkbaseException(StudentErrorCode.IMPORT_FILE_EMPTY);
        }

        checkAgainstTheRegister(rows, session, errors);
        Map<String, GuardianPlan> guardianPlans = checkGuardiansAgainstDirectory(seen.guardianGroups, errors);
        return new Examination(totalRows, rows, errors, guardianPlans);
    }

    /**
     * Reads one row's cells into a student, recording everything wrong with it rather than the first
     * thing.
     *
     * @return the row, or null if anything about it was wrong. A null return is not "stop" — the
     *     next row is read either way, because a school fixing one error per upload round trip will
     *     give up long before the file is clean (ADR-0021).
     */
    private ImportRow read(CsvRow row, Header header, Ladder ladder, Seen seen, Errors errors) {
        int number = row.number();
        if (row.malformed()) {
            errors.add(number, THE_ROW_ITSELF, row.problem());
            return null;
        }
        if (row.cells().size() != header.width()) {
            errors.add(
                    number,
                    THE_ROW_ITSELF,
                    "this row has " + row.cells().size() + " values where the first row names " + header.width()
                            + " columns. A value that contains a comma has to be inside double quotes.");
            return null;
        }

        int before = errors.count();

        String admissionNumber = required(row, header, ADMISSION_NUMBER, 40, errors);
        String fullName = required(row, header, FULL_NAME, 200, errors);
        LocalDate dateOfBirth = date(row, header, DATE_OF_BIRTH, true, errors);
        if (dateOfBirth != null && !dateOfBirth.isBefore(LocalDate.now())) {
            // The manual admission form says the same thing with @Past. A child admitted on their
            // day of birth is not a case; a year typed as 2027 for 2017 reaches a certificate.
            errors.add(number, DATE_OF_BIRTH, "is not a date in the past");
            dateOfBirth = null;
        }
        Gender gender = choice(row, header, GENDER, Gender.class, null, errors);
        StudentStatus status = choice(row, header, STATUS, StudentStatus.class, StudentStatus.ACTIVE, errors);
        LocalDate admittedOn = date(row, header, ADMITTED_ON, false, errors);
        UUID sectionId = placement(number, header.cell(row, CLASS), header.cell(row, SECTION), ladder, errors);
        String rollNumber = optional(row, header, ROLL_NUMBER, 20, errors);

        // Within-file duplicates, checked before the database is asked anything. The database would
        // find these too, at commit, and would blame the wrong row for them.
        if (admissionNumber != null) {
            Integer earlier = seen.admissionNumbers.putIfAbsent(admissionNumber, number);
            if (earlier != null) {
                errors.add(
                        number,
                        ADMISSION_NUMBER,
                        "another row in this file already uses this admission number (row " + earlier + ")");
            }
        }
        if (sectionId != null && rollNumber != null) {
            Integer earlier = seen.rollNumbers.putIfAbsent(sectionId + " " + rollNumber, number);
            if (earlier != null) {
                errors.add(
                        number,
                        ROLL_NUMBER,
                        "another row in this file already gives this roll number in the same section (row " + earlier
                                + ")");
            }
        }

        // The guardian this row names, if any (ADR-0021 §4) — registered against every other row's
        // guardian in this same file before the row is judged, for the same reason the admission
        // number is: a database round trip would find the same conflicts later and blame whichever
        // row happened to flush second.
        GuardianCell guardianCell = guardianCell(row, header, errors);
        String guardianKey = null;
        GuardianRelation guardianRelation = null;
        boolean guardianPrimary = false;
        if (guardianCell != null && guardianCell.name() != null && guardianCell.phone() != null) {
            guardianKey = PhoneDigits.canonicalKey(guardianCell.phone());
            guardianRelation = guardianCell.relation();
            guardianPrimary = guardianCell.primary();
            registerGuardian(number, guardianKey, guardianCell, seen, errors);
        }

        if (errors.count() != before) {
            return null;
        }
        return new ImportRow(
                number,
                admissionNumber,
                fullName,
                dateOfBirth,
                gender,
                status,
                admittedOn,
                sectionId,
                rollNumber,
                guardianKey,
                guardianRelation,
                guardianPrimary);
    }

    /**
     * The two things the school's existing records can refuse, asked in two queries rather than two
     * per row.
     *
     * <p>Checked by {@code validate} as well as by the commit, and ADR-0021 says why that is not a
     * disclosure to be nervous about: this endpoint already needs {@code student:student:manage},
     * and telling a caller which admission numbers exist is a read of the student register by
     * another name.
     */
    private void checkAgainstTheRegister(List<ImportRow> rows, AcademicSessionRef session, Errors errors) {
        if (rows.isEmpty()) {
            return;
        }

        Set<String> taken = new HashSet<>(students.findExistingAdmissionNumbers(
                rows.stream().map(ImportRow::admissionNumber).collect(Collectors.toSet())));
        List<String> rollNumbers = rows.stream()
                .map(ImportRow::rollNumber)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Set<String> takenRolls = rollNumbers.isEmpty()
                ? Set.of()
                : enrolments.findByAcademicSessionIdAndRollNumberIn(session.id(), rollNumbers).stream()
                        .map(enrolment -> enrolment.getSectionId() + " " + enrolment.getRollNumber())
                        .collect(Collectors.toSet());

        for (ImportRow row : rows) {
            if (taken.contains(row.admissionNumber())) {
                errors.add(row.row(), ADMISSION_NUMBER, "a student at this school already has this admission number");
            }
            if (row.rollNumber() != null && takenRolls.contains(row.sectionId() + " " + row.rollNumber())) {
                errors.add(
                        row.row(),
                        ROLL_NUMBER,
                        "another student already has this roll number in that section for this academic year");
            }
        }
    }

    // ── Guardians (ADR-0021 §4) ──────────────────────────────────────────────────────────────

    /**
     * The guardian columns of one row, or null when the row names no guardian at all.
     *
     * <p>{@code guardian_name} and {@code guardian_phone} are required <em>together</em>: matching
     * happens on the phone, so a name with no phone cannot be matched against anything and a phone
     * with no name cannot be told apart from anyone else's. The other three columns are read only
     * once these two are present — a stray {@code guardian_relation} on an otherwise empty guardian
     * is itself the mistake being reported, not a reason to guess the rest of the row.
     */
    private GuardianCell guardianCell(CsvRow row, Header header, Errors errors) {
        String rawName = header.cell(row, GUARDIAN_NAME);
        String rawPhone = header.cell(row, GUARDIAN_PHONE);
        String rawRelation = header.cell(row, GUARDIAN_RELATION);
        String rawEmail = header.cell(row, GUARDIAN_EMAIL);
        String rawPrimary = header.cell(row, GUARDIAN_PRIMARY);
        if (rawName.isEmpty()
                && rawPhone.isEmpty()
                && rawRelation.isEmpty()
                && rawEmail.isEmpty()
                && rawPrimary.isEmpty()) {
            return null;
        }

        String name = required(row, header, GUARDIAN_NAME, 200, errors);
        // guardian.phone is varchar(20) (ADR-0020) — a number with an extension, or written out in
        // full internationally, can genuinely be longer, and this is where that fails: as a named
        // row error a school can read and fix, not a 500 from a database refusing the insert.
        String phone = required(row, header, GUARDIAN_PHONE, 20, errors);
        GuardianRelation relation =
                choice(row, header, GUARDIAN_RELATION, GuardianRelation.class, GuardianRelation.GUARDIAN, errors);
        String email = optional(row, header, GUARDIAN_EMAIL, 320, errors);
        if (email != null && !LOOKS_LIKE_AN_EMAIL.matcher(email).matches()) {
            errors.add(row.number(), GUARDIAN_EMAIL, "is not a valid email address");
            email = null;
        }
        boolean primary = booleanCell(row, header, GUARDIAN_PRIMARY, true, errors);

        return new GuardianCell(name, phone, relation, email, primary);
    }

    /** {@code TRUE} or {@code FALSE}, case forgiven, an empty cell meaning {@code fallback}. */
    private boolean booleanCell(CsvRow row, Header header, String column, boolean fallback, Errors errors) {
        String value = header.cell(row, column);
        if (value.isEmpty()) {
            return fallback;
        }
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        errors.add(row.number(), column, "has to be TRUE or FALSE");
        return fallback;
    }

    /**
     * Groups this row's guardian with every other row naming the same phone number, within this
     * file, so that four rows naming one father produce one guardian rather than four.
     *
     * <p>The first row to use a phone number in the file establishes what that guardian is called. A
     * later row with the <strong>same</strong> name (case and spacing forgiven, like a class name —
     * see {@code key}) joins the group silently. A later row with a <strong>different</strong> name
     * is refused: an import has nobody to ask which of two spellings is right, and guessing either
     * way risks attaching a child to a stranger, which is worse than making the school fix the file.
     */
    private void registerGuardian(int number, String guardianKey, GuardianCell cell, Seen seen, Errors errors) {
        String nameKey = key(cell.name());
        GuardianGroup group = seen.guardianGroups.get(guardianKey);
        if (group == null) {
            seen.guardianGroups.put(
                    guardianKey, new GuardianGroup(number, cell.name(), cell.phone(), cell.email(), nameKey));
            return;
        }
        if (!group.nameKey.equals(nameKey)) {
            errors.add(
                    number,
                    GUARDIAN_PHONE,
                    "this phone number is already used by a guardian with a different name earlier in this"
                            + " file (row " + group.firstRow + ")");
            return;
        }
        if (group.email == null && cell.email() != null) {
            // The first row to mention this guardian did not happen to give an email; a later one
            // did. Both rows are the same person, so the detail is not lost for want of being on the
            // row that happened to establish the group.
            group.email = cell.email();
        }
        group.rows.add(number);
    }

    /**
     * Resolves every phone-distinct guardian this file mentions against the school's existing
     * directory, once, rather than once per row or once per group.
     *
     * <p>Three outcomes per group, and this is the decision ADR-0021 §4 records:
     *
     * <ul>
     *   <li><strong>The phone matches a directory guardian with the same name</strong> (case and
     *       spacing forgiven): reuse that guardian. This is the common case an import exists to get
     *       right — a father already in the directory from an older sibling.
     *   <li><strong>The phone matches nobody in the directory</strong>: a new guardian is planned,
     *       using the first row's spelling of the name and the first row's typed phone number.
     *   <li><strong>The phone matches a directory guardian with a different name</strong>: refused,
     *       for every row in the file that named this number. Two people can genuinely share a phone
     *       number (ADR-0020 §5), and the manual form's answer to that is to warn and let a human
     *       decide. An import has no human mid-file to ask, and the two ways of guessing are both
     *       worse than asking the school to look: matching anyway can attach a child to a stranger's
     *       guardian, and creating a second guardian on a shared number recreates the very duplicate
     *       this slice exists to prevent. So the row is refused, pointing at the directory rather
     *       than at a guess.
     * </ul>
     */
    private Map<String, GuardianPlan> checkGuardiansAgainstDirectory(Map<String, GuardianGroup> groups, Errors errors) {
        if (groups.isEmpty()) {
            return Map.of();
        }

        Map<String, List<Guardian>> directory = new HashMap<>();
        for (Guardian guardian : guardians.findAllWithPhone()) {
            String canonical = PhoneDigits.canonicalKey(guardian.getPhone());
            if (!canonical.isEmpty()) {
                directory
                        .computeIfAbsent(canonical, ignored -> new ArrayList<>())
                        .add(guardian);
            }
        }

        Map<String, GuardianPlan> plans = new LinkedHashMap<>();
        for (Map.Entry<String, GuardianGroup> entry : groups.entrySet()) {
            String canonicalKey = entry.getKey();
            GuardianGroup group = entry.getValue();
            List<Guardian> candidates = directory.getOrDefault(canonicalKey, List.of());
            Guardian matched = candidates.stream()
                    .filter(candidate -> key(candidate.getFullName()).equals(group.nameKey))
                    .findFirst()
                    .orElse(null);

            if (matched != null) {
                plans.put(canonicalKey, GuardianPlan.existing(matched.getId()));
            } else if (candidates.isEmpty()) {
                plans.put(canonicalKey, GuardianPlan.toCreate(group.fullName, group.phone, group.email));
            } else {
                for (int rowNumber : group.rows) {
                    errors.add(
                            rowNumber,
                            GUARDIAN_PHONE,
                            "this phone number already belongs to a different guardian at this school —"
                                    + " check the guardian directory before importing, or correct the name"
                                    + " here if it is a misspelling of the same person");
                }
            }
        }
        return plans;
    }

    // ── One cell at a time ───────────────────────────────────────────────────────────────────

    private String required(CsvRow row, Header header, String column, int maxLength, Errors errors) {
        String value = header.cell(row, column);
        if (value.isEmpty()) {
            errors.add(row.number(), column, "is required and is empty");
            return null;
        }
        return withinLength(row, column, value, maxLength, errors);
    }

    private String optional(CsvRow row, Header header, String column, int maxLength, Errors errors) {
        String value = header.cell(row, column);
        return value.isEmpty() ? null : withinLength(row, column, value, maxLength, errors);
    }

    private String withinLength(CsvRow row, String column, String value, int maxLength, Errors errors) {
        if (value.length() > maxLength) {
            errors.add(row.number(), column, "is longer than the " + maxLength + " characters this column holds");
            return null;
        }
        return value;
    }

    private LocalDate date(CsvRow row, Header header, String column, boolean required, Errors errors) {
        String value = header.cell(row, column);
        if (value.isEmpty()) {
            if (required) {
                errors.add(row.number(), column, "is required and is empty");
            }
            return null;
        }
        try {
            return LocalDate.parse(value, DATE);
        } catch (DateTimeParseException e) {
            // The value is not echoed: it is a child's date of birth (ADR-0014). The form it should
            // have been in is what the person needs, and an example of it is not anybody's data.
            errors.add(row.number(), column, "is not a date in yyyy-MM-dd form, for example 2015-06-14");
            return null;
        }
    }

    /**
     * One of a fixed set of words, matched without regard to case or surrounding space.
     *
     * @param fallback what an empty cell means, or null if the column is required
     */
    private <E extends Enum<E>> E choice(
            CsvRow row, Header header, String column, Class<E> type, E fallback, Errors errors) {
        String value = header.cell(row, column);
        if (value.isEmpty()) {
            if (fallback == null) {
                errors.add(row.number(), column, "is required and is empty");
            }
            return fallback;
        }
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        // A single letter is accepted where it is unambiguous — `M`, `F`, `O` for gender — because
        // Indian school spreadsheets overwhelmingly carry the initial, and refusing it makes an
        // office edit six hundred cells to say what the file already said. It is only accepted when
        // exactly one constant of this enum starts with that letter, so it can never quietly pick
        // between two: `A` against ACTIVE and no other status resolves; against a hypothetical
        // ACTIVE and ARCHIVED it would not, and the school is told the full words instead.
        if (value.length() == 1) {
            List<E> initialMatches = Arrays.stream(type.getEnumConstants())
                    .filter(candidate -> candidate.name().regionMatches(true, 0, value, 0, 1))
                    .toList();
            if (initialMatches.size() == 1) {
                return initialMatches.get(0);
            }
        }
        errors.add(row.number(), column, "has to be one of " + words(type));
        return null;
    }

    /**
     * A class name and a section name, resolved against this school's own ladder (ADR-0021 §3).
     *
     * <p>The messages are the point of this method. A school whose file says "Class V" has to be
     * able to read the answer and see that its own ladder says "Class 5" — so the message lists the
     * school's classes, which are Internal under ADR-0014, rather than quoting back the cell, which
     * is not.
     *
     * @return the section, or null once it has recorded why there was not one
     */
    private UUID placement(int number, String className, String sectionName, Ladder ladder, Errors errors) {
        boolean missing = false;
        if (className.isEmpty()) {
            errors.add(number, CLASS, "is required and is empty");
            missing = true;
        }
        if (sectionName.isEmpty()) {
            errors.add(number, SECTION, "is required and is empty");
            missing = true;
        }
        if (missing) {
            return null;
        }

        SchoolClassRef schoolClass = ladder.activeClass(className);
        if (schoolClass == null) {
            errors.add(number, CLASS, ladder.whyNoClass(className));
            return null;
        }
        SectionRef section = ladder.activeSection(schoolClass.id(), sectionName);
        if (section == null) {
            errors.add(number, SECTION, ladder.whyNoSection(schoolClass, sectionName));
            return null;
        }
        return section.id();
    }

    // ── The file as a whole ──────────────────────────────────────────────────────────────────

    /**
     * Opens the upload, having first refused the one wrong file everybody will send.
     *
     * <p>An {@code .xlsx} is a zip and an old {@code .xls} is an OLE2 container; decoded as text
     * either becomes noise, and the header check would tell a school office that its column names
     * were wrong. What they need to be told is "Save As, CSV" (ADR-0021 §5), so it is worth four
     * bytes to know.
     */
    private static InputStream openAsCsv(MultipartFile file) throws IOException {
        BufferedInputStream stream = new BufferedInputStream(file.getInputStream());
        try {
            stream.mark(8);
            byte[] first = stream.readNBytes(4);
            stream.reset();
            if (Arrays.equals(first, ZIP_MAGIC) || Arrays.equals(first, OLE2_MAGIC)) {
                throw new ChalkbaseException(StudentErrorCode.IMPORT_NOT_CSV);
            }
            return stream;
        } catch (IOException | RuntimeException e) {
            stream.close();
            throw e;
        }
    }

    /** Which column is where, by the names the file's own first row gave them. */
    private record Header(Map<String, Integer> columns, int width) {

        static Header of(CsvRow row) {
            Map<String, Integer> columns = new LinkedHashMap<>();
            Set<String> repeated = new LinkedHashSet<>();
            List<String> cells = row.cells();
            for (int i = 0; i < cells.size(); i++) {
                String name = normalise(cells.get(i));
                if (name.isEmpty()) {
                    // An unnamed column. A school's file often ends with a few of them, left over
                    // from a formula somebody deleted, and they are nothing to complain about.
                    continue;
                }
                Integer earlier = columns.putIfAbsent(name, i);
                if (earlier != null && KNOWN_COLUMNS.contains(name)) {
                    repeated.add(name);
                }
            }

            if (!repeated.isEmpty()) {
                // Refused rather than resolved by taking the first or the last: the two columns will
                // disagree in some row, and either choice silently imports the wrong half of a file.
                throw new ChalkbaseException(
                        StudentErrorCode.IMPORT_COLUMN_REPEATED,
                        StudentErrorCode.IMPORT_COLUMN_REPEATED.defaultMessage(),
                        repeated.stream().collect(Collectors.toMap(name -> name, name -> "named more than once")));
            }

            List<String> missing = REQUIRED_COLUMNS.stream()
                    .filter(column -> !columns.containsKey(column))
                    .toList();
            if (!missing.isEmpty()) {
                // Both halves of the answer. "date_of_birth is missing" alone leaves a school
                // that wrote `dob` looking for a column it believes is there; naming `dob` back
                // to them as unrecognised is what closes the loop. Neither is anybody's data.
                Map<String, String> details = new LinkedHashMap<>();
                missing.forEach(column -> details.put(column, "required, and not named in the first row"));
                columns.keySet().stream()
                        .filter(column -> !KNOWN_COLUMNS.contains(column))
                        .forEach(column -> details.put(column, "in the file, but not a column this import knows"));
                throw new ChalkbaseException(
                        StudentErrorCode.IMPORT_COLUMNS_MISSING,
                        "The first row of that file has to name these columns: "
                                + String.join(", ", REQUIRED_COLUMNS) + ". These may be left empty: "
                                + String.join(", ", OPTIONAL_COLUMNS) + ". The order does not matter.",
                        details);
            }
            return new Header(Map.copyOf(columns), cells.size());
        }

        /** The cell this row holds for that column, trimmed, or empty when the column is not in the file. */
        String cell(CsvRow row, String column) {
            Integer at = columns.get(column);
            if (at == null || at >= row.cells().size()) {
                return "";
            }
            return row.cells().get(at).trim();
        }
    }

    /**
     * A column name as this import matches them: without regard to case, to surrounding space, or to
     * whether the school wrote "Admission Number", "admission-number" or "admission_number".
     *
     * <p>Lenient on purpose. The header is the school's own spelling of a name this product chose,
     * and refusing a file over a capital letter would be a worse answer than accepting one.
     */
    private static String normalise(String name) {
        String cleaned = name.strip()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return cleaned;
    }

    /** A name as this import matches a class or a section: case and inner spacing forgiven, spelling not. */
    private static String key(String name) {
        return name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static <T> List<T> concat(List<T> first, List<T> second) {
        List<T> all = new ArrayList<>(first);
        all.addAll(second);
        return List.copyOf(all);
    }

    private static <E extends Enum<E>> String words(Class<E> type) {
        List<String> names =
                Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
        return String.join(", ", names.subList(0, names.size() - 1)) + " or " + names.get(names.size() - 1);
    }

    private AcademicSessionRef requireSession(UUID sessionId) {
        return academics
                .session(sessionId)
                .orElseThrow(() -> new ChalkbaseException(StudentErrorCode.UNKNOWN_ACADEMIC_SESSION));
    }

    // ── Working state, none of which outlives the request ────────────────────────────────────

    /**
     * This school's ladder, indexed by name, read once for the whole file.
     *
     * <p>Retired classes and sections are indexed separately from live ones, because "that class was
     * retired" and "no class is called that" are different answers and a school given the wrong one
     * looks for a spelling mistake that is not there.
     */
    private static final class Ladder {

        private final Map<String, SchoolClassRef> activeClasses = new HashMap<>();
        private final Set<String> retiredClasses = new HashSet<>();
        private final Map<String, SectionRef> activeSections = new HashMap<>();
        private final Set<String> retiredSections = new HashSet<>();
        private final Map<UUID, List<String>> sectionNamesByClass = new LinkedHashMap<>();
        private final List<String> classNames;

        Ladder(List<SchoolClassRef> classes, List<SectionRef> sections) {
            for (SchoolClassRef schoolClass : classes) {
                if (schoolClass.active()) {
                    activeClasses.putIfAbsent(key(schoolClass.name()), schoolClass);
                } else {
                    retiredClasses.add(key(schoolClass.name()));
                }
            }
            for (SectionRef section : sections) {
                String index = section.classId() + " " + key(section.name());
                if (section.active()) {
                    activeSections.putIfAbsent(index, section);
                    sectionNamesByClass
                            .computeIfAbsent(section.classId(), id -> new ArrayList<>())
                            .add(section.name());
                } else {
                    retiredSections.add(index);
                }
            }
            classNames = classes.stream()
                    .filter(SchoolClassRef::active)
                    .sorted(Comparator.comparingInt(SchoolClassRef::sequence))
                    .map(SchoolClassRef::name)
                    .toList();
        }

        /**
         * Refuses the whole file when the school has nowhere to put anybody.
         *
         * <p>A school that uploads its roll before setting up its classes is an ordinary first-day
         * mistake, and six hundred identical "no class called Class 5" row errors describe the
         * symptom rather than the cause. This is the cause, and the fix is one screen away.
         */
        void requireSomethingToImportInto() {
            if (classNames.isEmpty()) {
                throw new ChalkbaseException(
                        StudentErrorCode.IMPORT_NO_CLASS_LADDER,
                        "This school has no classes set up yet, so there is nothing to import students"
                                + " into. Set up the class ladder under Academics first, then import.");
            }
            if (activeSections.isEmpty()) {
                throw new ChalkbaseException(
                        StudentErrorCode.IMPORT_NO_CLASS_LADDER,
                        "This school's classes have no sections yet, and every student is imported into"
                                + " a section. Add the sections under Academics first, then import.");
            }
        }

        SchoolClassRef activeClass(String name) {
            return activeClasses.get(key(name));
        }

        SectionRef activeSection(UUID classId, String name) {
            return activeSections.get(classId + " " + key(name));
        }

        String whyNoClass(String name) {
            if (retiredClasses.contains(key(name))) {
                return "names a class this school has retired, so nobody can be admitted into it";
            }
            if (classNames.isEmpty()) {
                return "names a class this school does not have — this school has no classes set up yet."
                        + " Set up the class ladder first, then import.";
            }
            return "names a class this school does not have. Its classes are: " + list(classNames)
                    + ". The name has to match one of those exactly, apart from capitals and spacing.";
        }

        String whyNoSection(SchoolClassRef schoolClass, String name) {
            if (retiredSections.contains(schoolClass.id() + " " + key(name))) {
                return "names a section of " + schoolClass.name() + " that this school has retired";
            }
            List<String> names = sectionNamesByClass.getOrDefault(schoolClass.id(), List.of());
            if (names.isEmpty()) {
                return schoolClass.name() + " has no sections set up at this school yet";
            }
            return "names a section " + schoolClass.name() + " does not have. Its sections are: " + list(names) + ".";
        }

        private static String list(List<String> names) {
            if (names.size() <= NAMES_IN_A_MESSAGE) {
                return String.join(", ", names);
            }
            return String.join(", ", names.subList(0, NAMES_IN_A_MESSAGE)) + " and "
                    + (names.size() - NAMES_IN_A_MESSAGE) + " more";
        }
    }

    /** What the file has already claimed for itself, so the second claim is the one reported. */
    private static final class Seen {
        private final Map<String, Integer> admissionNumbers = new HashMap<>();
        private final Map<String, Integer> rollNumbers = new HashMap<>();

        /**
         * Every distinct phone number this file has named a guardian with, in the order the file
         * first mentioned each one. {@code LinkedHashMap} rather than {@code HashMap} so that
         * {@code checkGuardiansAgainstDirectory} and the commit that follows it create guardians in
         * the file's own order — nothing depends on that order being any particular one, but nothing
         * should have to wonder either.
         */
        private final Map<String, GuardianGroup> guardianGroups = new LinkedHashMap<>();
    }

    /**
     * One guardian's worth of cells off a row, before it is known whether that guardian is new or
     * already in the directory. Not classified like {@code ImportRow} — it never leaves the method
     * that builds it and is folded into a {@link GuardianGroup} or discarded within the same call.
     */
    private record GuardianCell(String name, String phone, GuardianRelation relation, String email, boolean primary) {}

    /**
     * Every row in <em>this file</em> that named the same phone number, and what they agree the
     * guardian is called — see {@code registerGuardian}. One of these exists per distinct phone
     * number the file mentions, whether or not that number turns out to belong to somebody already
     * in the directory.
     */
    private static final class GuardianGroup {
        private final int firstRow;
        private final String fullName;
        private final String phone;
        private final String nameKey;
        private final List<Integer> rows = new ArrayList<>();
        private String email;

        GuardianGroup(int firstRow, String fullName, String phone, String email, String nameKey) {
            this.firstRow = firstRow;
            this.fullName = fullName;
            this.phone = phone;
            this.email = email;
            this.nameKey = nameKey;
            this.rows.add(firstRow);
        }
    }

    /**
     * What {@code checkGuardiansAgainstDirectory} decided to do about one {@link GuardianGroup}:
     * reuse a guardian that already exists, or create one from the group's own details. Never both —
     * {@code existingId() == null} is exactly the signal that the other three fields are the ones to
     * use.
     */
    private static final class GuardianPlan {
        private final UUID existingId;
        private final String fullName;
        private final String phone;
        private final String email;

        private GuardianPlan(UUID existingId, String fullName, String phone, String email) {
            this.existingId = existingId;
            this.fullName = fullName;
            this.phone = phone;
            this.email = email;
        }

        static GuardianPlan existing(UUID id) {
            return new GuardianPlan(id, null, null, null);
        }

        static GuardianPlan toCreate(String fullName, String phone, String email) {
            return new GuardianPlan(null, fullName, phone, email);
        }

        UUID existingId() {
            return existingId;
        }

        String fullName() {
            return fullName;
        }

        String phone() {
            return phone;
        }

        String email() {
            return email;
        }
    }

    /**
     * Everything wrong with the file, in the order it was found, and which rows are spoilt.
     *
     * <p>Uncapped while collecting and capped only when the report is built, so that the two hundred
     * a school is shown are the first two hundred <em>rows</em> rather than whichever checks
     * happened to run first — the database checks run after every row has been read, and a report
     * that showed those and hid row 3's empty name would be a worse report.
     */
    private static final class Errors {

        private final List<ImportError> found = new ArrayList<>();
        private final Set<Integer> spoiltRows = new HashSet<>();

        void add(int row, String column, String message) {
            found.add(new ImportError(row, column, message));
            spoiltRows.add(row);
        }

        int count() {
            return found.size();
        }

        Set<Integer> spoiltRows() {
            return spoiltRows;
        }

        boolean isEmpty() {
            return found.isEmpty();
        }

        List<ImportError> inRowOrder() {
            List<ImportError> sorted = new ArrayList<>(found);
            sorted.sort(Comparator.comparingInt(ImportError::row));
            return sorted;
        }
    }

    /** What one pass over the file found. Held for the length of the request and no longer. */
    private record Examination(
            int totalRows, List<ImportRow> rows, Errors errors, Map<String, GuardianPlan> guardianPlans) {

        int validRows() {
            return totalRows - errors.spoiltRows().size();
        }

        /**
         * @param imported students actually written. Zero from {@code validate}, and zero from a
         *     commit that found anything wrong.
         * @param guardiansCreated new guardian person records actually written. Zero unless
         *     {@code imported} is also non-zero, for the same all-or-nothing reason.
         * @param guardiansMatched guardians the file's rows were linked to <em>without</em> creating
         *     them — already in the directory, or already claimed by an earlier row in this file.
         * @param guardianLinksCreated student-guardian links actually written — every row that named
         *     a guardian and was actually imported, whether that guardian was created or matched.
         * @param studentsLinkedToExistingGuardians how many of those links pointed at a guardian
         *     that already existed before this import ran — the number ADR-0021 §4 is actually
         *     about: not how many people the school ended up with, but how many rows were spared a
         *     duplicate.
         */
        ImportReport report(
                int imported,
                int guardiansCreated,
                int guardiansMatched,
                int guardianLinksCreated,
                int studentsLinkedToExistingGuardians) {
            List<ImportError> all = errors.inRowOrder();
            List<ImportError> reported =
                    all.size() <= MAX_REPORTED_ERRORS ? all : List.copyOf(all.subList(0, MAX_REPORTED_ERRORS));
            return new ImportReport(
                    totalRows,
                    validRows(),
                    imported,
                    guardiansCreated,
                    guardiansMatched,
                    guardianLinksCreated,
                    studentsLinkedToExistingGuardians,
                    all.size(),
                    reported);
        }
    }
}
