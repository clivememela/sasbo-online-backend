package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.recruiter.RecruiterTypeDto;

import java.util.List;
import java.util.Optional;

public interface RecruiterTypeService {
    List<RecruiterTypeDto> findAll();
    RecruiterTypeDto create(RecruiterTypeDto dto);
    Optional<RecruiterTypeDto> update(Long id, RecruiterTypeDto dto);
    boolean delete(Long id);
}
