package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbMemberDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbPage;
import za.co.titandynamix.sasbo_online_backend.dto.acb.MemberSearchRequest;
import za.co.titandynamix.sasbo_online_backend.fixtures.AcbRunFixture;
import za.co.titandynamix.sasbo_online_backend.services.AcbReturnsService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔒 04.1, R-SEC-B, R-PERF-D: the Debits screen's member search is server-side and paged. Part of a
 * surname or membership number matches in any case; an identity number matches whole or not at all;
 * a {@code %} or {@code _} typed is itself, never a wildcard; and a search with nothing in it answers
 * nobody, never everyone. Synthetic members only, with invented identity numbers.
 */
@DisplayName("The Debits screen's member search (04.1)")
class AcbMemberSearchIT extends AbstractAcbIT {

    @Autowired
    private AcbReturnsService returns;

    @BeforeEach
    void fourMembers() {
        member(5001, "S-500001", "Qzsearch", "0001010000181", "6200005001");
        member(5002, "S-500002", "QZSEARCHER", "0001010000182", "6200005002");
        member(5003, "S-500003", "Qz_search", "0001010000183", "6200005003");
        member(5004, "S-500004", "Qz%search", null, "6200005004");
    }

    private void member(int id, String number, String surname, String identity, String account) {
        jdbc.update("INSERT INTO members (id, membership_number, surname, initials, status, payment_type, "
                        + "bank_name, bank_account_number, debit_day, identity_number, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'AB', 'Active', 'Debit Order', 'Fixture Bank 00', ?, 25, ?, ?, ?)",
                id, number, surname, account, identity, AcbRunFixture.CREATED, AcbRunFixture.CREATED);
    }

    private AcbPage<AcbMemberDto> search(String surname, String number, String identity) {
        return returns.searchMembers(new MemberSearchRequest(surname, number, identity, 0, 25));
    }

    private static Integer[] ids(AcbPage<AcbMemberDto> page) {
        return page.content().stream().map(AcbMemberDto::id).toArray(Integer[]::new);
    }

    @Test
    @DisplayName("part of a surname finds its members in any case, and an account leaves as its last four digits")
    void partOfASurname() {
        AcbPage<AcbMemberDto> found = search("qzsearch", null, null);
        assertThat(ids(found)).containsExactlyInAnyOrder(5001, 5002);
        assertThat(found.totalElements()).isEqualTo(2);
        assertThat(found.content()).extracting(AcbMemberDto::accountLastFour)
                .containsExactlyInAnyOrder("5001", "5002");
    }

    @Test
    @DisplayName("🔒 a % or _ typed is only itself -- never a wildcard that answers every member")
    void wildcardsAreEscaped() {
        assertThat(ids(search("%", null, null))).containsExactly(5004);
        assertThat(ids(search("_", null, null))).containsExactly(5003);
        assertThat(ids(search(null, "!", null))).as("the escape character itself is only itself").isEmpty();
    }

    @Test
    @DisplayName("an identity number finds its member whole, and a part of one finds nobody")
    void identityIsWholeOrNothing() {
        assertThat(ids(search(null, null, "0001010000181"))).containsExactly(5001);
        assertThat(ids(search(null, null, " 0001010000182 "))).as("trimmed").containsExactly(5002);
        assertThat(ids(search(null, null, "000101"))).isEmpty();
    }

    @Test
    @DisplayName("part of a membership number finds its member in any case, and criteria narrow together")
    void partOfANumber() {
        assertThat(ids(search(null, "s-500003", null))).containsExactly(5003);
        assertThat(ids(search("qz", "s-50000", "0001010000182"))).containsExactly(5002);
    }

    @Test
    @DisplayName("🔒 a search with nothing in it answers nobody, never everyone")
    void anEmptySearchAnswersNobody() {
        AcbPage<AcbMemberDto> none = search(null, "  ", "");
        assertThat(none.content()).isEmpty();
        assertThat(none.totalElements()).isZero();
    }

    @Test
    @DisplayName("🔒 the page is capped, whatever size is asked for")
    void thePageIsCapped() {
        AcbPage<AcbMemberDto> page = returns.searchMembers(new MemberSearchRequest("qz", null, null, 0, 100_000));
        assertThat(page.size()).isEqualTo(AcbRunServiceImpl.MAX_PAGE_SIZE);
        assertThat(ids(page)).containsExactlyInAnyOrder(5001, 5002, 5003, 5004);
    }

    @Test
    @DisplayName("🔒 a found member carries neither an identity number nor a whole account number")
    void theRowCarriesNoSecrets() {
        assertThat(Arrays.stream(AcbMemberDto.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("identityNumber", "bankAccountNumber", "accountNumber", "passportNumber");
    }
}
