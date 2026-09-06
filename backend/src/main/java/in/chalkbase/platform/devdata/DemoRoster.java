package in.chalkbase.platform.devdata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The invented school that {@link DemoSchoolSeeder} builds: its ladder, its children and the people
 * responsible for them.
 *
 * <p><strong>Every person named in this file is invented</strong> (AGENTS rule 9, ADR-0014). None of
 * it came from a real school and none of it ever may — demo data that started life as a real class
 * list is a data breach with a friendly name on it. The phone numbers are in the reserved-looking
 * ranges nobody answers and the email addresses are all under {@code example.com}, which RFC 2606
 * reserves for exactly this.
 *
 * <p>The shape here is chosen to make ADR-0020 §5 visible on screen rather than to look tidy: five
 * families put two or three children behind a <em>single</em> guardian record, so correcting one
 * phone number in the demo visibly corrects it for every sibling. Four children have both parents
 * linked, and four have no guardian at all — the empty state is a state, and a screen that has never
 * been shown one is a screen nobody has tested.
 *
 * <p>Values are plain strings rather than the owning modules' enums on purpose. {@code Gender},
 * {@code StudentStatus}, {@code GuardianRelation} and {@code Board} live in their modules'
 * {@code domain} packages, which are not exposed across a module boundary — see the class javadoc on
 * {@link DemoSchoolSeeder} for why this seeder speaks JSON instead of importing them.
 */
final class DemoRoster {

    /** Sections A and B under every class, which is what the ladder below is multiplied by. */
    static final List<String> SECTION_NAMES = List.of("A", "B");

    /**
     * The ladder in the order a school reads it. The API appends each class at
     * {@code max(sequence) + 1} (ADR-0019), so creating them in this order is what puts Nursery
     * before Class 8 — there is no sequence field to set and none to get wrong.
     */
    static final List<String> CLASS_NAMES = List.of(
            "Nursery", "LKG", "UKG", "Class 1", "Class 2", "Class 3", "Class 4", "Class 5", "Class 6", "Class 7",
            "Class 8");

    /** Nursery admits three-year-olds; every rung above adds a year. */
    static final int YOUNGEST_AGE = 3;

    private static final String FATHER = "FATHER";
    private static final String MOTHER = "MOTHER";

    private static final String MALE = "MALE";
    private static final String FEMALE = "FEMALE";
    private static final String OTHER = "OTHER";

    /**
     * A child on the demo rolls.
     *
     * @param family the guardian household this child belongs to, or null for a child with no
     *     guardian recorded. Several children sharing a key share one guardian <em>record</em>,
     *     which is the property this whole roster exists to demonstrate.
     */
    record Child(String fullName, String gender, String family) {}

    /** A person responsible for one or more children. Created once and linked to each of them. */
    record Guardian(String fullName, String relation, String phone, String email, String occupation, boolean primary) {}

    /**
     * Households whose guardians are written out rather than generated, because each is making a
     * point: the first five share one guardian between siblings, and the last three link both a
     * father and a mother to the same child.
     */
    private static final Map<String, List<Guardian>> SHARED_FAMILIES = Map.of(
            "KULKARNI",
                    List.of(new Guardian(
                            "Sunil Kulkarni", FATHER, "98450 10001", "sunil.kulkarni@example.com", "Bank clerk", true)),
            "NAIR", List.of(new Guardian("Latha Nair", MOTHER, "+91 98450 10002", null, "School teacher", true)),
            "BOSE",
                    List.of(new Guardian(
                            "Amitava Bose", FATHER, "98450 10003", "amitava.bose@example.com", "Pharmacist", true)),
            "IYER",
                    List.of(
                            new Guardian("Ganesh Iyer", FATHER, "98450 10004", null, "Civil engineer", true),
                            new Guardian("Revathi Iyer", MOTHER, "98450 10005", null, "Homemaker", false)),
            "DESHMUKH", List.of(new Guardian("Sunita Deshmukh", MOTHER, "98450 10006", null, "Nurse", true)),
            "LAKSHMI", List.of(new Guardian("Ambika Devi", MOTHER, "98450 10007", null, "Tailor", true)),
            "SHEIKH",
                    List.of(
                            new Guardian("Imran Sheikh", FATHER, "+91 98450 10008", null, "Shopkeeper", true),
                            new Guardian("Farida Sheikh", MOTHER, "98450 10009", null, "Homemaker", false)),
            "PILLAI",
                    List.of(
                            new Guardian("Rajesh Pillai", FATHER, "98450 10010", null, "Auto driver", true),
                            new Guardian("Anitha Pillai", MOTHER, "98450 10011", null, "Nurse", false)));

