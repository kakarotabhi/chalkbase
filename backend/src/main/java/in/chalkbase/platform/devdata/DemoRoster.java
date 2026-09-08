package in.chalkbase.platform.devdata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The invented school that {@link DemoSchoolSeeder} builds: its ladder, its children and the people
 * responsible for them.
 *
 * <p><strong>Every person named in this file is invented</strong> (AGENTS rule 9, ADR-0014). None of
 * it came from a real school and none of it ever may — demo data that started life as a real class
 * list is a data breach with a friendly name on it. The phone numbers are in the reserved-looking
 * ranges nobody answers and the email addresses are all under {@code example.com}, which RFC 2606
 * reserves for exactly this. Names below are built by combining common given names with common
 * surnames, both drawn from open pools rather than copied off any one list — the same technique the
 * smaller version of this file already used for every guardian it invented for a solo child.
 *
 * <p><strong>Six hundred children, not a few dozen.</strong> A list screen, a page size and a
 * guardian search exercised against sixty rows proves nothing about what a real school's six hundred
 * do to them — see {@code docs/status.md}. Hand-naming six hundred children the way the original
 * sixty were named would be six hundred lines making the same point over and over, so this file
 * generates households — a surname, a phone number, one or two parents — and lets
 * {@link DemoSchoolSeeder} turn them into rows of a CSV file for the bulk import endpoint
 * (ADR-0021) rather than six hundred individual admissions.
 *
 * <p>The shape here is still chosen to make ADR-0020 §5 visible on screen rather than to look tidy:
 * a large share of households put two, three or four children behind a <em>single</em> guardian
 * record, so correcting one phone number in the demo visibly corrects it for every sibling — see
 * {@link #build()} for the exact composition and why it adds up to six hundred. A run of children
 * with no guardian recorded stays in the mix, and so does a run of households with both parents
 * linked to the same child, because an empty state and a two-guardian state are both states a screen
 * has to survive and neither is exercised by a roster that never contains one.
 *
 * <p>Values are plain strings rather than the owning modules' enums on purpose. {@code Gender},
 * {@code StudentStatus}, {@code GuardianRelation} and {@code Board} live in their modules'
 * {@code domain} packages, which are not exposed across a module boundary — see the class javadoc on
 * {@link DemoSchoolSeeder} for why this seeder speaks JSON (now mostly one CSV file) instead of
 * importing them.
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

    /** How many children this roster admits in total — see {@link #build()} for the composition. */
    static final int TOTAL_CHILDREN = 600;

    private static final String FATHER = "FATHER";
    private static final String MOTHER = "MOTHER";

    private static final String MALE = "MALE";
    private static final String FEMALE = "FEMALE";
    private static final String OTHER = "OTHER";

    // ── How many households of each shape (must sum, in children, to TOTAL_CHILDREN) ────────────
    //
    // Chosen to make the guardian directory's dedup the dominant pattern in this roster rather than
    // a handful of examples in it: a hundred and fifty households below share a guardian between two,
    // three or four siblings, against a hundred and eighty that stand alone — so the common case on
    // this demo school's guardian screen is a person responsible for more than one child, which is
    // the case ADR-0020 §5 exists for. A one-in-six run of households leaves no guardian at all
    // (an admission taken over the phone, the details still to come) and a one-in-twenty-five run
    // gives both parents to the same child.
    private static final int NO_GUARDIAN_HOUSEHOLDS = 36;
    private static final int DUAL_GUARDIAN_HOUSEHOLDS = 24;
    private static final int QUAD_HOUSEHOLDS = 10;
    private static final int TRIO_HOUSEHOLDS = 40;
    private static final int PAIR_HOUSEHOLDS = 100;
    // Everything else is a solo household: one child, one guardian. Computed in build() rather than
    // written here twice, so the two can never drift apart.

    private static final List<String> MALE_FIRST_NAMES = List.of(
            "Aarav",
            "Kabir",
            "Rudra",
            "Arjun",
            "Dhruv",
            "Rohan",
            "Neel",
            "Ishaan",
            "Yuvan",
            "Reyansh",
            "Vihaan",
            "Krish",
            "Veer",
            "Aryan",
            "Ansh",
            "Advik",
            "Shaurya",
            "Ayaan",
            "Rehan",
            "Vivaan",
            "Atharv",
            "Ritvik",
            "Daksh",
            "Samar",
            "Hriday",
            "Ekansh",
            "Tejas",
            "Kian",
            "Aditya",
            "Karan",
            "Rahul",
            "Vikrant",
            "Nikhil",
            "Siddharth",
            "Varun",
            "Aakash",
            "Devansh",
            "Harsh",
            "Kunal",
            "Mohit",
            "Rajat",
            "Sahil",
            "Tarun",
            "Uday",
            "Vivek",
            "Yash",
            "Zubin",
            "Om",
            "Parth",
            "Raghav");

    private static final List<String> FEMALE_FIRST_NAMES = List.of(
            "Meera", "Diya", "Saanvi", "Kavya", "Myra", "Riya", "Zoya", "Ira", "Anaya", "Pari", "Trisha", "Tara",
            "Aadhya", "Avni", "Nitya", "Aisha", "Sara", "Kiara", "Amara", "Navya", "Prisha", "Anika", "Mahi", "Siya",
            "Aarohi", "Vanya", "Ridhi", "Naina", "Zara", "Ananya", "Bhavya", "Charvi", "Disha", "Esha", "Gauri", "Hina",
            "Ishita", "Jia", "Kritika", "Lavanya", "Manya", "Nandini", "Ojasvi", "Pihu", "Radhika", "Sanya", "Tanvi",
            "Urvi", "Vidya", "Yamini");

    private static final List<String> SURNAMES = List.of(
            "Kulkarni",
            "Joshi",
            "Nair",
            "Rao",
            "Bose",
            "Menon",
            "Iyer",
            "Sethi",
            "Deshmukh",
            "Chatterjee",
            "Sheikh",
            "Malhotra",
            "Pillai",
            "Banerjee",
            "Verma",
            "Sinha",
            "Ghosh",
            "Bhat",
            "Patil",
            "Shetty",
            "Dubey",
            "Qureshi",
            "Chauhan",
            "Thomas",
            "Gowda",
            "Mishra",
            "Fernandes",
            "Saxena",
            "Dsouza",
            "Bhatia",
            "Agarwal",
            "Khan",
            "Kaur",
            "Das",
            "Nanda",
            "Solanki",
            "Jadhav",
            "Kamath",
            "Hegde",
            "Pandey",
            "Tiwari",
            "Chopra",
            "Ahluwalia",
            "Barua",
            "Panicker",
            "Bajwa",
            "Mahajan",
            "Merchant",
            "Wagh",
            "Reddy");

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

    /** What {@link #build()} produced: the roster and the households behind it. */
    private record Built(List<Child> children, Map<String, List<Guardian>> families) {}

    private static final Built BUILT = build();
    private static final List<Child> CHILDREN = BUILT.children();
    private static final Map<String, List<Guardian>> FAMILIES = BUILT.families();

    private DemoRoster() {}

    static List<Child> children() {
        return CHILDREN;
    }

    /** The guardians of one child, in the order they should be linked. Empty for a child who has none. */
    static List<Guardian> guardiansOf(Child child) {
        return child.family() == null ? List.of() : FAMILIES.getOrDefault(child.family(), List.of());
    }

    /** Every household, keyed the way {@link Child#family()} names it. */
    static Map<String, List<Guardian>> families() {
        return FAMILIES;
    }

    // ── Building the roster ──────────────────────────────────────────────────────────────────

    /**
     * Six hundred children in six household shapes, and the guardians those households share.
     *
     * <p>Households are emitted in blocks — every no-guardian household, then every two-parent one,
     * then descending sibling-group sizes, then solo households filling out the rest — rather than
     * interleaved. That is deliberate and it costs nothing: {@link DemoSchoolSeeder} places children
     * into sections by their position in this list modulo the section count, and with two sections
     * per class that assignment cycles through the whole ladder well within any one block, so a
     * block of no-guardian children still lands across most of the school rather than in one room of
     * it. Within a household, consecutive children fall in different sections for the same reason —
     * exactly the "siblings in different classes" property the original, hand-written roster called
     * out, produced here as a side effect of the arithmetic rather than by hand-spacing them.
     */
    private static Built build() {
        List<Child> children = new ArrayList<>();
        Map<String, List<Guardian>> families = new LinkedHashMap<>();

        int next = 0;
        next = addHouseholds(children, families, NO_GUARDIAN_HOUSEHOLDS, 1, 0, next);
        next = addHouseholds(children, families, DUAL_GUARDIAN_HOUSEHOLDS, 1, 2, next);
        next = addHouseholds(children, families, QUAD_HOUSEHOLDS, 4, 1, next);
        next = addHouseholds(children, families, TRIO_HOUSEHOLDS, 3, 1, next);
        next = addHouseholds(children, families, PAIR_HOUSEHOLDS, 2, 1, next);
        int soloHouseholds = TOTAL_CHILDREN
                - NO_GUARDIAN_HOUSEHOLDS
                - DUAL_GUARDIAN_HOUSEHOLDS
                - QUAD_HOUSEHOLDS * 4
                - TRIO_HOUSEHOLDS * 3
                - PAIR_HOUSEHOLDS * 2;
        int soloStart = children.size();
        addHouseholds(children, families, soloHouseholds, 1, 1, next);

        // Two edge cases the smaller roster hand-wrote once each, kept here for the same reason:
        // a single-name student (ADR-0020 §1 keeps one name field for exactly this — there is no
        // surname to append one to) and the OTHER value of a toggle that looks two-way until a demo
        // shows it is not. Both land in the solo block, so neither disturbs a shared guardian.
        Child singleName = children.get(soloStart);
        children.set(soloStart, new Child("Lakshmi", singleName.gender(), singleName.family()));
        Child otherGender = children.get(soloStart + 1);
        children.set(soloStart + 1, new Child(otherGender.fullName(), OTHER, otherGender.family()));

        return new Built(List.copyOf(children), Map.copyOf(families));
    }

    /**
     * Emits {@code count} households of {@code childrenPerHousehold} children apiece, sharing
     * {@code guardiansPerHousehold} guardians between them.
     *
     * @return the next unused household index, so the caller can hand it to the next block without
     *     two blocks ever generating the same surname, phone number or first-name rotation
     */
    private static int addHouseholds(
            List<Child> children,
            Map<String, List<Guardian>> families,
            int count,
            int childrenPerHousehold,
            int guardiansPerHousehold,
            int startIndex) {
        for (int h = 0; h < count; h++) {
            int index = startIndex + h;
            String surname = SURNAMES.get(index % SURNAMES.size());
            String key = guardiansPerHousehold == 0 ? null : "HH-" + index;
            if (key != null) {
                families.put(key, generateGuardians(surname, index, guardiansPerHousehold));
            }
            for (int c = 0; c < childrenPerHousehold; c++) {
                int childIndex = children.size();
                boolean male = childIndex % 2 == 0;
                String given = male
                        ? MALE_FIRST_NAMES.get(childIndex % MALE_FIRST_NAMES.size())
                        : FEMALE_FIRST_NAMES.get(childIndex % FEMALE_FIRST_NAMES.size());
                children.add(new Child(given + " " + surname, male ? MALE : FEMALE, key));
            }
        }
        return startIndex + count;
    }

    /**
     * One or two parents for a household, built from its surname so the household reads as a
     * family — the same technique the smaller roster's {@code generateGuardian} used for every solo
     * child it invented.
     *
     * @param guardianCount 1 for the common case (one parent on file, alternating which), or 2 for a
     *     household with both parents linked — two distinct people, two distinct phone numbers, the
     *     father marked primary
     */
    private static List<Guardian> generateGuardians(String surname, int index, int guardianCount) {
        String fatherGiven = FATHER_NAMES.get(index % FATHER_NAMES.size());
        String motherGiven = MOTHER_NAMES.get(index % MOTHER_NAMES.size());
        String occupation = OCCUPATIONS.get(index % OCCUPATIONS.size());

        if (guardianCount == 2) {
            return List.of(
                    new Guardian(
                            fatherGiven + " " + surname,
                            FATHER,
                            phoneFor(index),
                            emailFor(index, fatherGiven, surname),
                            occupation,
                            true),
                    new Guardian(
                            motherGiven + " " + surname,
                            MOTHER,
                            // A distinct number in a range no other household's index reaches, so a
                            // household's two parents are never mistaken for one shared phone.
                            phoneFor(index + 1_000),
                            emailFor(index + 1_000, motherGiven, surname),
                            "Homemaker",
                            false));
        }

        boolean father = index % 2 == 0;
        String given = father ? fatherGiven : motherGiven;
        return List.of(new Guardian(
                given + " " + surname,
                father ? FATHER : MOTHER,
                phoneFor(index),
                emailFor(index, given, surname),
                occupation,
                true));
    }

    /**
     * A phone number in a shape the office would actually have typed — some with a country code,
     * some without, because {@code guardian.phone_digits} exists precisely so the search survives
     * that — in the reserved-looking range nobody answers.
     */
    private static String phoneFor(int index) {
        String digits = "98450 " + (20000 + index);
        return index % 5 == 0 ? "+91 " + digits : digits;
    }

    /** Roughly one household in three gives an email; the rest leave it blank, as a paper form would. */
    private static String emailFor(int index, String given, String surname) {
        return index % 3 == 0
                ? given.toLowerCase(Locale.ROOT) + "." + surname.toLowerCase(Locale.ROOT) + "@example.com"
                : null;
    }
}
