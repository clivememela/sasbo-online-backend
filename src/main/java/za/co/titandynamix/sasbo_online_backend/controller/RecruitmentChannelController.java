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

    /*
     * 🔴 A REFUSAL ANSWERED AS A BARE STRING (the manual's reading of Maintenance, 6 Oct). Both writes caught the
     * service's IllegalArgumentException and answered its message as plain text -- not the API's envelope -- so the
     * browser, reading JSON, found no message and said only "That request was rejected." for a name already taken.
     * The exception now reaches the global handler, which answers the envelope with the service's own sentence.
     */
    @PostMapping
    public ResponseEntity<RecruitmentChannelDto> create(@RequestBody RecruitmentChannelDto dto) {
        return ResponseEntity.ok(recruitmentChannelService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecruitmentChannelDto> update(@PathVariable Long id, @RequestBody RecruitmentChannelDto dto) {
        return recruitmentChannelService.update(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return recruitmentChannelService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
