package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeRoleDto;
import za.co.titandynamix.sasbo_online_backend.mapper.RepresentativeRoleMapper;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRoleRepository;

import java.util.List;

@RestController
@RequestMapping("/api/representative-userRoles")
@RequiredArgsConstructor
public class RepresentativeRoleController {

    private final RepresentativeRoleRepository repository;
    private final RepresentativeRoleMapper mapper;

    @GetMapping
    public List<RepresentativeRoleDto> getAll() {
        return this.repository.findAll().stream()
                .map(this.mapper::toDto)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<RepresentativeRoleDto> getById(@PathVariable Integer id) {
        return this.repository.findById(id)
                .map(this.mapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<RepresentativeRoleDto> create(@RequestBody RepresentativeRoleDto dto) {
        try {
            var entity = this.mapper.toEntity(dto);
            var saved = this.repository.save(entity);
            return ResponseEntity.ok(this.mapper.toDto(saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<RepresentativeRoleDto> update(@PathVariable Integer id, @RequestBody RepresentativeRoleDto dto) {
        return this.repository.findById(id).map(existing -> {
            existing.setName(dto.getName());
            existing.setDescription(dto.getDescription());
            existing.setRequiresStructure(dto.getRequiresStructure());
            existing.setIsElectedRole(dto.getIsElectedRole());
            var saved = this.repository.save(existing);
            return ResponseEntity.ok(this.mapper.toDto(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        if (!this.repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        this.repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
