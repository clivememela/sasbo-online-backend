package za.co.titandynamix.sasbo_online_backend.controller;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.dto.member.JobGradeDto;
import za.co.titandynamix.sasbo_online_backend.dto.member.JobGradeResponseDto;
import za.co.titandynamix.sasbo_online_backend.entity.member.JobGrade;
import za.co.titandynamix.sasbo_online_backend.services.JobGradeService;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/job-grades")
@RequiredArgsConstructor
public class JobGradeController {
    private static final Logger log = LoggerFactory.getLogger(JobGradeController.class);
    private final JobGradeService jobGradeService;

    @GetMapping
    public ResponseEntity<List<JobGradeResponseDto>> getByBank(
            @RequestParam(name = "bankCode", required = false) String bankCode,
            @RequestParam(name = "bank", required = false) String bank) {

        String identifier = (bankCode != null && !bankCode.isBlank()) ? bankCode : bank;

        // The DROP-DOWN gets the verified master list only. A grade an administrator
        // typed is stored and reviewable, but offering it here would turn one typo into
        // everyone's option -- and the drop-down exists for consistency (6c clause 3).
        // Resolution on save still sees every grade, verified or not, via
        // findByBankIdentifier; the two lists are deliberately different.
        List<JobGrade> jobGradeList = jobGradeService.findVerifiedByBankIdentifier(identifier);

        if (jobGradeList == null || jobGradeList.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<JobGradeResponseDto> values = jobGradeList.stream()
                                                   .filter(jobGrade -> jobGrade.getGrade() != null)
                                                   .map(jobGrade -> {
                                                       JobGradeDto dto = createDto(jobGrade);

                                                       // Defensively catch structural errors here
                                                       try {
                                                           return JobGradeResponseDto.from(dto);
                                                       } catch (Exception e) {
                                                           // Log the bad record but don't crash the server
                                                           log.error("Failed mapping job grade ID: {} - {}", jobGrade.getId(), e.getMessage());
                                                           return null;
                                                       }
                                                   })
                                                   .filter(Objects::nonNull) // Now this safely drops failed conversions
                                                   .toList();

        return ResponseEntity.ok(values);
    }

    @Nonnull
    private static JobGradeDto createDto(JobGrade jobGrade) {
        JobGradeDto dto = new JobGradeDto();
        dto.setId(jobGrade.getId());
        dto.setGrade(jobGrade.getGrade());
        dto.setDescription(jobGrade.getDescription());
        dto.setContractType(jobGrade.getContractType());
        dto.setIsBargainingCouncil(jobGrade.getIsBargainingCouncil());
        dto.setBargainingUnit(jobGrade.getBargainingUnit() == null
                ? null : jobGrade.getBargainingUnit().name());
        dto.setBargainingUnitCondition(jobGrade.getBargainingUnitCondition());
        return dto;
    }

}