    /**
     * Sixty children, in the order they are admitted.
     *
     * <p>The order is load-bearing twice over. Sections are assigned round-robin down this list, so
     * siblings written next to each other would land in the same room; they are spaced instead, and
     * a family's children come out in different classes, which is what makes a shared guardian look
     * like a shared guardian. And the position decides the age, so a child's date of birth matches
     * the rung they are on.
     */
    private static final List<Child> CHILDREN = List.of(
            new Child("Aarav Kulkarni", MALE, "KULKARNI"),
            solo("Meera Joshi", FEMALE),
            new Child("Diya Nair", FEMALE, "NAIR"),
            solo("Kabir Rao", MALE),
            new Child("Rudra Bose", MALE, "BOSE"),
            solo("Saanvi Menon", FEMALE),
            new Child("Kavya Iyer", FEMALE, "IYER"),
            solo("Arjun Sethi", MALE),
            new Child("Myra Deshmukh", FEMALE, "DESHMUKH"),
            solo("Riya Chatterjee", FEMALE),
            new Child("Zoya Sheikh", FEMALE, "SHEIKH"),
            solo("Dhruv Malhotra", MALE),
            new Child("Rohan Pillai", MALE, "PILLAI"),
            solo("Ira Banerjee", FEMALE),
            new Child("Anaya Kulkarni", FEMALE, "KULKARNI"),
            solo("Neel Verma", MALE),
            new Child("Ishaan Nair", MALE, "NAIR"),
            solo("Pari Sinha", FEMALE),
            new Child("Trisha Bose", FEMALE, "BOSE"),
            solo("Yuvan Ghosh", MALE),
            new Child("Adhrit Iyer", MALE, "IYER"),
            solo("Tara Bhat", FEMALE),
            new Child("Aadhya Deshmukh", FEMALE, "DESHMUKH"),
            solo("Reyansh Patil", MALE),
            new Child("Vihaan Kulkarni", MALE, "KULKARNI"),
            solo("Avni Shetty", FEMALE),
            solo("Krish Dubey", MALE),
            new Child("Aritra Bose", MALE, "BOSE"),
            // A single-name student, which is the case ADR-0020 §1 keeps one name field for. Her
            // guardian is written out above rather than generated, because there is no surname to
            // generate one from — which is the point.
            new Child("Lakshmi", FEMALE, "LAKSHMI"),
            solo("Nitya Rane", FEMALE),
            solo("Aisha Qureshi", FEMALE),
            solo("Veer Chauhan", MALE),
            solo("Sara Thomas", FEMALE),
            solo("Aryan Gowda", MALE),
            noGuardian("Ansh Mishra", MALE),
            solo("Kiara Fernandes", FEMALE),
            solo("Advik Saxena", MALE),
            solo("Amara Dsouza", FEMALE),
            noGuardian("Shaurya Bhatia", MALE),
            solo("Navya Agarwal", FEMALE),
            solo("Ayaan Khan", MALE),
            solo("Prisha Kaur", FEMALE),
            solo("Rehan Ali", MALE),
            solo("Anika Das", FEMALE),
            noGuardian("Vivaan Nanda", MALE),
            solo("Mahi Solanki", FEMALE),
            solo("Atharv Jadhav", MALE),
            solo("Siya Kamath", FEMALE),
            solo("Ritvik Hegde", MALE),
            solo("Aarohi Pandey", FEMALE),
            solo("Daksh Tiwari", MALE),
            noGuardian("Vanya Chopra", FEMALE),
            solo("Samar Ahluwalia", MALE),
            solo("Ridhi Barua", FEMALE),
            solo("Hriday Panicker", MALE),
            solo("Naina Bajwa", FEMALE),
            solo("Ekansh Mahajan", MALE),
            solo("Zara Merchant", FEMALE),
            solo("Tejas Wagh", MALE),
            // The third value of the enum is a value, and a demo that only ever shows two of them
            // is how a screen ships with a two-way toggle on it.
            solo("Kian Roy", OTHER));

