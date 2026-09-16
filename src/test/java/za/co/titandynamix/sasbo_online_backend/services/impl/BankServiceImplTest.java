package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import za.co.titandynamix.sasbo_online_backend.dto.bank.BankDto;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.fixtures.BankFixture;
import za.co.titandynamix.sasbo_online_backend.mapper.BankMapper;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.services.BankService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Rewritten. The previous version had nine tests, and all nine passed with
 * {@code findAll}'s sort deleted -- including three whose names claimed sort
 * order. Each stubbed {@code findAll(any(Sort.class))} with an already-ordered
 * list and then asserted the list was ordered, which proves that Mockito
 * returns what it was told to return.
 *
 * <p>Two of the nine were duplicates of other tests at a different size and are
 * gone. What replaces them is the distinction that matters:
 *
 * <ul>
 *   <li>A claim about <b>what a collaborator was called with</b> is made by an
 *       {@link ArgumentCaptor}, and the captured value is asserted. {@code any()}
 *       may not stand behind a claim of specificity.
 *   <li>A claim about <b>what the service returns</b> is only worth making
 *       where the service does something to it -- here, mapping entities to
 *       DTOs. Where the service is pure delegation, the test says so.
 * </ul>
 *
 * <p>Note what is deliberately NOT asserted: that the returned list is sorted.
 * The service does not sort, the database does, and H2 in PostgreSQL mode does
 * not reproduce PostgreSQL collation. Sort order is asserted in
 * {@code BankRepositoryIT} against the pinned container, or nowhere.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BankServiceImpl")
class BankServiceImplTest {

    @Mock
    private BankRepository bankRepository;

    @Mock
    private BankMapper bankMapper;

    @Captor
    private ArgumentCaptor<Sort> sortCaptor;

    @Captor
    private ArgumentCaptor<Bank> bankCaptor;

    private BankService bankService;

    @BeforeEach
    void setUp() {
        this.bankService = new BankServiceImpl(this.bankRepository, this.bankMapper);
    }

    @Test
    @DisplayName("asks the repository for banks ordered by name, ascending")
    void findAllRequestsAscendingNameOrder() {
        when(this.bankRepository.findAll(any(Sort.class))).thenReturn(List.of());

        this.bankService.findAll();

        // The point of the rewrite. The old test verified any(Sort.class) under
        // this same name, which is true of a call that passes Sort.unsorted().
        verify(this.bankRepository).findAll(this.sortCaptor.capture());
        assertThat(this.sortCaptor.getValue())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "name"));
    }

    @Test
    @DisplayName("returns the repository's result without altering it")
    void findAllDelegates() {
        // A delegation test, named as one. It is worth having because the
        // method could filter or de-duplicate and does not -- but it makes no
        // claim about order, which it could not support.
        List<Bank> fromRepository = List.of(
                BankFixture.withId(1, "Absa"), BankFixture.withId(2, "Capitec"));
        when(this.bankRepository.findAll(any(Sort.class))).thenReturn(fromRepository);

        assertThat(this.bankService.findAll()).isEqualTo(fromRepository);
    }

    @Test
    @DisplayName("maps every bank to a DTO on the DTO path, in the same order")
    void findAllDtosMapsEveryElement() {
        // Here the service does something -- it maps -- so asserting the result
        // is asserting the service. The mapper is stubbed per input so that a
        // service dropping or reordering elements shows up.
        Bank absa = BankFixture.withId(1, "Absa");
        Bank capitec = BankFixture.withId(2, "Capitec");
        when(this.bankRepository.findAll(any(Sort.class))).thenReturn(List.of(absa, capitec));
        when(this.bankMapper.toDto(absa)).thenReturn(dto(1, "Absa"));
        when(this.bankMapper.toDto(capitec)).thenReturn(dto(2, "Capitec"));

        List<BankDto> dtos = this.bankService.findAllDtos();

        assertThat(dtos)
                .extracting(BankDto::getId, BankDto::getName)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "Absa"),
                        org.assertj.core.groups.Tuple.tuple(2, "Capitec"));
    }

    @Test
    @DisplayName("the DTO path asks for the same ordering as the entity path")
    void findAllDtosRequestsAscendingNameOrder() {
        // Two methods, two sorts, and nothing stops them drifting apart. The
        // client reads the DTO endpoint, so this is the one that matters most.
        when(this.bankRepository.findAll(any(Sort.class))).thenReturn(List.of());

        this.bankService.findAllDtos();

        verify(this.bankRepository).findAll(this.sortCaptor.capture());
        assertThat(this.sortCaptor.getValue())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "name"));
    }

    @Test
    @DisplayName("looks a bank up by the exact name it was given")
    void findByNameIgnoreCasePassesTheNameThrough() {
        // Adopted from the previous version: it stubs on an exact argument, so
        // it does prove delegation rather than asserting its own stub.
        Bank absa = BankFixture.withId(1, "Absa");
        when(this.bankRepository.findByNameIgnoreCase("absa")).thenReturn(Optional.of(absa));

        assertThat(this.bankService.findByNameIgnoreCase("absa")).contains(absa);
    }

    @Test
    @DisplayName("returns empty when no bank carries that name")
    void findByNameIgnoreCaseEmpty() {
        when(this.bankRepository.findByNameIgnoreCase("Nowhere Bank")).thenReturn(Optional.empty());

        assertThat(this.bankService.findByNameIgnoreCase("Nowhere Bank")).isEmpty();
    }

    @Test
    @DisplayName("saves the bank it was handed, unchanged")
    void saveHandsTheBankToTheRepositoryUnchanged() {
        // The old testSaveNewBank asserted the fields of the object the stub
        // returned. What is actually worth knowing is what went IN -- a service
        // that blanked a field on the way through would have passed that test.
        Bank toSave = BankFixture.named("Capitec");
        when(this.bankRepository.save(any(Bank.class))).thenReturn(BankFixture.withId(9, "Capitec"));

        Bank saved = this.bankService.save(toSave);

        verify(this.bankRepository).save(this.bankCaptor.capture());
        assertThat(this.bankCaptor.getValue().getName()).isEqualTo("Capitec");
        assertThat(this.bankCaptor.getValue().getUniversalBranchCode())
                .isEqualTo(BankFixture.UNIVERSAL_BRANCH_CODE);
        assertThat(saved.getId()).isEqualTo(9);
    }

    @Test
    @DisplayName("does not consult the mapper on the entity path")
    void findAllDoesNotMap() {
        // A negative assertion, the pattern JobGradeServiceTest set. Without
        // it, nothing distinguishes findAll from findAllDtos.
        when(this.bankRepository.findAll(any(Sort.class))).thenReturn(List.of());

        this.bankService.findAll();

        verifyNoInteractions(this.bankMapper);
    }

    private static BankDto dto(Integer id, String name) {
        BankDto dto = new BankDto();
        dto.setId(id);
        dto.setName(name);
        dto.setUniversalBranchCode(BankFixture.UNIVERSAL_BRANCH_CODE);
        return dto;
    }
}
