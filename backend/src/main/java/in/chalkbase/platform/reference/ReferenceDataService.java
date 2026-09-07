package in.chalkbase.platform.reference;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * States, one of the two hardcoded lists a school-facing form used to carry (ADR-0029). The other,
 * boards, is deliberately not here — see below.
 *
 * <p><strong>Cached in memory after the first read.</strong> The list is identical for every user of
 * every school, {@link ReferenceDataSeeder} only ever changes it as part of a deploy, and a deploy
 * restarts this process — so there is no window in which the cache could go stale without the very
 * event that would also clear it. A future admin screen that let someone edit a row through the API
 * would need to evict this cache at write time; nothing today does.
 *
 * <p><strong>Why boards are not here too.</strong> {@code Board} is the {@code school} module's own
 * domain enum, and {@code platform} — the shared kernel every module depends on — must not import a
 * feature module's domain type; that dependency would run backwards ({@code AGENTS.md} rule 2,
 * checked by {@code ModularityTests}). {@code SchoolController#boards} exposes the board list from
 * inside {@code school} instead, reusing {@link ReferenceItemResponse} as the shape but none of this
 * class.
 */
@Service
@Transactional(readOnly = true)
public class ReferenceDataService {

    private final StateRepository states;

    /**
     * Populated on first read, then never recomputed for the life of the process. See the class
     * comment for why that is safe rather than a bug waiting to happen.
     */
    private volatile List<ReferenceItemResponse> cachedStates;

    public ReferenceDataService(StateRepository states) {
        this.states = states;
    }

    public List<ReferenceItemResponse> states() {
        List<ReferenceItemResponse> cached = cachedStates;
        if (cached == null) {
            cached = states.findAllByOrderByNameAsc().stream()
                    .map(state -> new ReferenceItemResponse(state.getName(), state.getName()))
                    .toList();
            cachedStates = cached;
        }
        return cached;
    }
}
