package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.dto.location.ProvinceDto;
import za.co.titandynamix.sasbo_online_backend.mapper.ProvinceMapper;
import za.co.titandynamix.sasbo_online_backend.repository.ProvinceRepository;
import za.co.titandynamix.sasbo_online_backend.services.ProvinceService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProvinceServiceImpl implements ProvinceService {

    private final ProvinceRepository provinceRepository;
    private final ProvinceMapper provinceMapper;

    @Override
    public List<ProvinceDto> findAll() {
        return this.provinceRepository.findAll().stream()
                .map(this.provinceMapper::toDto)
                .toList();
    }
}
