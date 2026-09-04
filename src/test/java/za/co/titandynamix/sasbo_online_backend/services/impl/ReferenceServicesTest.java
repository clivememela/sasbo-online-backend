package za.co.titandynamix.sasbo_online_backend.services.impl;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.titandynamix.sasbo_online_backend.dto.bank.BankBranchDto;
import za.co.titandynamix.sasbo_online_backend.dto.location.ProvinceDto;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.RecruitmentChannelDto;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.entity.bank.BankBranch;
import za.co.titandynamix.sasbo_online_backend.entity.codes.DeletionCode;
import za.co.titandynamix.sasbo_online_backend.entity.location.Province;
import za.co.titandynamix.sasbo_online_backend.entity.recruitment.RecruitmentChannel;
import za.co.titandynamix.sasbo_online_backend.fixtures.BankFixture;
import za.co.titandynamix.sasbo_online_backend.fixtures.LocationFixture;
import za.co.titandynamix.sasbo_online_backend.mapper.ProvinceMapper;
import za.co.titandynamix.sasbo_online_backend.repository.BankBranchRepository;
import za.co.titandynamix.sasbo_online_backend.repository.DeletionCodeRepository;
import za.co.titandynamix.sasbo_online_backend.repository.ProvinceRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RecruitmentChannelRepository;
import za.co.titandynamix.sasbo_online_backend.services.BankService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The five reference-data services, each in its own {@code @Nested} block.
 *
 * <p>Grouped because each is small -- one to four methods, mostly delegation
 * with a single rule -- and five near-empty files would obscure rather than
 * clarify. A failure still names one service, because the nesting carries it.
 *
 * <p>Written to the {@code JobGradeServiceTest} pattern: assert the branch, and
 * use a negative to prove the other branch was <i>not</i> taken.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("The reference services")
class ReferenceServicesTest {

    @Nested
    @DisplayName("ProvinceServiceImpl")
    class Provinces {

        @Mock private ProvinceRepository provinceRepository;
        @Mock private ProvinceMapper provinceMapper;

        @Test
        @DisplayName("maps every province the repository returns, in order")
        void mapsEveryProvince() {
            // Pure delegation plus a map, so the assertion is that nothing is
            // dropped or reordered. Stubbing per input is what makes that
            // checkable -- one shared stub would pass for a service that
            // returned the same DTO twice.
            Province gauteng = LocationFixture.province("Gauteng", "GP");
            Province limpopo = LocationFixture.province("Limpopo", "LP");
            when(this.provinceRepository.findAll()).thenReturn(List.of(gauteng, limpopo));
            when(this.provinceMapper.toDto(gauteng)).thenReturn(dto("Gauteng"));
            when(this.provinceMapper.toDto(limpopo)).thenReturn(dto("Limpopo"));

            List<ProvinceDto> result =
                    new ProvinceServiceImpl(this.provinceRepository, this.provinceMapper).findAll();

            assertThat(result).extracting(ProvinceDto::getName)
                    .containsExactly("Gauteng", "Limpopo");
        }

        @Test
        @DisplayName("returns an empty list rather than null when there are none")
        void emptyIsEmpty() {
            when(this.provinceRepository.findAll()).thenReturn(List.of());

            assertThat(new ProvinceServiceImpl(this.provinceRepository, this.provinceMapper)
                    .findAll()).isEmpty();
        }

        private ProvinceDto dto(String name) {
            ProvinceDto dto = new ProvinceDto();
            dto.setName(name);
            return dto;
        }
    }

    @Nested
    @DisplayName("RecruitmentChannelServiceImpl")
    class RecruitmentChannels {

        @Mock private RecruitmentChannelRepository repository;

        @Test
        @DisplayName("refuses a duplicate name, and writes nothing when it does")
        void refusesADuplicate() {
            // The negative verify is the assertion. A service that threw AFTER
            // saving would pass a test that only checked the exception.
            when(this.repository.existsByNameIgnoreCase("Walk in")).thenReturn(true);

            assertThatThrownBy(() -> service().create(dto("Walk in")))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(this.repository, never()).save(any(RecruitmentChannel.class));
        }

