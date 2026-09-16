package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.dto.member.JobGradeDto;
import za.co.titandynamix.sasbo_online_backend.dto.member.JobGradeResponseDto;
import za.co.titandynamix.sasbo_online_backend.entity.member.JobGrade;
import za.co.titandynamix.sasbo_online_backend.services.impl.JobGradeServiceImpl;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/job-grades")
@RequiredArgsConstructor
public class JobGradeController {
    private final JobGradeServiceImpl jobGradeService;

    @GetMapping
    public ResponseEntity<List<JobGradeResponseDto>> getByBank(
            @RequestParam(name = "bankCode", required = false) String bankCode,
            @RequestParam(name = "bank", required = false) String bank) {

        String identifier = (bankCode != null && !bankCode.isBlank()) ? bankCode : bank;

        List<JobGrade> jobGradeList = jobGradeService.findByBankIdentifier(identifier);

        if (jobGradeList == null || jobGradeList.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<JobGradeResponseDto> values = jobGradeList.stream()
                                                   .filter(jobGrade -> jobGrade.getGrade() != null)
                                                   .map(jobGrade -> {
                                                       JobGradeDto dto = new JobGradeDto();
                                                       dto.setId(jobGrade.getId());
                                                       dto.setGrade(jobGrade.getGrade());
                                                       dto.setDescription(jobGrade.getDescription());
                                                       dto.setContractType(jobGrade.getContractType());
                                                       dto.setIsBargainingCouncil(jobGrade.getIsBargainingCouncil());

                                                       // Defensively catch structural errors here
                                                       try {
                                                           return JobGradeResponseDto.from(dto);
                                                       } catch (Exception e) {
                                                           // Log the bad record but don't crash the server
                                                           System.err.println("Failed mapping job grade ID: " + jobGrade.getId() + " - " + e.getMessage());
                                                           return null;
                                                       }
                                                   })
                                                   .filter(response -> response != null) // Now this safely drops failed conversions
                                                   .collect(Collectors.toList());

        return ResponseEntity.ok(values);
    }

}
