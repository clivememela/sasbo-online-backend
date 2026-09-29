package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberHistoryPageDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MemberHistoryRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A page of a member's history (month end 03.6): a page that does not exist is refused before anything is read, a
 * member not on record is {@code MEMBER_NOT_FOUND}, and a member with no history is an empty FIRST page -- not a
 * refusal.
 */
@DisplayName("MemberHistoryService")
class MemberHistoryServiceTest {

    private final MemberRepository members = mock(MemberRepository.class);
    private final MemberHistoryRepository history = mock(MemberHistoryRepository.class);
    private final MemberHistoryService service = new MemberHistoryService(members, history);

    private static MonthEndRefusalCode codeOf(Throwable refusal) {
        return ((MonthEndRefusal) refusal).code();
    }

    @Test
    @DisplayName("a negative page, a size of nought, a size over two hundred -- HISTORY_PAGE_INVALID, and nothing is read")
    void aPageThatCannotExist() {
        for (int[] ask : new int[][]{{-1, 50}, {0, 0}, {0, 201}}) {
            assertThatThrownBy(() -> service.page(7, ask[0], ask[1]))
                    .satisfies(refusal -> assertThat(codeOf(refusal)).isEqualTo(MonthEndRefusalCode.HISTORY_PAGE_INVALID));
        }
        verifyNoInteractions(members, history);
    }

    @Test
    @DisplayName("a member not on record is MEMBER_NOT_FOUND")
    void aMemberNotOnRecord() {
        when(members.existsById(7)).thenReturn(false);

        assertThatThrownBy(() -> service.page(7, null, null))
                .satisfies(refusal -> assertThat(codeOf(refusal)).isEqualTo(MonthEndRefusalCode.MEMBER_NOT_FOUND));
        verifyNoInteractions(history);
    }

    @Test
    @DisplayName("a member with no history is an empty first page; a page past the last is HISTORY_PAGE_INVALID")
    void noHistoryAndPastTheLast() {
        when(members.existsById(7)).thenReturn(true);
        when(history.page(7, 0, 50)).thenReturn(new MemberHistoryRepository.Slice(List.of(), 0));
        when(history.page(7, 3, 50)).thenReturn(new MemberHistoryRepository.Slice(List.of(), 0));

        MemberHistoryPageDto first = service.page(7, null, null);

        assertThat(first.content()).isEmpty();
        assertThat(first.totalPages()).isZero();
        assertThatThrownBy(() -> service.page(7, 3, 50))
                .satisfies(refusal -> assertThat(codeOf(refusal)).isEqualTo(MonthEndRefusalCode.HISTORY_PAGE_INVALID));
    }

    @Test
    @DisplayName("the page answers its entries, and how many pages there are")
    void aPage() {
        when(members.existsById(7)).thenReturn(true);
        MemberHistoryPageDto.Entry entry = new MemberHistoryPageDto.Entry("FIELD", 1, "2026-09-10T08:00:00Z", "system",
                "cellTel", "+27 60 555 0001", "+27 60 555 0002", null, null, "save-1", null);
        when(history.page(7, 1, 20)).thenReturn(new MemberHistoryRepository.Slice(List.of(entry), 41));

        MemberHistoryPageDto page = service.page(7, 1, 20);

        assertThat(page.content()).containsExactly(entry);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.totalElements()).isEqualTo(41);
    }
}
