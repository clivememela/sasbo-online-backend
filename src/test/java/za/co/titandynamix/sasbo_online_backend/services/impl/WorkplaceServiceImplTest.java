package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplacePageDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.SasboReferenceRepository;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The workplaces, a page at a time, answered AS THE SWITCH ALLOWS (month end 01.2, MD62), with a page that does not
 * exist refused by code and a text over its ceiling refused naming its field (MR-SEC-22, ME-14).
 */
@DisplayName("WorkplaceServiceImpl")
class WorkplaceServiceImplTest {

    private final WorkplaceRepository workplaces = mock(WorkplaceRepository.class);
    private final SasboReferenceRepository lists = mock(SasboReferenceRepository.class);

    private WorkplaceServiceImpl service(String switchWord) {
        return new WorkplaceServiceImpl(workplaces, lists, new WorkplaceSwitch(switchWord), new SasboEnvironment(null));
    }

    private static WorkplaceDto invented(long id) {
        return new WorkplaceDto(id, 900_000 + (int) id, "INVENTED BRANCH " + id, "INVENTED BANK", 1L, "INVENTED BANK",
                "INVENTED GROUP", 901, "NAME", "A", "NORTH", "Q01", "INVENTED COMMITTEE", 12, null);
    }

    @Test
    @DisplayName("🔒 MD62: switched off, nothing is read -- an empty page that says the workplaces are not loaded here")
    void switchedOffNothingIsRead() {
        // A row on record, so that a switch not asked answers it -- and this reddens on an assertion, not a null (DL-68a).
        when(workplaces.page(any(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(new WorkplaceRepository.Slice(List.of(invented(1)), 1));

        WorkplacePageDto page = service(null).page(0, 50, null, null, null, null, null);

        assertThat(page.content()).isEmpty();
        assertThat(page.reason()).isEqualTo("NOT_LOADED");
        verifyNoInteractions(workplaces);
    }

    @Test
    @DisplayName("🔒 MR-SEC-19: at names, the rows are read without their staff complement")
    void atNamesNoComplement() {
        when(workplaces.page(any(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(new WorkplaceRepository.Slice(List.of(invented(1)), 1));

        service("names").page(0, 50, null, null, null, null, null);

        verify(workplaces).page(any(), eq(0), eq(50), eq(false));
    }

    @Test
    @DisplayName("at all, the rows are read with their staff complement")
    void atAllTheComplement() {
        when(workplaces.page(any(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(new WorkplaceRepository.Slice(List.of(invented(1)), 1));

        service("all").page(null, null, null, null, null, null, null);

        verify(workplaces).page(any(), eq(0), eq(WorkplaceServiceImpl.DEFAULT_PAGE_SIZE), eq(true));
    }

    @Test
    @DisplayName("a page's total and the number of pages come from the one statement's count")
    void pagesFromTheCount() {
        when(workplaces.page(any(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(new WorkplaceRepository.Slice(List.of(invented(1), invented(2)), 5));

        WorkplacePageDto page = service("all").page(1, 2, null, null, null, null, null);

        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.reason()).isNull();
    }

    @Test
    @DisplayName("WORKPLACE_PAGE_INVALID: a negative page, a size outside one to two hundred, a page past the last")
    void aPageThatDoesNotExist() {
        WorkplaceServiceImpl service = service("all");
        for (int[] pageAndSize : new int[][] {{-1, 50}, {0, 0}, {0, 201}}) {
            assertThatThrownBy(() -> service.page(pageAndSize[0], pageAndSize[1], null, null, null, null, null))
                    .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.WORKPLACE_PAGE_INVALID);
                        assertThat(refusal.details()).containsEntry("maxSize", "200");
                    });
        }
        when(workplaces.page(any(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(new WorkplaceRepository.Slice(List.of(), 0));
        assertThatThrownBy(() -> service.page(7, 50, null, null, null, null, null))
                .as("a page past the last is refused, never answered as an empty page that looks like the end")
                .isInstanceOfSatisfying(MonthEndRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.WORKPLACE_PAGE_INVALID));
    }

    @Test
    @DisplayName("🔒 MR-SEC-22: a search over a hundred characters is REQUEST_MALFORMED, naming the field and giving none of it back")
    void aSearchOverItsCeiling() {
        String planted = "SEARCH-TERM-PLANTED-" + "x".repeat(90);

        assertThatThrownBy(() -> service("all").page(0, 50, null, null, null, null, planted))
                .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.REQUEST_MALFORMED);
                    assertThat(refusal.details()).containsExactlyEntriesOf(Map.of("field", "search"));
                    assertThat(refusal.getMessage()).doesNotContain("PLANTED");
                });
        verify(workplaces, never()).page(any(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("a search below two characters asks nothing of it; a filter's letter or code is trimmed and upper-cased")
    void searchesAndFiltersAreNormalised() {
        when(workplaces.page(any(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(new WorkplaceRepository.Slice(List.of(invented(1)), 1));
        ArgumentCaptor<WorkplaceRepository.Filter> filter = ArgumentCaptor.forClass(WorkplaceRepository.Filter.class);

        service("all").page(0, 50, 3L, 901, " a ", "q01", " b ");

        verify(workplaces).page(filter.capture(), eq(0), eq(50), eq(true));
        assertThat(filter.getValue()).isEqualTo(new WorkplaceRepository.Filter(3L, 901, "A", "Q01", null));
        assertThatThrownBy(() -> service("all").page(0, 50, null, null, "ABCDE", null, null))
                .isInstanceOfSatisfying(MonthEndRefusal.class,
                        refusal -> assertThat(refusal.details()).containsEntry("field", "area"));
    }

    @Test
    @DisplayName("ME-19: an empty first page says why, where no workplace is on record -- and says nothing where a filter found none")
    void anEmptyPageSaysWhy() {
        when(workplaces.page(any(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(new WorkplaceRepository.Slice(List.of(), 0));
        when(workplaces.count()).thenReturn(0L);
        when(lists.recorded()).thenReturn(Map.of("workplaces", new SasboReferenceRepository.ListRecord(
                LocalDate.of(2026, 8, 31), null, "FAILED", "REFUSED", null, Instant.EPOCH)));

        assertThat(service("all").page(0, 50, null, null, null, null, null).reason()).isEqualTo("FAILED");

        when(workplaces.count()).thenReturn(5507L);
        assertThat(service("all").page(0, 50, null, null, "ZZ", null, null).reason())
                .as("a filter that found nothing is not a list that is empty")
                .isNull();
    }
}
