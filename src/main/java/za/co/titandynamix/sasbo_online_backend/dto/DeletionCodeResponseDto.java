package za.co.titandynamix.sasbo_online_backend.dto;

import java.util.List;

public record DeletionCodeResponseDto(
        Long id,
        String name,
        Long parentId,
        List<DeletionCodeResponseDto> subReasons
) {}

