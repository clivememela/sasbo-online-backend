package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.entity.Representative;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRepository;

import java.util.List;

@RestController
@RequestMapping("/api/representatives")
@RequiredArgsConstructor
public class RepresentativeController {

    private final RepresentativeRepository repository;

    @GetMapping
    public List<Representative> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Representative rep) {
        if (repository.existsByNameIgnoreCase(rep.getName())) {
            return ResponseEntity.badRequest().body("A representative with that name already exists.");
        }
        return ResponseEntity.ok(repository.save(rep));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody Representative rep) {
        return repository.findById(id).map(existing -> {
            if (!existing.getName().equalsIgnoreCase(rep.getName()) &&
                    repository.existsByNameIgnoreCase(rep.getName())) {
                return ResponseEntity.badRequest().body("A representative with that name already exists.");
            }
            existing.setName(rep.getName());
            existing.setEmail(rep.getEmail());
            existing.setPhone(rep.getPhone());
            return ResponseEntity.ok(repository.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        if (!repository.existsById(id)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