        @Test
        @DisplayName("trims the name before saving, and enables the channel")
        void trimsAndEnables() {
            when(this.repository.existsByNameIgnoreCase(any())).thenReturn(false);
            when(this.repository.save(any(RecruitmentChannel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            service().create(dto("  Walk in  "));

            ArgumentCaptor<RecruitmentChannel> saved =
                    ArgumentCaptor.forClass(RecruitmentChannel.class);
            verify(this.repository).save(saved.capture());
            assertThat(saved.getValue().getName()).isEqualTo("Walk in");
            assertThat(saved.getValue().isEnabled()).isTrue();
        }

        @Test
        @DisplayName("lets a channel keep its own name on update")
        void keepingTheSameNameIsNotADuplicate() {
            // The subtle branch: renaming to your own name must not trip the
            // duplicate check. Without this, editing a channel's enabled flag
            // would be rejected as a duplicate of itself.
            RecruitmentChannel existing = RecruitmentChannel.builder()
                    .id(1L).name("Walk in").enabled(true).build();
            when(this.repository.findById(1L)).thenReturn(Optional.of(existing));
            when(this.repository.save(any(RecruitmentChannel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            assertThat(service().update(1L, dto("Walk in"))).isPresent();

            verify(this.repository, never()).existsByNameIgnoreCase(any());
        }

        @Test
        @DisplayName("refuses a rename onto another channel's name")
        void refusesARenameOntoAnother() {
            RecruitmentChannel existing = RecruitmentChannel.builder()
                    .id(1L).name("Walk in").enabled(true).build();
            when(this.repository.findById(1L)).thenReturn(Optional.of(existing));
            when(this.repository.existsByNameIgnoreCase("Referral")).thenReturn(true);

            assertThatThrownBy(() -> service().update(1L, dto("Referral")))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(this.repository, never()).save(any(RecruitmentChannel.class));
        }

        @Test
        @DisplayName("delete reports whether it deleted anything")
        void deleteReportsTheOutcome() {
            when(this.repository.existsById(1L)).thenReturn(true);
            when(this.repository.existsById(99L)).thenReturn(false);

            assertThat(service().delete(1L)).isTrue();
            assertThat(service().delete(99L)).isFalse();
            verify(this.repository, never()).deleteById(99L);
        }

        private RecruitmentChannelServiceImpl service() {
            return new RecruitmentChannelServiceImpl(this.repository);
        }

        private RecruitmentChannelDto dto(String name) {
            RecruitmentChannelDto dto = new RecruitmentChannelDto();
            dto.setName(name);
            dto.setEnabled(true);
            return dto;
        }
    }

    @Nested
    @DisplayName("DeletionCodeServiceImpl")
    class DeletionCodes {

        @Mock private DeletionCodeRepository deletionCodeRepository;

        @Test
        @DisplayName("maps a root code with its sub-reasons nested underneath")
        void mapsTheHierarchy() {
            // The mapping is recursive and hand-written, which is where a
            // hierarchy quietly comes back flat.
            DeletionCode root = DeletionCode.builder().id(2L).name("Resigned Society").build();
            DeletionCode child = DeletionCode.builder().id(11L).name("Financial")
                    .parent(root).build();
            root.getSubReasons().add(child);
            when(this.deletionCodeRepository.findByParentIsNull()).thenReturn(List.of(root));

            var codes = new DeletionCodeServiceImpl(this.deletionCodeRepository)
                    .getRootDeletionCodes();

            assertThat(codes).hasSize(1);
            assertThat(codes.get(0).name()).isEqualTo("Resigned Society");
            assertThat(codes.get(0).parentId()).isNull();
            assertThat(codes.get(0).subReasons()).hasSize(1);
            assertThat(codes.get(0).subReasons().get(0).parentId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("throws rather than returning null for a code that does not exist")
        void missingCodeThrows() {
            // The controller relies on this: it is what the 404 in the error
            // envelope is made of.
            when(this.deletionCodeRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> new DeletionCodeServiceImpl(this.deletionCodeRepository)
                    .getDeletionCodeById(99L))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("BankingServiceImpl")
    class Banking {

        @Mock private za.co.titandynamix.sasbo_online_backend.services.BankBranchService
                bankBranchService;

        @Test
        @DisplayName("reads the bank name through the branch")
        void readsTheBankNameThroughTheBranch() {
            BankBranch branch = new BankBranch();
            branch.setBank(BankFixture.withId(1, "Absa"));
            when(this.bankBranchService.findById(4)).thenReturn(Optional.of(branch));

            assertThat(new BankingServiceImpl(this.bankBranchService).getBankNameFromBranch(4L))
                    .isEqualTo("Absa");
        }

        @Test
        @DisplayName("throws for a branch that does not exist")
        void missingBranchThrows() {
            when(this.bankBranchService.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> new BankingServiceImpl(this.bankBranchService)
                    .getBankNameFromBranch(99L))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("BankBranchServiceImpl")
    class BankBranches {

        @Mock private BankBranchRepository bankBranchRepository;
        @Mock private BankService bankService;

        @Test
        @DisplayName("attaches an existing bank rather than creating a second one")
        void reusesAnExistingBank() {
            Bank absa = BankFixture.withId(1, "Absa");
            when(this.bankService.findByNameIgnoreCase("Absa")).thenReturn(Optional.of(absa));
            when(this.bankBranchRepository.save(any(BankBranch.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            service().createFromDto(dtoNamed("Absa"));

            ArgumentCaptor<BankBranch> saved = ArgumentCaptor.forClass(BankBranch.class);
            verify(this.bankBranchRepository).save(saved.capture());
            assertThat(saved.getValue().getBank()).isSameAs(absa);
            verify(this.bankService, never()).save(any(Bank.class));
        }

        @Test
        @DisplayName("creates the bank when the name matches none, with a blank universal code")
        void createsAMissingBank() {
            // Worth pinning: the created bank gets universalBranchCode = "", not
            // null. That is what keeps the NOT NULL column satisfied, and it is
            // the kind of detail a refactor silently changes into a constraint
            // violation at import time.
            when(this.bankService.findByNameIgnoreCase("Newbank")).thenReturn(Optional.empty());
            when(this.bankService.save(any(Bank.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(this.bankBranchRepository.save(any(BankBranch.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            service().createFromDto(dtoNamed("Newbank"));

            ArgumentCaptor<Bank> created = ArgumentCaptor.forClass(Bank.class);
            verify(this.bankService).save(created.capture());
            assertThat(created.getValue().getName()).isEqualTo("Newbank");
            assertThat(created.getValue().getUniversalBranchCode()).isEmpty();
        }

        @Test
        @DisplayName("binds the branch fields from the payload it was given")
        void bindsTheBranchFields() {
            Bank absa = BankFixture.withId(1, "Absa");
            when(this.bankService.findByNameIgnoreCase(any())).thenReturn(Optional.of(absa));
            when(this.bankBranchRepository.save(any(BankBranch.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            BankBranchDto dto = dtoNamed("Absa");
            dto.setBranch("Sandton");
            dto.setBranchCode("632005");
            dto.setArea("Gauteng");
            dto.setEmail("branch@example.invalid");

            service().createFromDto(dto);

            ArgumentCaptor<BankBranch> saved = ArgumentCaptor.forClass(BankBranch.class);
            verify(this.bankBranchRepository).save(saved.capture());
            assertThat(saved.getValue().getBranchName()).isEqualTo("Sandton");
            assertThat(saved.getValue().getBranchCode()).isEqualTo("632005");
            assertThat(saved.getValue().getArea()).isEqualTo("Gauteng");
            assertThat(saved.getValue().getEmail()).isEqualTo("branch@example.invalid");
        }

        private BankBranchServiceImpl service() {
            return new BankBranchServiceImpl(this.bankBranchRepository, this.bankService);
        }

        private BankBranchDto dtoNamed(String bankName) {
            BankBranchDto dto = new BankBranchDto();
            dto.setBankName(bankName);
            return dto;
        }
    }
}
