package za.co.titandynamix.sasbo_online_backend.dto.signin;

import java.util.List;

/**
 * A page of users or of the record of who did what -- the shape the application's other pages answer with
 * ({@code AcbPage}, {@code WorkplacePageDto}): {@code {content, page, size, totalElements, totalPages}}.
 */
public record SignInPage<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
