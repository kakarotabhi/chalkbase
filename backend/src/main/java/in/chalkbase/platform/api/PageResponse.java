package in.chalkbase.platform.api;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * One page of a list endpoint, carried as the {@code data} of an {@link ApiResponse}.
 *
 * <p>Offset pagination — {@code ?page=0&size=25&sort=occurredAt,desc} — settled in the Phase 0
 * decisions. Cursor pagination was rejected for the general case: every list in a school ERP is a
 * bounded, admin-facing table where the user wants "page 7 of 12" and a total count, and a cursor
 * cannot answer "how many are there", which is the question actually being asked.
 *
 * <p>Deliberately <strong>not</strong> Spring Data's {@code Page}. Serialising that type ships
 * {@code pageable}, {@code sort} and a dozen other fields whose shape is a Spring Data
 * implementation detail — it changes between versions, and every client would be coupled to it.
 * Four numbers and a list is the contract; {@link #of} is the only place the two types meet.
 *
 * @param content the rows on this page, in the requested order
 * @param page zero-based page number, as requested
 * @param size the requested page size, not the number of rows returned — the last page is shorter
 * @param totalElements how many rows match the filter across every page
 * @param totalPages how many pages that makes at this size
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public PageResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }

    /**
     * Wraps a repository result, taking the content separately because the query returns entities
     * and the API returns records — an entity never crosses the HTTP boundary.
     */
    public static <T> PageResponse<T> of(Page<?> page, List<T> content) {
        return new PageResponse<>(
                content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
