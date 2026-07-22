package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.dto.recruiter.RecruiterTypeDto;
import za.co.titandynamix.sasbo_online_backend.entity.recruiter.RecruiterType;
import za.co.titandynamix.sasbo_online_backend.repository.RecruiterTypeRepository;
import za.co.titandynamix.sasbo_online_backend.services.RecruiterTypeService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecruiterTypeServiceImpl implements RecruiterTypeService {

    private final RecruiterTypeRepository repository;

    @Override
    public List<RecruiterTypeDto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    @Override
    public RecruiterTypeDto create(RecruiterTypeDto dto) {
        if (repository.existsByNameIgnoreCase(dto.getName())) {
            throw new IllegalArgumentException("A recruiter type with that name already exists.");
        }
        RecruiterType entity = RecruiterType.builder()
                .name(dto.getName().trim())
                .enabled(true)
                .build();
        return toDto(repository.save(entity));
    }

    @Override
    public Optional<RecruiterTypeDto> update(Long id, RecruiterTypeDto dto) {
        return repository.findById(id).map(existing -> {
            if (!existing.getName().equalsIgnoreCase(dto.getName()) &&
                    repository.existsByNameIgnoreCase(dto.getName())) {
                throw new IllegalArgumentException("A recruiter type with that name already exists.");
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

    private RecruiterTypeDto toDto(RecruiterType r) {
        RecruiterTypeDto dto = new RecruiterTypeDto();
        dto.setId(String.valueOf(r.getId()));
        dto.setName(r.getName());
        dto.setEnabled(r.isEnabled());
        return dto;
    }
}
