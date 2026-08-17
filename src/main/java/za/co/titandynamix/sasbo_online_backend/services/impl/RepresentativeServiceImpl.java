package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeDto;
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeRoleDto;
import za.co.titandynamix.sasbo_online_backend.dto.representative.UnionStructureDto;
import za.co.titandynamix.sasbo_online_backend.entity.location.Area;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.representative.RepresentativeRole;
import za.co.titandynamix.sasbo_online_backend.entity.representative.UnionStructure;
import za.co.titandynamix.sasbo_online_backend.mapper.RepresentativeMapper;
import za.co.titandynamix.sasbo_online_backend.mapper.RepresentativeRoleMapper;
import za.co.titandynamix.sasbo_online_backend.mapper.UnionStructureMapper;
import za.co.titandynamix.sasbo_online_backend.repository.*;
import za.co.titandynamix.sasbo_online_backend.services.RepresentativeService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RepresentativeServiceImpl implements RepresentativeService {

    private final RepresentativeRepository representativeRepository;
    private final MemberRepository memberRepository;
    private final RepresentativeMapper representativeMapper;
    private final UnionStructureMapper unionStructureMapper;
    private final RepresentativeRoleMapper representativeRoleMapper;
    private final RepresentativeRoleRepository representativeRoleRepository;
    private final AreaRepository areaRepository;
    private final UnionStructureRepository unionStructureRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RepresentativeDto> findAll() {
        return this.representativeRepository.findAll().stream().map(this.representativeMapper::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RepresentativeDto> findById(Integer id) {
        return this.representativeRepository.findById(id).map(this.representativeMapper::toDto);
    }

    @Override
    @Transactional
    public RepresentativeDto create(RepresentativeDto dto) {
        // Business rule: a representative must be a member
        if (dto.getMemberId() == null) {
            throw new IllegalArgumentException("A representative must be an existing member.");
        }
        Member member = this.memberRepository.findById(dto.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("Member not found with id: " + dto.getMemberId()));

        // Derive name from the combined "name" field, falling back to firstNames, then the member
        String name = (dto.getName() != null && !dto.getName().isBlank())
                ? dto.getName()
                : (dto.getFirstNames() != null && !dto.getFirstNames().isBlank())
                    ? dto.getFirstNames()
                    : buildName(member);


        // Validate Area (optional — can be set later via edit)
        Area area = null;
        if (dto.getAreaId() != null) {
            area = this.areaRepository.findById(dto.getAreaId())
                    .orElseThrow(() -> new IllegalArgumentException("Area not found with id: " + dto.getAreaId()));
        }

        // Validate UserRole and its requirements
        RepresentativeRole role = null;
        if (dto.getRoleId() != null) {
            role = this.representativeRoleRepository.findById(dto.getRoleId())
                    .orElseThrow(() -> new IllegalArgumentException("UserRole not found with id: " + dto.getRoleId()));

            // Validate conditional requirements based on role
            if (Boolean.TRUE.equals(role.getRequiresStructure()) && dto.getStructureId() == null) {
                throw new IllegalArgumentException("UserRole '" + role.getName() + "' requires a structure to be specified.");
            }
            if (Boolean.TRUE.equals(role.getIsElectedRole()) && dto.getElectionDate() == null) {
                throw new IllegalArgumentException("UserRole '" + role.getName() + "' requires an election date to be specified.");
            }
        }

        // Validate Structure if provided
        UnionStructure structure = null;
        if (dto.getStructureId() != null) {
            structure = this.unionStructureRepository.findById(dto.getStructureId())
                    .orElseThrow(() -> new IllegalArgumentException("Structure not found with id: " + dto.getStructureId()));
        }

        var entity = this.representativeMapper.toEntity(dto);
        String[] parts = name.split("\\s+", 2);
        entity.setFirstNames(parts[0]);
        entity.setSurname(parts.length > 1 ? parts[1] : "");
        entity.setMember(member);
        entity.setArea(area);
        entity.setRole(role);
        entity.setStructure(structure);
        entity.setElectionDate(dto.getElectionDate());
        return this.representativeMapper.toDto(this.representativeRepository.save(entity));
    }

    @Override
    @Transactional
    public Optional<RepresentativeDto> update(Integer id, RepresentativeDto dto) {
        return this.representativeRepository.findById(id).map(existing -> {
            // Update the name when a combined "name" field is provided
            String newName = (dto.getName() != null && !dto.getName().isBlank())
                    ? dto.getName().trim()
                    : null;
            if (newName != null) {
                String[] parts = newName.split("\\s+", 2);
                existing.setFirstNames(parts[0]);
                existing.setSurname(parts.length > 1 ? parts[1] : "");
            }
            existing.setEmail(dto.getEmail());
            existing.setPhone(dto.getPhone());

            // Update Area if provided
            if (dto.getAreaId() != null) {
                Area area = this.areaRepository.findById(dto.getAreaId())
                        .orElseThrow(() -> new IllegalArgumentException("Area not found with id: " + dto.getAreaId()));
                existing.setArea(area);
            }

            // Update UserRole if provided
            if (dto.getRoleId() != null) {
                RepresentativeRole role = this.representativeRoleRepository.findById(dto.getRoleId())
                        .orElseThrow(() -> new IllegalArgumentException("UserRole not found with id: " + dto.getRoleId()));

                // Validate conditional requirements
                if (Boolean.TRUE.equals(role.getRequiresStructure()) && dto.getStructureId() == null) {
                    throw new IllegalArgumentException("UserRole '" + role.getName() + "' requires a structure to be specified.");
                }
                if (Boolean.TRUE.equals(role.getIsElectedRole()) && dto.getElectionDate() == null) {
                    throw new IllegalArgumentException("UserRole '" + role.getName() + "' requires an election date to be specified.");
                }

                existing.setRole(role);
            }

            // Update Structure if provided
            if (dto.getStructureId() != null) {
                UnionStructure structure = this.unionStructureRepository.findById(dto.getStructureId())
                        .orElseThrow(() -> new IllegalArgumentException("Structure not found with id: " + dto.getStructureId()));
                existing.setStructure(structure);
            }

            // Update Election Date if provided
            if (dto.getElectionDate() != null) {
                existing.setElectionDate(dto.getElectionDate());
            }

            return this.representativeMapper.toDto(this.representativeRepository.save(existing));
        });
    }

    @Override
    public boolean delete(Integer id) {
        if (!this.representativeRepository.existsById(id)) return false;
        this.representativeRepository.deleteById(id);
        return true;
    }

    /**
     * @return List<RepresentativeRoleDto> a list of all representative userRoles
     */
    @Override
    public List<RepresentativeRoleDto> findAllRepresentativeRoles() {

        return this.representativeRoleRepository.findAll().stream().map(this.representativeRoleMapper::toDto).toList();
    }

    /**
     * @return List<UnionStructureDto>
     */
    @Override
    public List<UnionStructureDto> findAllStructures() {
        return this.unionStructureRepository.findAll().stream().map(this.unionStructureMapper::toDto).toList();
    }

    private String buildName(Member m) {
        StringBuilder sb = new StringBuilder();
        if (m.getInitials() != null && !m.getInitials().isBlank()) sb.append(m.getInitials()).append(" ");
        if (m.getSurname() != null) sb.append(m.getSurname());
        return sb.toString().trim();
    }
}
