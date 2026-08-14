package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeDto;
import za.co.titandynamix.sasbo_online_backend.entity.Area;
import za.co.titandynamix.sasbo_online_backend.entity.Member;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;
import za.co.titandynamix.sasbo_online_backend.entity.representative.RepresentativeRole;
import za.co.titandynamix.sasbo_online_backend.entity.representative.UnionStructure;
import za.co.titandynamix.sasbo_online_backend.mapper.RepresentativeMapper;
import za.co.titandynamix.sasbo_online_backend.repository.*;
import za.co.titandynamix.sasbo_online_backend.services.RepresentativeService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RepresentativeServiceImpl implements RepresentativeService {

    private final RepresentativeRepository repository;
    private final MemberRepository memberRepository;
    private final RepresentativeMapper mapper;
    private final RepresentativeRoleRepository roleRepository;
    private final AreaRepository areaRepository;
    private final UnionStructureRepository structureRepository;

    @Override
    public List<RepresentativeDto> findAll() {
        return this.repository.findAll().stream().map(this.mapper::toDto).toList();
    }

    @Override
    public RepresentativeDto create(RepresentativeDto dto) {
        // Business rule: a representative must be a member
        if (dto.getMemberId() == null) {
            throw new IllegalArgumentException("A representative must be an existing member.");
        }
        Member member = this.memberRepository.findById(dto.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("Member not found with id: " + dto.getMemberId()));

        // Business rule: a member can only hold one representative role
        if (this.repository.existsByMemberId(member.getId())) {
            throw new IllegalArgumentException("This member is already a representative.");
        }

        // Derive name from member if not provided
        String name = (dto.getName() != null && !dto.getName().isBlank())
                ? dto.getName()
                : buildName(member);

        if (this.repository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("A representative with that name already exists.");
        }

        // Validate Area
        Area area = null;
        if (dto.getAreaId() != null) {
            area = this.areaRepository.findById(dto.getAreaId())
                    .orElseThrow(() -> new IllegalArgumentException("Area not found with id: " + dto.getAreaId()));
        } else {
            throw new IllegalArgumentException("Area is required for a representative.");
        }

        // Validate Role and its requirements
        RepresentativeRole role = null;
        if (dto.getRoleId() != null) {
            role = this.roleRepository.findById(dto.getRoleId())
                    .orElseThrow(() -> new IllegalArgumentException("Role not found with id: " + dto.getRoleId()));

            // Validate conditional requirements based on role
            if (Boolean.TRUE.equals(role.getRequiresStructure()) && dto.getStructureId() == null) {
                throw new IllegalArgumentException("Role '" + role.getName() + "' requires a structure to be specified.");
            }
            if (Boolean.TRUE.equals(role.getRequiresElectionDate()) && dto.getElectionDate() == null) {
                throw new IllegalArgumentException("Role '" + role.getName() + "' requires an election date to be specified.");
            }
        }

        // Validate Structure if provided
        UnionStructure structure = null;
        if (dto.getStructureId() != null) {
            structure = this.structureRepository.findById(dto.getStructureId())
                    .orElseThrow(() -> new IllegalArgumentException("Structure not found with id: " + dto.getStructureId()));
        }

        var entity = this.mapper.toEntity(dto);
        entity.setName(name);
        entity.setMember(member);
        entity.setArea(area);
        entity.setRole(role);
        entity.setStructure(structure);
        entity.setElectionDate(dto.getElectionDate());
        return this.mapper.toDto(this.repository.save(entity));
    }

    @Override
    public Optional<RepresentativeDto> update(Integer id, RepresentativeDto dto) {
        return this.repository.findById(id).map(existing -> {
            if (!existing.getName().equalsIgnoreCase(dto.getName()) &&
                    this.repository.existsByNameIgnoreCase(dto.getName())) {
                throw new IllegalArgumentException("A representative with that name already exists.");
            }

            existing.setName(dto.getName());
            existing.setEmail(dto.getEmail());
            existing.setPhone(dto.getPhone());

            // Update Area if provided
            if (dto.getAreaId() != null) {
                Area area = this.areaRepository.findById(dto.getAreaId())
                        .orElseThrow(() -> new IllegalArgumentException("Area not found with id: " + dto.getAreaId()));
                existing.setArea(area);
            }

            // Update Role if provided
            if (dto.getRoleId() != null) {
                RepresentativeRole role = this.roleRepository.findById(dto.getRoleId())
                        .orElseThrow(() -> new IllegalArgumentException("Role not found with id: " + dto.getRoleId()));

                // Validate conditional requirements
                if (Boolean.TRUE.equals(role.getRequiresStructure()) && dto.getStructureId() == null) {
                    throw new IllegalArgumentException("Role '" + role.getName() + "' requires a structure to be specified.");
                }
                if (Boolean.TRUE.equals(role.getRequiresElectionDate()) && dto.getElectionDate() == null) {
                    throw new IllegalArgumentException("Role '" + role.getName() + "' requires an election date to be specified.");
                }

                existing.setRole(role);
            }

            // Update Structure if provided
            if (dto.getStructureId() != null) {
                UnionStructure structure = this.structureRepository.findById(dto.getStructureId())
                        .orElseThrow(() -> new IllegalArgumentException("Structure not found with id: " + dto.getStructureId()));
                existing.setStructure(structure);
            }

            // Update Election Date if provided
            if (dto.getElectionDate() != null) {
                existing.setElectionDate(dto.getElectionDate());
            }

            return this.mapper.toDto(this.repository.save(existing));
        });
    }

    @Override
    public boolean delete(Integer id) {
        if (!this.repository.existsById(id)) return false;
        this.repository.deleteById(id);
        return true;
    }

    private String buildName(Member m) {
        StringBuilder sb = new StringBuilder();
        if (m.getInitials() != null && !m.getInitials().isBlank()) sb.append(m.getInitials()).append(" ");
        if (m.getSurname() != null) sb.append(m.getSurname());
        return sb.toString().trim();
    }
}
