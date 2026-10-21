package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.titandynamix.sasbo_online_backend.dto.subscription.SubscriptionsDto;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.entity.subscription.Subscriptions;
import za.co.titandynamix.sasbo_online_backend.enums.SubscriptionsStatus;
import za.co.titandynamix.sasbo_online_backend.fixtures.BankFixture;
import za.co.titandynamix.sasbo_online_backend.mapper.SubscriptionsMapper;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SubscriptionsRepository;
import za.co.titandynamix.sasbo_online_backend.support.KnownDefects;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The bank-name resolution this service performs, and the place the known
 * silent-drop defect lives.
 *
 * <p>Written to the {@code JobGradeServiceTest} pattern: real branching, and
 * negative assertions to prove a path was <i>not</i> taken.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubscriptionsServiceImpl")
class SubscriptionsServiceImplTest {

    private static final LocalDateTime REGISTERED = LocalDateTime.of(2024, 3, 1, 9, 0);

    @Mock
    private SubscriptionsRepository subscriptionRepository;

    @Mock
    private SubscriptionsMapper subscriptionsMapper;

    @Mock
    private BankRepository bankRepository;

    @Captor
    private ArgumentCaptor<Subscriptions> saved;

    private SubscriptionsServiceImpl service;

    @BeforeEach
    void setUp() {
        this.service = new SubscriptionsServiceImpl(
                this.subscriptionRepository, this.subscriptionsMapper, this.bankRepository);
    }

    @Test
    @DisplayName("findAll uses the fetch-joined query, and the bank-filtered one when given an id")
    void findAllPicksTheRightQuery() {
        // Two queries, one method, and the branch is a null check. Asserted with
        // a negative on the other side, so a service that always called one of
        // them would fail here.
        when(this.subscriptionRepository.findAllWithBank()).thenReturn(List.of());
        when(this.subscriptionRepository.findByBankIdWithBank(7L)).thenReturn(List.of());

        this.service.findAll(null);
        verify(this.subscriptionRepository).findAllWithBank();
        verify(this.subscriptionRepository, org.mockito.Mockito.never())
                .findByBankIdWithBank(any());

        this.service.findAll(7L);
        verify(this.subscriptionRepository).findByBankIdWithBank(7L);
    }

