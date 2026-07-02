package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeDto;
import za.co.titandynamix.sasbo_online_backend.services.RepresentativeService;

import java.util.List;

@RestController
@RequestMapping("/api/representatives")
@RequiredArgsConstructor
public class RepresentativeController {

    private final RepresentativeService representativeService;

    @GetMapping
    public List<RepresentativeDto> getAll() {
        return this.representativeService.findAll();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody RepresentativeDto dto) {
        try {
            return ResponseEntity.ok(this.representativeService.create(dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody RepresentativeDto dto) {
        try {
            return this.representativeService.update(id, dto)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        return this.representativeService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
