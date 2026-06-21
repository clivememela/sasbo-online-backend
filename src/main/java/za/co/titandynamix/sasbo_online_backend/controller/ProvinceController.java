package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.dto.location.ProvinceDto;
import za.co.titandynamix.sasbo_online_backend.mapper.ProvinceMapper;
import za.co.titandynamix.sasbo_online_backend.repository.ProvinceRepository;

import java.util.List;

@RestController
@RequestMapping("/api/provinces")
@RequiredArgsConstructor
public class ProvinceController {

    private final ProvinceRepository provinceRepository;
    private final ProvinceMapper provinceMapper;

    @GetMapping
    public List<ProvinceDto> getAll() {
        return this.provinceRepository.findAll().stream()
                .map(this.provinceMapper::toDto)
                .toList();
    }
}
