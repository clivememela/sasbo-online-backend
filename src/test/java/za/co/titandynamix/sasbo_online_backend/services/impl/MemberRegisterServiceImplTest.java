package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterOverviewDto;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterQuery;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRegisterRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRegisterRepository.Filter;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRegisterRepository.Order;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.services.MemberRegisterService;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

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
 * The member register a page at a time (register row A10): what a page may ask, refused with a code before anything is
 * read; the page arithmetic, including a page past the last answered as the last; and the figure strip, counted for the
 * caller's own register as of SASBO's day. The repository is mocked -- its statements are proved on H2
 * ({@code MemberRegisterRepositoryTest}) and on PostgreSQL ({@code MemberRegisterIT}, {@code MemberRegisterPlanIT}).
 */
@DisplayName("The member register, a page at a time (A10)")
class MemberRegisterServiceImplTest {

    /** 08:00 in Johannesburg on 6 October 2026. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T06:00:00Z"), AcbTime.ZONE);

    private MemberRegisterRepository register;
    private MemberRegisterService service;

    @BeforeEach
    void aServiceOverAMockedRegister() {
        register = mock(MemberRegisterRepository.class);
        service = new MemberRegisterServiceImpl(register, TransactionOperations.withoutTransaction(), CLOCK);
    }

    private static MemberRegisterQuery query(Integer page, Integer size, String sort) {
        return new MemberRegisterQuery(null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, page, size, sort);
    }

    private static List<MemberRegisterPageDto.Row> rows(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(n -> new MemberRegisterPageDto.Row(n, "M-9" + n, null, "Mx",
                "I", "Invented" + n, "Synthetic", "INVENTED BANK", "INVENTED BRANCH", "Active")).toList();
    }

    private MonthEndRefusal refusalOf(MemberRegisterQuery asked) {
        try {
            service.page(asked, CallerScope.all());
        } catch (MonthEndRefusal refusal) {
            return refusal;
        }
        throw new AssertionError("expected a refusal");
    }

    // ── what a page may ask ───────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("nothing asked is the first page of fifty, in surname order, for the whole register")
    void nothingAskedIsTheFirstPage() {
        when(register.count(any(), any())).thenReturn(3L);
        when(register.page(any(), any(), any(), anyBoolean(), anyInt(), anyInt())).thenReturn(rows(3));

        MemberRegisterPageDto page = service.page(null, CallerScope.all());

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(MemberRegisterService.DEFAULT_PAGE_SIZE).isEqualTo(50);
        assertThat(page.sort()).isEqualTo(MemberRegisterService.DEFAULT_SORT).isEqualTo("surname,asc");
        ArgumentCaptor<Filter> filter = ArgumentCaptor.forClass(Filter.class);
        verify(register).page(filter.capture(), eq(CallerScope.all()), eq(Order.SURNAME), eq(false), eq(0), eq(50));
        assertThat(Arrays.stream(Filter.class.getRecordComponents()).map(c -> value(c, filter.getValue())))
                .as("every criterion is not asked").allMatch(v -> v == null);
    }

    private static Object value(RecordComponent component, Object record) {
        try {
            return component.getAccessor().invoke(record);
        } catch (ReflectiveOperationException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

    @ParameterizedTest(name = "page {0}, size {1}")
    @CsvSource({"-1, 50", "0, 0", "0, -5", "0, 101", "3, 500"})
    @DisplayName("🔒 a page below nought or a size outside 1 to 100 is REGISTER_PAGE_INVALID -- and nothing is read")
    void aPageThatCannotBeAskedIsRefused(int page, int size) {
        MonthEndRefusal refusal = refusalOf(query(page, size, null));

        assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.REGISTER_PAGE_INVALID);
        assertThat(refusal.code().status().value()).isEqualTo(400);
        assertThat(refusal.details()).containsEntry("page", String.valueOf(page))
                .containsEntry("size", String.valueOf(size)).containsEntry("maxSize", "100");
        verifyNoInteractions(register);
    }

    @ParameterizedTest(name = "size {0}")
    @ValueSource(ints = {1, 25, 50, 100})
    @DisplayName("must-pass: 1 and 100 are sizes a page may be -- the ceiling is inclusive")
    void theCeilingIsInclusive(int size) {
        when(register.count(any(), any())).thenReturn(0L);

        assertThat(service.page(query(0, size, null), CallerScope.all()).size()).isEqualTo(size);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"identityNumber", "identityNumber,asc", "surname,sideways", "surname,asc,desc", "Surname",
            ",asc", "employeeNumber"})
    @DisplayName("🔒 an order the register is not read in is REGISTER_SORT_UNKNOWN, naming the orders it takes")
    void anUnknownOrderIsRefused(String sort) {
        MonthEndRefusal refusal = refusalOf(query(0, 50, sort));

        assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.REGISTER_SORT_UNKNOWN);
        assertThat(refusal.code().status().value()).isEqualTo(400);
        assertThat(refusal.details()).containsEntry("sorts", "surname,membershipNumber,bank,branch,status")
                .containsEntry("directions", "asc,desc");
        verifyNoInteractions(register);
    }

    @ParameterizedTest(name = "\"{0}\" -> {1} {2}")
    @CsvSource(delimiter = '|', value = {
            "surname|SURNAME|false", "surname,desc|SURNAME|true", "membershipNumber,asc|MEMBERSHIP_NUMBER|false",
            "bank|BANK|false", " branch , DESC |BRANCH|true", "status,Desc|STATUS|true"})
    @DisplayName("each order the register is read in, either way, reaches the repository -- and the answer says it")
    void eachOrderReachesTheRepository(String sort, Order order, boolean descending) {
        when(register.count(any(), any())).thenReturn(1L);
        when(register.page(any(), any(), any(), anyBoolean(), anyInt(), anyInt())).thenReturn(rows(1));

        MemberRegisterPageDto page = service.page(query(0, 50, sort), CallerScope.all());

        verify(register).page(any(), any(), eq(order), eq(descending), eq(0), eq(50));
        assertThat(page.sort()).isEqualTo(order.key() + (descending ? ",desc" : ",asc"));
    }

    @Test
    @DisplayName("must-pass: the orders on the wire are the repository's, one for each order of V68")
    void theOrdersAreTheRepositorys() {
        assertThat(MemberRegisterService.SORTS)
                .containsExactly("surname", "membershipNumber", "bank", "branch", "status")
                .hasSize(Order.values().length);
    }

    @Test
    @DisplayName("🔒 a status the record does not take is STATUS_UNKNOWN, naming the ones it does")
    void anUnknownStatusIsRefused() {
        MemberRegisterQuery gone = new MemberRegisterQuery(null, "Gone", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, 0, 50, null);

        MonthEndRefusal refusal = refusalOf(gone);

        assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.STATUS_UNKNOWN);
        assertThat(refusal.details().get("known")).contains("Active", "Suspended", "Resigned");
        verifyNoInteractions(register);
    }

    @Test
    @DisplayName("🔒 ME-14: a text over its ceiling is REQUEST_MALFORMED, naming the field and giving nothing of it back")
    void anOverlongTextIsMalformed() {
        String typed = "8".repeat(MemberRegisterService.MAX_TERM_LENGTH + 1);
        MemberRegisterQuery overlong = new MemberRegisterQuery(typed, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, 0, 50, null);

        MonthEndRefusal refusal = refusalOf(overlong);

        assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.REQUEST_MALFORMED);
        assertThat(refusal.details()).containsExactly(Map.entry("field", "q"));
        assertThat(refusal.getMessage()).doesNotContain(typed);
        verifyNoInteractions(register);
    }

    @Test
    @DisplayName("every criterion is trimmed, and a blank one is not asked")
    void criteriaAreTrimmed() {
        when(register.count(any(), any())).thenReturn(0L);
        MemberRegisterQuery asked = new MemberRegisterQuery("  Mok  ", " Active ", "   ", null, null, null, null,
                null, null, null, " 1234 ", null, null, null, null, null, 0, 50, null);

        service.page(asked, CallerScope.all());

        ArgumentCaptor<Filter> filter = ArgumentCaptor.forClass(Filter.class);
        verify(register).count(filter.capture(), any());
        assertThat(filter.getValue().q()).isEqualTo("Mok");
        assertThat(filter.getValue().status()).isEqualTo("Active");
        assertThat(filter.getValue().bank()).isNull();
        assertThat(filter.getValue().identityNumber()).isEqualTo("1234");
    }

    @Test
    @DisplayName("the caller's scope reaches both statements, as it was given")
    void theScopeReachesTheRepository() {
        CallerScope area = CallerScope.areas(Set.of("A"));
        when(register.count(any(), any())).thenReturn(1L);
        when(register.page(any(), any(), any(), anyBoolean(), anyInt(), anyInt())).thenReturn(rows(1));

        service.page(null, area);

        verify(register).count(any(), eq(area));
        verify(register).page(any(), eq(area), any(), anyBoolean(), anyInt(), anyInt());
    }

    // ── the page arithmetic ──────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("an empty register is page 0 of 0 -- and its rows are never asked for")
    void anEmptyRegister() {
        when(register.count(any(), any())).thenReturn(0L);

        MemberRegisterPageDto page = service.page(query(4, 25, null), CallerScope.all());

        assertThat(page.content()).isEmpty();
        assertThat(page.page()).isZero();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
        verify(register, never()).page(any(), any(), any(), anyBoolean(), anyInt(), anyInt());
    }

    @ParameterizedTest(name = "{0} members, size {1}, page {2} -> page {3} of {4}")
    @CsvSource({
            "120, 50, 0, 0, 3", "120, 50, 1, 1, 3", "120, 50, 2, 2, 3",
            "100, 50, 1, 1, 2", "101, 50, 2, 2, 3", "1, 25, 0, 0, 1",
            // A page past the last is answered as the last -- and says so.
            "120, 50, 3, 2, 3", "120, 50, 2147483647, 2, 3", "7, 100, 9, 0, 1"})
    @DisplayName("the first, the last, and a page past the last -- answered as the last")
    void thePageArithmetic(long total, int size, int asked, int answered, int pages) {
        when(register.count(any(), any())).thenReturn(total);
        when(register.page(any(), any(), any(), anyBoolean(), anyInt(), anyInt())).thenReturn(rows(1));

        MemberRegisterPageDto page = service.page(query(asked, size, null), CallerScope.all());

        assertThat(page.page()).isEqualTo(answered);
        assertThat(page.totalPages()).isEqualTo(pages);
        assertThat(page.totalElements()).isEqualTo(total);
        verify(register).page(any(), any(), any(), anyBoolean(), eq(answered), eq(size));
    }

    // ── the figure strip ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the strip counts the caller's register as of SASBO's day: by status, the month's joiners, today's saves")
    void theFigures() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        byStatus.put("Active", 40L);
        byStatus.put("Pending", 3L);
        byStatus.put("Suspended", 5L);
        byStatus.put("Resigned", 11L);
        byStatus.put("A STATUS NOBODY KNOWS", 1L);
        byStatus.put(null, 2L);
        CallerScope region = CallerScope.regions(Set.of("IR1"));
        when(register.countsByStatus(region)).thenReturn(byStatus);
        when(register.joined(eq(region), any(), any(), any())).thenReturn(new long[] {6, 4});
        when(register.updated(eq(region), any(), any())).thenReturn(2L);
        when(register.branches(region, MemberRegisterService.MAX_BRANCHES)).thenReturn(List.of("INVENTED BRANCH"));

        MemberRegisterOverviewDto overview = service.overview(region);

        assertThat(overview.total()).as("every member, whatever their status").isEqualTo(62);
        assertThat(overview.active()).isEqualTo(40);
        assertThat(overview.pending()).isEqualTo(3);
        assertThat(overview.suspended()).isEqualTo(5);
        assertThat(overview.newThisMonth()).isEqualTo(6);
        assertThat(overview.newDebitOrders()).isEqualTo(4);
        assertThat(overview.updatedToday()).isEqualTo(2);
        assertThat(overview.branches()).containsExactly("INVENTED BRANCH");
        assertThat(overview.asOf()).isEqualTo("2026-10-06");
        verify(register).joined(region, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), "Debit Order");
    }

    @Test
    @DisplayName("🔒 \"today\" is SASBO's day, turned into the zone the saves are stamped in -- never the host's day")
    void todayIsSasbosDay() {
        // 23:30 in Johannesburg on 6 October is still the 6th there, and 21:30 on the 6th in UTC.
        Clock late = Clock.fixed(Instant.parse("2026-10-06T21:30:00Z"), AcbTime.ZONE);
        MemberRegisterService lateService = new MemberRegisterServiceImpl(register,
                TransactionOperations.withoutTransaction(), late);
        when(register.countsByStatus(any())).thenReturn(Map.of());
        when(register.joined(any(), any(), any(), any())).thenReturn(new long[] {0, 0});

        assertThat(lateService.overview(CallerScope.all()).asOf()).isEqualTo("2026-10-06");

        ZoneId stored = ZoneId.systemDefault();
        LocalDateTime dayStart = LocalDate.of(2026, 10, 6).atStartOfDay(AcbTime.ZONE).withZoneSameInstant(stored)
                .toLocalDateTime();
        verify(register).updated(CallerScope.all(), dayStart, dayStart.plusDays(1));
    }

    // ── the answer ───────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("🔒 a row carries the register's six columns and the id, and no identity, account or contact field")
    void aRowIsSlim() {
        assertThat(Arrays.stream(MemberRegisterPageDto.Row.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("id", "membershipNumber", "employeeNumber", "title", "initials", "surname",
                        "firstNames", "bankName", "branch", "status");
    }

    @Test
    @DisplayName("🔒 neither a row nor a question prints a member's name or numbers when logged")
    void nothingPrintsAValue() {
        MemberRegisterPageDto.Row row = rows(1).get(0);
        String identity = MemberFixture.syntheticIdFor(77);
        MemberRegisterQuery asked = new MemberRegisterQuery(identity, null, null, null, null, null, "Invented1", null,
                null, null, identity, "62000012345", "+27 60 555 0001", null, null, null, 0, 50, null);

        assertThat(row.toString()).isEqualTo("Row[id=1]");
        assertThat(asked.toString()).contains("q", "surname", "identityNumber")
                .doesNotContain(identity, "Invented1", "62000012345", "555");
    }
}
