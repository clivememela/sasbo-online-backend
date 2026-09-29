package za.co.titandynamix.sasbo_online_backend.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A refusal's details are written the same way on every start: in the order given where the map has one, by name
 * where it has none. {@code Map.of} of two or more is salted afresh on every start, and a golden answer built from one
 * read {@code page} before {@code size} on a runner and after it on a laptop.
 */
@DisplayName("MonthEndRefusal")
class MonthEndRefusalTest {

    @Test
    @DisplayName("details given in an order are kept in that order")
    void inTheOrderGiven() {
        MonthEndRefusal refusal = new MonthEndRefusal(MonthEndRefusalCode.WORKPLACE_PAGE_INVALID, "No such page.",
                MonthEndRefusal.details("size", "2", "page", "9", "maxSize", "200"));

        assertThat(refusal.details().keySet()).containsExactly("size", "page", "maxSize");
    }

    @Test
    @DisplayName("details given without an order are written by name -- the same on every start")
    void withoutAnOrderByName() {
        Map<String, String> unordered = new HashMap<>(Map.of("page", "9", "size", "2"));

        MonthEndRefusal refusal = new MonthEndRefusal(MonthEndRefusalCode.WORKPLACE_PAGE_INVALID, "No such page.",
                unordered);

        assertThat(unordered.keySet()).as("the fixture walks out of name order, or this proves nothing")
                .containsExactly("size", "page");
        assertThat(refusal.details().keySet()).containsExactly("page", "size");
    }
}
