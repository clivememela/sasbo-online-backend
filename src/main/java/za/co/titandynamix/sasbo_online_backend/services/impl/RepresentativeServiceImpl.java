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

import java.util.*;

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
        // 1. Pull directly from the members table where is_representative is true
        List<Member> representativeMembers = this.memberRepository.findByIsRepresentativeTrue();

        // 2. Map the member entities straight into your existing RepresentativeDtos
        return representativeMembers.stream().map(member -> {
            String combinedName = buildName(member);

            return RepresentativeDto.builder()
                           .id(member.getId()) // Keeping the unique member identifier as the DTO ID
                           .memberId(member.getId())
                           .firstNames(member.getFirstNames())
                           .surname(member.getSurname())
                           .name(combinedName)
                           .personalEmail(member.getPersonalEmail())
                           .workEmail(member.getWorkEmail())
                           .preferredEmail(member.getPreferredEmail())
                           .phone(member.getPhone())
                           .isElectedRep(member.getIsRepresentative())
                           .electionDate(member.getElectionDate())
                           .areaId(member.getArea() != null ? member.getArea().getId() : null)
                           .roleId(member.getRole() != null ? member.getRole().getId() : null)
                           // If your structure setup uses a multi-select list, wrap the single ID in a list
                           .structureIds(member.getStructure() != null ? List.of(member.getStructure().getId()) : List.of())
                           .membersCount(0) // Safe placeholder value
                           .build();
        }).toList();
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

        // Validate conditional requirements based on isElectedRep flag
        if (Boolean.TRUE.equals(dto.getIsElectedRep()) && dto.getElectionDate() == null) {
            throw new IllegalArgumentException("Elected representatives require an election date to be specified.");
        }

        // Validate UserRole and its requirements
        RepresentativeRole role = null;
        if (dto.getRoleId() != null) {
            role = this.representativeRoleRepository.findById(dto.getRoleId())
                    .orElseThrow(() -> new IllegalArgumentException("UserRole not found with id: " + dto.getRoleId()));

            // Validate conditional requirements based on role
            if (Boolean.TRUE.equals(role.getRequiresStructure())
                    && (dto.getStructureIds() == null || dto.getStructureIds().isEmpty())) {
                throw new IllegalArgumentException("UserRole '" + role.getName() + "' requires at least one structure to be specified.");
            }
            if (Boolean.TRUE.equals(role.getIsElectedRole()) && dto.getElectionDate() == null) {
                throw new IllegalArgumentException("UserRole '" + role.getName() + "' requires an election date to be specified.");
            }
        }

        // Validate Structures if provided
        Set<UnionStructure> structures = new HashSet<>();
        if (dto.getStructureIds() != null && !dto.getStructureIds().isEmpty()) {
            for (Integer structureId : dto.getStructureIds()) {
                UnionStructure structure = this.unionStructureRepository.findById(structureId)
                        .orElseThrow(() -> new IllegalArgumentException("Structure not found with id: " + structureId));
                structures.add(structure);
            }
        }

        var entity = this.representativeMapper.toEntity(dto);
        String[] parts = name.split("\\s+", 2);
        entity.setFirstNames(parts[0]);
        entity.setSurname(parts.length > 1 ? parts[1] : "");
        entity.setMember(member);
        entity.setArea(area);
        entity.setRole(role);
        entity.setStructures(structures);
        entity.setIsElectedRep(dto.getIsElectedRep());
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
            existing.setPersonalEmail(dto.getPersonalEmail());
            existing.setWorkEmail(dto.getWorkEmail());
            existing.setPreferredEmail(dto.getPreferredEmail());
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
                if (Boolean.TRUE.equals(role.getRequiresStructure())
                        && (dto.getStructureIds() == null || dto.getStructureIds().isEmpty())) {
                    throw new IllegalArgumentException("UserRole '" + role.getName() + "' requires at least one structure to be specified.");
                }
                if (Boolean.TRUE.equals(role.getIsElectedRole()) && dto.getElectionDate() == null) {
                    throw new IllegalArgumentException("UserRole '" + role.getName() + "' requires an election date to be specified.");
                }

                existing.setRole(role);
            }

            // Update Structures if provided (replaces existing structures)
            if (dto.getStructureIds() != null) {
                Set<UnionStructure> structures = new HashSet<>();
                for (Integer structureId : dto.getStructureIds()) {
                    UnionStructure structure = this.unionStructureRepository.findById(structureId)
                            .orElseThrow(() -> new IllegalArgumentException("Structure not found with id: " + structureId));
                    structures.add(structure);
                }
                existing.setStructures(structures);
            }

            // Update isElectedRep flag
            if (dto.getIsElectedRep() != null) {
                existing.setIsElectedRep(dto.getIsElectedRep());
                // Clear election date if rep is not elected
                if (Boolean.FALSE.equals(dto.getIsElectedRep())) {
                    existing.setElectionDate(null);
                }
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

    /**
     * Builds the position/structure hierarchy as a tree for cascading dropdown selections.
     * Root-level structures (parentId = null) are returned at the top level,
     * each with its nested children populated recursively.
     */
    @Override
    public List<UnionStructureDto> findStructuresTree() {
        List<UnionStructure> all = this.unionStructureRepository.findAll();

        // Map every structure to a DTO (without children yet)
        Map<Integer, UnionStructureDto> dtoById = new LinkedHashMap<>();
        for (UnionStructure us : all) {
            UnionStructureDto dto = this.unionStructureMapper.toDto(us);
            dto.setChildren(new ArrayList<>());
            dtoById.put(us.getId(), dto);
        }

        // Wire children into their parents
        List<UnionStructureDto> roots = new ArrayList<>();
        for (UnionStructure us : all) {
            UnionStructureDto dto = dtoById.get(us.getId());
            if (us.getParent() == null) {
                roots.add(dto);
            } else {
                UnionStructureDto parentDto = dtoById.get(us.getParent().getId());
                if (parentDto != null) {
                    parentDto.getChildren().add(dto);
                } else {
                    // Orphan — treat as root
                    roots.add(dto);
                }
            }
        }

        // Remove empty children lists so JSON stays clean
        roots.forEach(this::pruneEmptyChildren);
        return roots;
    }

    /** Recursively removes empty children lists to keep the JSON response clean. */
    private void pruneEmptyChildren(UnionStructureDto dto) {
        if (dto.getChildren() != null && dto.getChildren().isEmpty()) {
            dto.setChildren(null);
        } else if (dto.getChildren() != null) {
            dto.getChildren().forEach(this::pruneEmptyChildren);
        }
    }

    private String buildName(Member m) {
        StringBuilder sb = new StringBuilder();
        if (m.getInitials() != null && !m.getInitials().isBlank()) sb.append(m.getInitials()).append(" ");
        if (m.getSurname() != null) sb.append(m.getSurname());
        return sb.toString().trim();
    }
}
