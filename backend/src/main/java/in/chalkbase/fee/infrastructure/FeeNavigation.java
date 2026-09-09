package in.chalkbase.fee.infrastructure;

import in.chalkbase.platform.navigation.NavigationItem;
import in.chalkbase.platform.navigation.NavigationProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Where this module's screens appear in the menu (ADR-0008).
 *
 * <p>A container with two children today, the same shape {@code AttendanceNavigation} uses: the
 * catalogue drops a container whose children have all been filtered away, so a role holding
 * neither permission below never sees a Fees entry that opens onto nothing. The frontend's own
 * label catalogue already reserved {@code nav.fees.collect}, {@code nav.fees.receipts} and
 * {@code nav.fees.defaulters} for the collection lane that has not shipped yet; this module claims
 * only the two ids its own screens answer.
 *
 * <p>Ordered at 50, just after attendance (40) and ahead of the modules that have not shipped yet.
 */
@Configuration
public class FeeNavigation {

    public static final String FEES = "fees";
    public static final String FEES_HEADS = "fees.heads";
    public static final String FEES_STRUCTURE = "fees.structure";

    @Bean
    NavigationProvider feeNavigationProvider() {
        return () -> List.of(new NavigationItem(
                FEES,
                "nav.fees",
                "fees",
                50,
                null,
                List.of(
                        new NavigationItem(FEES_HEADS, "nav.fees.heads", "fees", 10, FeePermissions.HEAD_READ),
                        new NavigationItem(
                                FEES_STRUCTURE, "nav.fees.structure", "fees", 20, FeePermissions.STRUCTURE_READ))));
    }
}
