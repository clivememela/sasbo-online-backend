package za.co.titandynamix.sasbo_online_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.dto.DeletionCodeCreateDto;
import za.co.titandynamix.sasbo_online_backend.dto.DeletionCodeResponseDto;
import za.co.titandynamix.sasbo_online_backend.services.DeletionCodeService;

import java.util.List;

@RestController
@RequestMapping("/api/deletion-codes")
@RequiredArgsConstructor
public class DeletionCodeController {

    private final DeletionCodeService deletionCodeService;

    /**
     * GET /api/deletion-codes
     * Fetches all main categories with their nested sub-reasons.
     */
    @GetMapping
    public ResponseEntity<List<DeletionCodeResponseDto>> getAllRootCodes() {
        return ResponseEntity.ok(this.deletionCodeService.getRootDeletionCodes());
    }

    /**
     * GET /api/deletion-codes/{id}
     * Fetches a single deletion code by its ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<DeletionCodeResponseDto> getCodeById(@PathVariable Long id) {
        return ResponseEntity.ok(this.deletionCodeService.getDeletionCodeById(id));
    }

    /**
     * POST /api/deletion-codes
     * Creates a new deletion code.
     * Pass parentId in the query parameters if it is a sub-reason.
     */
    @PostMapping
    public ResponseEntity<DeletionCodeResponseDto> createCode(@Valid @RequestBody DeletionCodeCreateDto requestDto) {
        DeletionCodeResponseDto created = this.deletionCodeService.createDeletionCode(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

}
