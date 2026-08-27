package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.RecruitmentChannelDto;
import za.co.titandynamix.sasbo_online_backend.services.RecruitmentChannelService;

import java.util.List;

@RestController
@RequestMapping("/api/recruitment-channels")
@RequiredArgsConstructor
public class RecruitmentChannelController {

    private final RecruitmentChannelService recruitmentChannelService;

    @GetMapping
    public List<RecruitmentChannelDto> getAll() {
        return recruitmentChannelService.findAll();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody RecruitmentChannelDto dto) {
        try {
            return ResponseEntity.ok(recruitmentChannelService.create(dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody RecruitmentChannelDto dto) {
        try {
            return recruitmentChannelService.update(id, dto)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return recruitmentChannelService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
