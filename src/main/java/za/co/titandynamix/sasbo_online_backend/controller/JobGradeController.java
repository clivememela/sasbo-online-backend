package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.entity.member.JobGrade;
import za.co.titandynamix.sasbo_online_backend.services.JobGradeService;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/job-grades")
@RequiredArgsConstructor
public class JobGradeController {
    private final JobGradeService service;

    @GetMapping
    public ResponseEntity<List<String>> getByBank(@RequestParam(name = "bankCode", required = false) String bankCode,
                                                  @RequestParam(name = "bank", required = false) String bank) {
        String identifier = (bankCode != null && !bankCode.isBlank()) ? bankCode : bank;
        try {
            List<JobGrade> grades = service.findByBankIdentifier(identifier);
            List<String> values = grades.stream().map(jg -> jg.getGrade()).collect(Collectors.toList());
            return ResponseEntity.ok(values);
        } catch (Exception e) {
            // Return 500 intentionally if something unexpected happens; include message for easier debugging
            return ResponseEntity.status(500).body(List.of("ERROR: " + e.getMessage()));
        }
    }
}

