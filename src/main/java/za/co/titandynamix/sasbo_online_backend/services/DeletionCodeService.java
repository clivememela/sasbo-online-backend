package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.deletion.DeletionCodeCreateDto;
import za.co.titandynamix.sasbo_online_backend.dto.deletion.DeletionCodeResponseDto;

import java.util.List;

public interface DeletionCodeService {

    List<DeletionCodeResponseDto> getRootDeletionCodes();
    DeletionCodeResponseDto getDeletionCodeById(Long id);
    DeletionCodeResponseDto createDeletionCode(DeletionCodeCreateDto createDto);

}
