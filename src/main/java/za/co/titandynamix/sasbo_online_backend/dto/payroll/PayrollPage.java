package za.co.titandynamix.sasbo_online_backend.dto.payroll;

import java.util.List;

/**
 * One page of a payroll list, with what the screen needs to page it -- the shape of ACB's {@code AcbPage}, never
 * Spring's {@code PageImpl}, whose JSON is an implementation detail.
 */
public record PayrollPage<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PayrollPage<T> of(List<T> content, int page, int size, long total) {
        int pages = size == 0 ? 0 : (int) ((total + size - 1) / size);
        return new PayrollPage<>(content, page, size, total, pages);
    }
}
