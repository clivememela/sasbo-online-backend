package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.RecruitmentChannelDto;
import za.co.titandynamix.sasbo_online_backend.entity.recruitment.RecruitmentChannel;
import za.co.titandynamix.sasbo_online_backend.repository.RecruitmentChannelRepository;
import za.co.titandynamix.sasbo_online_backend.services.RecruitmentChannelService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecruitmentChannelServiceImpl implements RecruitmentChannelService {

    private final RecruitmentChannelRepository repository;

    @Override
    public List<RecruitmentChannelDto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    @Override
    public RecruitmentChannelDto create(RecruitmentChannelDto dto) {
        if (repository.existsByNameIgnoreCase(dto.getName())) {
            throw new IllegalArgumentException("A recruitment type with that name already exists.");
        }
        RecruitmentChannel entity = RecruitmentChannel.builder()
                .name(dto.getName().trim())
                .enabled(true)
                .build();
        return toDto(repository.save(entity));
    }

    @Override
    public Optional<RecruitmentChannelDto> update(Long id, RecruitmentChannelDto dto) {
        return repository.findById(id).map(existing -> {
            if (!existing.getName().equalsIgnoreCase(dto.getName()) &&
                    repository.existsByNameIgnoreCase(dto.getName())) {
                throw new IllegalArgumentException("A recruitment type with that name already exists.");
            }
            existing.setName(dto.getName().trim());
            existing.setEnabled(dto.isEnabled());
            return toDto(repository.save(existing));
        });
    }

    @Override
    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }

    private RecruitmentChannelDto toDto(RecruitmentChannel r) {
        RecruitmentChannelDto dto = new RecruitmentChannelDto();
        dto.setId(String.valueOf(r.getId()));
        dto.setName(r.getName());
        dto.setEnabled(r.isEnabled());
        return dto;
    }
}
