package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeDto;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;
import za.co.titandynamix.sasbo_online_backend.entity.representative.RepresentativeRole;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.mapper.RepresentativeMapper;
import za.co.titandynamix.sasbo_online_backend.mapper.RepresentativeRoleMapper;
import za.co.titandynamix.sasbo_online_backend.mapper.UnionStructureMapper;
import za.co.titandynamix.sasbo_online_backend.repository.AreaRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRoleRepository;
import za.co.titandynamix.sasbo_online_backend.repository.UnionStructureRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The representative rules -- the densest branching on the server, and none of
 * it was covered.
 *
 * <p>Five conditions can reject a create, each for a different reason, and one
 * of them is the union's own governance rule: an elected role requires an
 * election date. Every rejection here is asserted with a <b>negative verify</b>
 * as well, because a service that threw <i>after</i> writing would satisfy a
 * test that only checked the exception -- and half a representative is worse
 * than none.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RepresentativeServiceImpl")
class RepresentativeServiceImplTest {

    /** Fixed. Nothing here reads the wall clock. */
    private static final LocalDate ELECTED_ON = LocalDate.of(2023, 6, 15);

    @Mock private RepresentativeRepository representativeRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private RepresentativeMapper representativeMapper;
    @Mock private UnionStructureMapper unionStructureMapper;
    @Mock private RepresentativeRoleMapper representativeRoleMapper;
    @Mock private RepresentativeRoleRepository representativeRoleRepository;
    @Mock private AreaRepository areaRepository;
    @Mock private UnionStructureRepository unionStructureRepository;

    @Captor
    private ArgumentCaptor<Representative> saved;

    private RepresentativeServiceImpl service;

