package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.dto.location.AreaDto;
import za.co.titandynamix.sasbo_online_backend.repository.AreaRepository;

import java.util.List;

@RestController
@RequestMapping("/api/areas")
@RequiredArgsConstructor
public class AreaController {

    private final AreaRepository areaRepository;

    @GetMapping
    public List<AreaDto> getAll() {
        return this.areaRepository.findAll().stream()
                .map(area -> AreaDto.builder()
                        .id(area.getId())
                        .name(area.getName())
                        .build())
                .toList();
    }
}
