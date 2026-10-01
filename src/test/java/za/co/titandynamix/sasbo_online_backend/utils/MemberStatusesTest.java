package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The six statuses and their kinds (month end 03.2, PA-4): a figure reads a status's KIND, never its spelling, so
 * nothing is renamed before the table with SASBO and a renaming then moves no figure.
 */
@DisplayName("MemberStatuses")
class MemberStatusesTest {

    @Test
    @DisplayName("PA-4: the six statuses as the screens offer them, each with its kind -- Pending on record, three leaving")
    void theSixAndTheirKinds() {
        assertThat(MemberStatuses.KINDS.keySet())
                .containsExactly("Active", "Pending", "Suspended", "Resigned", "Retired", "Terminated");
        assertThat(MemberStatuses.ofKind(MemberStatusKind.LEAVING)).containsExactly("Resigned", "Retired", "Terminated");
        assertThat(MemberStatuses.ofKind(MemberStatusKind.SUSPENDED)).containsExactly("Suspended");
        assertThat(MemberStatuses.ofKind(MemberStatusKind.NEITHER)).containsExactly("Active", "Pending");
    }

    @Test
    @DisplayName("rule X: a status nobody knows -- the recruiters screen's Inactive (A49) -- has no kind, and is never guessed one")
    void anUnknownStatusHasNoKind() {
        assertThat(MemberStatuses.kindOf("Inactive")).isNull();
        assertThat(MemberStatuses.kindOf("active")).as("the spelling is the record's, exactly").isNull();
        assertThat(MemberStatuses.kindOf(null)).isNull();
    }

    @Test
    @DisplayName("ACB's two statuses keep their spellings and are of the kinds ACB's rules assume")
    void acbsTwoStatuses() {
        assertThat(MemberStatuses.kindOf(MemberStatuses.SUSPENDED)).isEqualTo(MemberStatusKind.SUSPENDED);
        assertThat(MemberStatuses.kindOf(MemberStatuses.TERMINATED)).isEqualTo(MemberStatusKind.LEAVING);
    }
}
