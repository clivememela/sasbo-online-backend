package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeDto;
import za.co.titandynamix.sasbo_online_backend.mapper.RepresentativeMapper;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRepository;
import za.co.titandynamix.sasbo_online_backend.services.RepresentativeService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RepresentativeServiceImpl implements RepresentativeService {

    private final RepresentativeRepository repository;
    private final RepresentativeMapper mapper;

    @Override
    public List<RepresentativeDto> findAll() {
        return this.repository.findAll().stream().map(this.mapper::toDto).toList();
    }

    @Override
    public RepresentativeDto create(final RepresentativeDto dto) {
        if (this.repository.existsByNameIgnoreCase(dto.getName())) {
            throw new IllegalArgumentException("A representative with that name already exists.");
        }
        return this.mapper.toDto(this.repository.save(this.mapper.toEntity(dto)));
    }

    @Override
    public Optional<RepresentativeDto> update(final Integer id, final RepresentativeDto dto) {
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
    public boolean delete(final Integer id) {
        if (!this.repository.existsById(id)) return false;
        this.repository.deleteById(id);
        return true;
    }
}
