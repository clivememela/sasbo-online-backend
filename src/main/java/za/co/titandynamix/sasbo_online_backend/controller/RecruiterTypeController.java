package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.dto.recruiter.RecruiterTypeDto;
import za.co.titandynamix.sasbo_online_backend.services.RecruiterTypeService;

import java.util.List;

@RestController
@RequestMapping("/api/recruiter-types")
@RequiredArgsConstructor
public class RecruiterTypeController {

    private final RecruiterTypeService recruiterTypeService;

    @GetMapping
    public List<RecruiterTypeDto> getAll() {
        return recruiterTypeService.findAll();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody RecruiterTypeDto dto) {
        try {
            return ResponseEntity.ok(recruiterTypeService.create(dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody RecruiterTypeDto dto) {
        try {
            return recruiterTypeService.update(id, dto)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return recruiterTypeService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
