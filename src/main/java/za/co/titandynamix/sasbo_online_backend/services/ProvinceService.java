package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.location.ProvinceDto;

import java.util.List;

public interface ProvinceService {
    List<ProvinceDto> findAll();
}
