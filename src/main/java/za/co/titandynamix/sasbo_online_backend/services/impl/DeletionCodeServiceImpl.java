package za.co.titandynamix.sasbo_online_backend.services.impl;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.titandynamix.sasbo_online_backend.dto.deletion.DeletionCodeCreateDto;
import za.co.titandynamix.sasbo_online_backend.dto.deletion.DeletionCodeResponseDto;
import za.co.titandynamix.sasbo_online_backend.entity.codes.DeletionCode;
import za.co.titandynamix.sasbo_online_backend.repository.DeletionCodeRepository;
import za.co.titandynamix.sasbo_online_backend.services.DeletionCodeService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeletionCodeServiceImpl implements DeletionCodeService {

    private final DeletionCodeRepository deletionCodeRepository;

    /**
     * Retrieves all root-level deletion codes along with their nested sub-reasons.
     */
    public List<DeletionCodeResponseDto> getRootDeletionCodes() {
        return this.deletionCodeRepository.findByParentIsNull()
                       .stream()
                       .map(this::mapToDto)
                       .toList();
    }

    /**
     * Retrieves a single deletion code by ID.
     */
    public DeletionCodeResponseDto getDeletionCodeById(final Long id) {
        return this.deletionCodeRepository.findById(id)
                       .map(this::mapToDto)
                       .orElseThrow(() -> new EntityNotFoundException("Deletion code not found with ID: " + id));
    }

    /**
     * Creates a new root or sub-reason deletion code.
     */
    @Transactional
    public DeletionCodeResponseDto createDeletionCode(DeletionCodeCreateDto deletionCodeCreateDto) {
        DeletionCode.DeletionCodeBuilder builder = DeletionCode.builder().name(deletionCodeCreateDto.name());


        if (deletionCodeCreateDto.parentId() != null) {
            DeletionCode parent = deletionCodeRepository.findById(deletionCodeCreateDto.parentId())
                                          .orElseThrow(() -> new EntityNotFoundException("Parent code not found with ID: " + deletionCodeCreateDto.parentId()));
            builder.parent(parent);
        }

        DeletionCode savedCode = deletionCodeRepository.save(builder.build());
        return mapToDto(savedCode);
    }


    /**
     * Helper method to map Entity to DTO recursively.
     */
    private DeletionCodeResponseDto mapToDto(DeletionCode entity) {
        Long parentId = (entity.getParent() != null) ? entity.getParent().getId() : null;

        List<DeletionCodeResponseDto> subDtoList = entity.getSubReasons()
                                                           .stream()
                                                           .map(this::mapToDto)
                                                           .toList();

        return new DeletionCodeResponseDto(
                entity.getId(),
                entity.getName(),
                parentId,
                subDtoList
        );
    }
}
