package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.dto.location.ProvinceDto;
import za.co.titandynamix.sasbo_online_backend.services.ProvinceService;

import java.util.List;

@RestController
@RequestMapping("/api/provinces")
@RequiredArgsConstructor
public class ProvinceController {

    private final ProvinceService provinceService;

    @GetMapping
    public List<ProvinceDto> getAll() {
        return this.provinceService.findAll();
    }
}
