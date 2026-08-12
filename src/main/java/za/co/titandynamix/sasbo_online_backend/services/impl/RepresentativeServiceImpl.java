package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeDto;
import za.co.titandynamix.sasbo_online_backend.entity.Member;
import za.co.titandynamix.sasbo_online_backend.mapper.RepresentativeMapper;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRepository;
import za.co.titandynamix.sasbo_online_backend.services.RepresentativeService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RepresentativeServiceImpl implements RepresentativeService {

    private final RepresentativeRepository repository;
    private final MemberRepository memberRepository;
    private final RepresentativeMapper mapper;

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

        var entity = this.mapper.toEntity(dto);
        entity.setName(name);
        entity.setMember(member);
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