    @Test
    @DisplayName("resolves a bank by its exact name before falling back to a fuzzy match")
    void resolvesTheExactNameFirst() {
        // The exact lookup is the cheap, correct path. If the fallback ran
        // first, an exact name could match some other bank containing it.
        Bank absa = BankFixture.withId(1, "Absa");
        when(this.bankRepository.findByNameIgnoreCase("Absa")).thenReturn(Optional.of(absa));
        when(this.subscriptionsMapper.toEntity(any(SubscriptionsDto.class)))
                .thenReturn(new Subscriptions());
        when(this.subscriptionRepository.save(any(Subscriptions.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        this.service.create(dto("Absa"));

        verify(this.subscriptionRepository).save(this.saved.capture());
        assertThat(this.saved.getValue().getBank()).isEqualTo(absa);
        verify(this.bankRepository, org.mockito.Mockito.never()).findAll();
    }

    @Test
    @DisplayName("falls back to a partial match when no bank carries that exact name")
    void fallsBackToAPartialMatch() {
        // The fallback exists because imported names are inconsistent -- "Absa"
        // against "Absa Bank Limited". Asserting it is what distinguishes the
        // fallback working from the exact lookup happening to succeed.
        Bank absa = BankFixture.withId(1, "Absa Bank Limited");
        when(this.bankRepository.findByNameIgnoreCase("Absa")).thenReturn(Optional.empty());
        when(this.bankRepository.findAll()).thenReturn(List.of(absa));
        when(this.subscriptionsMapper.toEntity(any(SubscriptionsDto.class)))
                .thenReturn(new Subscriptions());
        when(this.subscriptionRepository.save(any(Subscriptions.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        this.service.create(dto("Absa"));

        verify(this.subscriptionRepository).save(this.saved.capture());
        assertThat(this.saved.getValue().getBank()).isEqualTo(absa);
    }

    @Test
    @DisplayName("leaves the bank alone when the payload names none")
    void noBankNameLeavesTheBankAlone() {
        when(this.subscriptionsMapper.toEntity(any(SubscriptionsDto.class)))
                .thenReturn(new Subscriptions());
        when(this.subscriptionRepository.save(any(Subscriptions.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        this.service.create(dto(null));

        verify(this.bankRepository, org.mockito.Mockito.never()).findByNameIgnoreCase(any());
    }

    @Test
    @Disabled(KnownDefects.UNMATCHED_BANK_NAME_IS_DROPPED_SILENTLY)
    @DisplayName("an unmatchable bank name is an error, not a silent success")
    void anUnmatchableBankNameIsAnError() {
        // The defect the register has been promising. Exact matching, then a
        // fuzzy fallback, then `.ifPresent()` with no `else` -- so a name that
        // matches nothing writes the record with a blank bank, no log and no
        // error. One such row is in the live database today.
        //
        // That is "a field that reads but does not write" in its purest form:
        // the write succeeds, the caller is told it succeeded, and the field is
        // simply not there. Nothing downstream can tell that apart from a
        // subscription that legitimately has no bank.
        //
        // Written to the correct behaviour. It turns green the day the miss is
        // made noisy -- which is a production change, and a decision about what
        // to do with the rows already written that way.
        when(this.bankRepository.findByNameIgnoreCase("Nowhere Bank"))
                .thenReturn(Optional.empty());
        when(this.bankRepository.findAll()).thenReturn(List.of());
        when(this.subscriptionsMapper.toEntity(any(SubscriptionsDto.class)))
                .thenReturn(new Subscriptions());

        assertThatThrownBy(() -> this.service.create(dto("Nowhere Bank")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("🔴 A23's route: a bank that already has its row is refused SUBSCRIPTION_BANK_TAKEN, and nothing is saved")
    void aSecondRowForABankIsRefused() {
        // Every reader takes a bank's row as THE row: capture takes the first, and a debit order run refuses a bank
        // with two (BANK_SUBSCRIPTION_AMBIGUOUS). Before this the second row was saved, and the bank's next run stopped.
        Bank absa = BankFixture.withId(1, "Absa");
        when(this.bankRepository.findByNameIgnoreCase("Absa")).thenReturn(Optional.of(absa));
        when(this.subscriptionRepository.existsByBank_Id(1)).thenReturn(true);
        when(this.subscriptionsMapper.toEntity(any(SubscriptionsDto.class))).thenReturn(new Subscriptions());

        assertThatThrownBy(() -> this.service.create(dto("Absa")))
                .isInstanceOf(za.co.titandynamix.sasbo_online_backend.exception.ReferenceRefusal.class)
                .hasMessage("Absa already has its subscription row. Change that row instead of adding another.")
                .satisfies(thrown -> assertThat(
                        ((za.co.titandynamix.sasbo_online_backend.exception.ReferenceRefusal) thrown).code())
                        .isEqualTo(za.co.titandynamix.sasbo_online_backend.exception.ReferenceRefusalCode
                                .SUBSCRIPTION_BANK_TAKEN));
        verify(this.subscriptionRepository, org.mockito.Mockito.never()).save(any(Subscriptions.class));
    }

    @Test
    @DisplayName("delete reports whether it deleted anything")
    void deleteReportsTheOutcome() {
        when(this.subscriptionRepository.existsById(1L)).thenReturn(true);
        when(this.subscriptionRepository.existsById(99L)).thenReturn(false);

        assertThat(this.service.delete(1L)).isTrue();
        assertThat(this.service.delete(99L)).isFalse();
        verify(this.subscriptionRepository).deleteById(1L);
        verify(this.subscriptionRepository, org.mockito.Mockito.never()).deleteById(99L);
    }

    private static SubscriptionsDto dto(String bankName) {
        return new SubscriptionsDto(1L, bankName, 25, new BigDecimal("120.50"), true,
                SubscriptionsStatus.ACTIVE, REGISTERED);
    }
}
