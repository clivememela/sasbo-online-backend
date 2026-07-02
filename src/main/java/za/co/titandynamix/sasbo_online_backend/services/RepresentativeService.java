package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeDto;

import java.util.List;
import java.util.Optional;

public interface RepresentativeService {
    List<RepresentativeDto> findAll();
    RepresentativeDto create(RepresentativeDto dto);
    Optional<RepresentativeDto> update(Integer id, RepresentativeDto dto);
    boolean delete(Integer id);
}