    private static final List<String> FATHER_NAMES = List.of(
            "Suresh",
            "Mahesh",
            "Anil",
            "Vikram",
            "Prakash",
            "Rajiv",
            "Deepak",
            "Naveen",
            "Sanjay",
            "Manoj",
            "Girish",
            "Ashok",
            "Harish",
            "Jitendra",
            "Pramod");

    private static final List<String> MOTHER_NAMES = List.of(
            "Shalini", "Vandana", "Rekha", "Poonam", "Kavita", "Sushma", "Geeta", "Nisha", "Anjana", "Radha", "Bhavna",
            "Sneha", "Usha", "Madhavi", "Jyoti");

    private static final List<String> OCCUPATIONS = List.of(
            "Shopkeeper",
            "Bank clerk",
            "Farmer",
            "Auto driver",
            "Nurse",
            "School teacher",
            "Tailor",
            "Electrician",
            "Homemaker",
            "Civil engineer",
            "Pharmacist",
            "Accountant");

    /** Every household, keyed the way {@link Child#family()} names it. Written-out ones plus generated ones. */
    private static final Map<String, List<Guardian>> FAMILIES = buildFamilies();

    private DemoRoster() {}

    static List<Child> children() {
        return CHILDREN;
    }

    /** The guardians of one child, in the order they should be linked. Empty for a child who has none. */
    static List<Guardian> guardiansOf(Child child) {
        return child.family() == null ? List.of() : FAMILIES.get(child.family());
    }

    /** The distinct guardian households, so each person is created once and then linked more than once. */
    static Map<String, List<Guardian>> families() {
        return FAMILIES;
    }

    /** A child whose guardian is generated from their own surname: their own household, one parent. */
    private static Child solo(String fullName, String gender) {
        return new Child(fullName, gender, "SOLO:" + fullName);
    }

    /**
     * A child with nobody recorded yet — an admission taken over the phone, with the parent's
     * details still to come. Every screen that lists guardians has to survive this.
     */
    private static Child noGuardian(String fullName, String gender) {
        return new Child(fullName, gender, null);
    }

    private static Map<String, List<Guardian>> buildFamilies() {
        Map<String, List<Guardian>> families = new LinkedHashMap<>(SHARED_FAMILIES);
        List<String> generated = new ArrayList<>();
        for (Child child : CHILDREN) {
            if (child.family() != null && child.family().startsWith("SOLO:")) {
                generated.add(child.family());
            }
        }
        for (int i = 0; i < generated.size(); i++) {
            String key = generated.get(i);
            families.put(key, List.of(generateGuardian(key.substring("SOLO:".length()), i)));
        }
        return Map.copyOf(families);
    }

    /**
     * One parent for a child with no siblings here, built from their surname so the pair reads as a
     * family. Alternating father and mother, and a phone number in a shape the office would
     * actually have typed — some with a country code, some without, because
     * {@code guardian.phone_digits} exists precisely so the search survives that.
     */
    private static Guardian generateGuardian(String childFullName, int index) {
        int space = childFullName.lastIndexOf(' ');
        String surname = space < 0 ? childFullName : childFullName.substring(space + 1);
        boolean father = index % 2 == 0;
        String given =
                father ? FATHER_NAMES.get(index % FATHER_NAMES.size()) : MOTHER_NAMES.get(index % MOTHER_NAMES.size());
        String digits = "98450 " + (20000 + index);
        String phone = index % 5 == 0 ? "+91 " + digits : digits;
        String email = index % 3 == 0
                ? given.toLowerCase(java.util.Locale.ROOT) + "." + surname.toLowerCase(java.util.Locale.ROOT)
                        + "@example.com"
                : null;
        return new Guardian(
                given + " " + surname,
                father ? FATHER : MOTHER,
                phone,
                email,
                OCCUPATIONS.get(index % OCCUPATIONS.size()),
                true);
    }
}
