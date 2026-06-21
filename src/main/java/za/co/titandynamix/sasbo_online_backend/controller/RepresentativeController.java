package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeDto;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRepository;

import java.util.List;

@RestController
@RequestMapping("/api/representatives")
@RequiredArgsConstructor
public class RepresentativeController {

    private final RepresentativeRepository repository;

    @GetMapping
    public List<RepresentativeDto> getAll() {
        return this.repository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody RepresentativeDto repDto) {
        if (this.repository.existsByNameIgnoreCase(repDto.getName())) {
            return ResponseEntity.badRequest().body("A representative with that name already exists.");
        }
        Representative rep = toEntity(repDto);
        return ResponseEntity.ok(toDto(this.repository.save(rep)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody RepresentativeDto repDto) {
        return this.repository.findById(id).map(existing -> {
            if (!existing.getName().equalsIgnoreCase(repDto.getName()) &&
                        this.repository.existsByNameIgnoreCase(repDto.getName())) {
                return ResponseEntity.badRequest().body("A representative with that name already exists.");
            }
            existing.setName(repDto.getName());
            existing.setEmail(repDto.getEmail());
            existing.setPhone(repDto.getPhone());
            return ResponseEntity.ok(toDto(this.repository.save(existing)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        if (!this.repository.existsById(id)) return ResponseEntity.notFound().build();
        this.repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private RepresentativeDto toDto(Representative rep) {
        return RepresentativeDto.builder()
                .id(rep.getId())
                .name(rep.getName())
                .email(rep.getEmail())
                .phone(rep.getPhone())
                .build();
    }

    private Representative toEntity(RepresentativeDto dto) {
        Representative rep = new Representative();
        rep.setId(dto.getId());
        rep.setName(dto.getName());
        rep.setEmail(dto.getEmail());
        rep.setPhone(dto.getPhone());
        return rep;
    }
}
