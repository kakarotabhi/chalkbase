package in.chalkbase.platform.reference;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code public.state}, read-only from this application's point of view (see {@link State}).
 *
 * <p>Not tenant-scoped and carries no {@code search_path} concern either way: {@link State} is
 * qualified to {@code public} explicitly, so this repository answers the same rows no matter which
 * school's schema the connection is bound to.
 */
public interface StateRepository extends JpaRepository<State, String> {

    List<State> findAllByOrderByNameAsc();
}
