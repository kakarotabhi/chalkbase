package in.chalkbase.student.api;

/**
 * Where a student sits <em>this year</em>, flattened onto a list row.
 *
 * <p>Names only, no ids. A class list is read, not navigated from — the row already carries the
 * student's id, and a client that wants the enrolment itself asks for the student's detail, which
 * carries every enrolment with its ids. Sending four more ids on every one of eight hundred rows to
 * save a call nobody makes is the wrong trade.
 *
 * <p><strong>"Current" means the school's current academic year</strong>, not "the most recent one".
 * See {@code StudentService#currentEnrolments}: a school that has set up next year's session early
 * would otherwise see next year's class against every child while still teaching this year, and
 * nothing on the screen would say the number was for a different year.
 *
 * @param rollNumber null until the class list settles. Assigned after admission, on purpose.
 */
public record CurrentEnrolment(String sessionName, String className, String sectionName, String rollNumber) {}
