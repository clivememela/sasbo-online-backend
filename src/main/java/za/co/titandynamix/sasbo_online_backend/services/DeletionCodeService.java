package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.DeletionCodeCreateDto;
import za.co.titandynamix.sasbo_online_backend.dto.DeletionCodeResponseDto;

import java.util.List;

public interface DeletionCodeService {

    List<DeletionCodeResponseDto> getRootDeletionCodes();
    DeletionCodeResponseDto getDeletionCodeById(Long id);
    DeletionCodeResponseDto createDeletionCode(DeletionCodeCreateDto createDto);

}