    @BeforeEach
    void setUp() {
        this.service = new RepresentativeServiceImpl(
                this.representativeRepository, this.memberRepository, this.representativeMapper,
                this.unionStructureMapper, this.representativeRoleMapper,
                this.representativeRoleRepository, this.areaRepository,
                this.unionStructureRepository);

        when(this.representativeMapper.toEntity(any(RepresentativeDto.class)))
                .thenAnswer(invocation -> new Representative());
        when(this.representativeRepository.save(any(Representative.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(this.representativeMapper.toDto(any(Representative.class)))
                .thenReturn(new RepresentativeDto());
    }

    @Test
    @DisplayName("refuses a representative who is not an existing member")
    void refusesANonMember() {
        // The union's rule: a representative is a member first. Asserted with
        // the negative verify, so a service that saved first would still fail.
        assertThatThrownBy(() -> this.service.create(new RepresentativeDto()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(this.representativeRepository, never()).save(any(Representative.class));
    }

    @Test
    @DisplayName("refuses a member id that matches no member")
    void refusesAnUnknownMember() {
        when(this.memberRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.create(dtoFor(99)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(this.representativeRepository, never()).save(any(Representative.class));
    }

    @Test
    @DisplayName("refuses an elected representative with no election date")
    void refusesAnElectedRepWithNoDate() {
        this.stubMember();
        RepresentativeDto dto = dtoFor(1);
        dto.setIsElectedRep(Boolean.TRUE);
        dto.setElectionDate(null);

        assertThatThrownBy(() -> this.service.create(dto))
                .isInstanceOf(IllegalArgumentException.class);

        verify(this.representativeRepository, never()).save(any(Representative.class));
    }

    @Test
    @DisplayName("refuses an elected ROLE with no election date, which is a separate rule")
    void refusesAnElectedRoleWithNoDate() {
        // Two rules that look like one: the flag on the DTO, and the flag on the
        // ROLE. A representative can carry an elected role without the DTO's own
        // flag being set, and the governance requirement is the role's.
        this.stubMember();
        RepresentativeRole elected = MemberFixture.role("Branch Chair");
        elected.setIsElectedRole(Boolean.TRUE);
        when(this.representativeRoleRepository.findById(3)).thenReturn(Optional.of(elected));

        RepresentativeDto dto = dtoFor(1);
        dto.setRoleId(3);

        assertThatThrownBy(() -> this.service.create(dto))
                .isInstanceOf(IllegalArgumentException.class);

        verify(this.representativeRepository, never()).save(any(Representative.class));
    }

    @Test
    @DisplayName("refuses a structure-requiring role with no structures")
    void refusesAStructureRequiringRoleWithNoStructures() {
        this.stubMember();
        RepresentativeRole needsStructure = MemberFixture.role("Provincial Delegate");
        needsStructure.setRequiresStructure(Boolean.TRUE);
        when(this.representativeRoleRepository.findById(4)).thenReturn(Optional.of(needsStructure));

        RepresentativeDto dto = dtoFor(1);
        dto.setRoleId(4);
        dto.setStructureIds(List.of());

        assertThatThrownBy(() -> this.service.create(dto))
                .isInstanceOf(IllegalArgumentException.class);

        verify(this.representativeRepository, never()).save(any(Representative.class));
    }

    @Test
    @DisplayName("refuses a structure id that matches no structure")
    void refusesAnUnknownStructure() {
        this.stubMember();
        when(this.unionStructureRepository.findById(77)).thenReturn(Optional.empty());

        RepresentativeDto dto = dtoFor(1);
        dto.setStructureIds(List.of(77));

        assertThatThrownBy(() -> this.service.create(dto))
                .isInstanceOf(IllegalArgumentException.class);

        verify(this.representativeRepository, never()).save(any(Representative.class));
    }

    @Test
    @DisplayName("splits a single name field into first names and surname")
    void splitsTheName() {
        // The service takes one "name" and stores two columns. The split is
        // limit-2, so a middle name stays with the surname -- worth pinning,
        // because it is the kind of thing a refactor "tidies" into three columns
        // or into a different split and nobody notices until a report is wrong.
        this.stubMember();
        RepresentativeDto dto = dtoFor(1);
        dto.setName("Thandi Nomsa Khumalo");

        this.service.create(dto);

        verify(this.representativeRepository).save(this.saved.capture());
        assertThat(this.saved.getValue().getFirstNames()).isEqualTo("Thandi");
        assertThat(this.saved.getValue().getSurname()).isEqualTo("Nomsa Khumalo");
    }

    @Test
    @DisplayName("gives a one-word name an empty surname rather than a null one")
    void aSingleWordNameGetsAnEmptySurname() {
        // The boundary of that split. Empty rather than null matters: the full
        // name is rebuilt by concatenation elsewhere, and a null there produces
        // "Thandi null" or nothing at all depending on the path.
        this.stubMember();
        RepresentativeDto dto = dtoFor(1);
        dto.setName("Thandi");

        this.service.create(dto);

        verify(this.representativeRepository).save(this.saved.capture());
        assertThat(this.saved.getValue().getSurname()).isEmpty();
    }

    @Test
    @DisplayName("accepts an elected representative once an election date is present")
    void acceptsAnElectedRepWithADate() {
        // The positive half. Without it, every rejection above would still pass
        // against a service that rejected all elected representatives outright.
        this.stubMember();
        RepresentativeDto dto = dtoFor(1);
        dto.setIsElectedRep(Boolean.TRUE);
        dto.setElectionDate(ELECTED_ON);

        this.service.create(dto);

        verify(this.representativeRepository).save(this.saved.capture());
        assertThat(this.saved.getValue().getElectionDate()).isEqualTo(ELECTED_ON);
    }

    @Test
    @DisplayName("delete reports whether it deleted anything")
    void deleteReportsTheOutcome() {
        when(this.representativeRepository.existsById(1)).thenReturn(true);
        when(this.representativeRepository.existsById(99)).thenReturn(false);

        assertThat(this.service.delete(1)).isTrue();
        assertThat(this.service.delete(99)).isFalse();
        verify(this.representativeRepository, never()).deleteById(99);
    }

    private void stubMember() {
        Member member = MemberFixture.representativeMember("Khumalo", "ACTIVE");
        member.setId(1);
        when(this.memberRepository.findById(1)).thenReturn(Optional.of(member));
    }

    private static RepresentativeDto dtoFor(Integer memberId) {
        RepresentativeDto dto = new RepresentativeDto();
        dto.setMemberId(memberId);
        dto.setName("Thandi Khumalo");
        return dto;
    }
}
