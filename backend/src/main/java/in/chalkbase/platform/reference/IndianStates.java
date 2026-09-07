package in.chalkbase.platform.reference;

import java.util.List;

/**
 * The shipped list of Indian states and union territories.
 *
 * <p>This is the source of truth (ADR-0029), the same relationship {@code PermissionCatalog} has to
 * the {@code permission} table: {@link ReferenceDataSeeder} copies this list into {@code
 * public.state} at every startup, upserting on {@link IndianState#code()} so a corrected {@link
 * IndianState#name()} reaches every school on the next deploy without a migration. A state or union
 * territory renamed, created or reorganised is a change here, reviewed like any other code change —
 * not a migration that edits a row a previous migration inserted, which this project's migrations
 * may never do once merged.
 *
 * <p>Alphabetical by name, which is also the order {@link ReferenceDataService} reads them back in:
 * a picker with thirty-six entries is browsed, not searched, and a reader finds a name faster in a
 * list sorted the way a phone book is.
 */
public final class IndianStates {

    public static final List<IndianState> ALL = List.of(
            new IndianState("AN", "Andaman and Nicobar Islands"),
            new IndianState("AP", "Andhra Pradesh"),
            new IndianState("AR", "Arunachal Pradesh"),
            new IndianState("AS", "Assam"),
            new IndianState("BR", "Bihar"),
            new IndianState("CH", "Chandigarh"),
            new IndianState("CT", "Chhattisgarh"),
            new IndianState("DNHDD", "Dadra and Nagar Haveli and Daman and Diu"),
            new IndianState("DL", "Delhi"),
            new IndianState("GA", "Goa"),
            new IndianState("GJ", "Gujarat"),
            new IndianState("HR", "Haryana"),
            new IndianState("HP", "Himachal Pradesh"),
            new IndianState("JK", "Jammu and Kashmir"),
            new IndianState("JH", "Jharkhand"),
            new IndianState("KA", "Karnataka"),
            new IndianState("KL", "Kerala"),
            new IndianState("LA", "Ladakh"),
            new IndianState("LD", "Lakshadweep"),
            new IndianState("MP", "Madhya Pradesh"),
            new IndianState("MH", "Maharashtra"),
            new IndianState("MN", "Manipur"),
            new IndianState("ML", "Meghalaya"),
            new IndianState("MZ", "Mizoram"),
            new IndianState("NL", "Nagaland"),
            new IndianState("OD", "Odisha"),
            new IndianState("PY", "Puducherry"),
            new IndianState("PB", "Punjab"),
            new IndianState("RJ", "Rajasthan"),
            new IndianState("SK", "Sikkim"),
            new IndianState("TN", "Tamil Nadu"),
            new IndianState("TG", "Telangana"),
            new IndianState("TR", "Tripura"),
            new IndianState("UP", "Uttar Pradesh"),
            new IndianState("UT", "Uttarakhand"),
            new IndianState("WB", "West Bengal"));

    private IndianStates() {}
}
