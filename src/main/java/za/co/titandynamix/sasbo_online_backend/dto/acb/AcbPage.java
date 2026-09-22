package za.co.titandynamix.sasbo_online_backend.dto.acb;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * One page of an ACB list, with what the screen needs to page it.
 *
 * <p>Its own shape rather than Spring's {@code PageImpl}, whose JSON is an implementation detail
 * Spring Data itself warns against serialising, and would change under the browser with a library
 * upgrade.
 */
public record AcbPage<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <E, T> AcbPage<T> of(Page<E> page, Function<E, T> mapper) {
        return new AcbPage<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
