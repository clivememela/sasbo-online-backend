package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberDto;
import za.co.titandynamix.sasbo_online_backend.dto.member.PaymentTypeMetadataDto;
import za.co.titandynamix.sasbo_online_backend.dto.member.SubscriptionUpdateRequest;
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeRoleDto;
import za.co.titandynamix.sasbo_online_backend.dto.representative.UnionStructureDto;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.enums.PaymentType;
import za.co.titandynamix.sasbo_online_backend.mapper.MemberMapper;
import za.co.titandynamix.sasbo_online_backend.services.EmailService;
import za.co.titandynamix.sasbo_online_backend.services.MemberService;
import za.co.titandynamix.sasbo_online_backend.services.RepresentativeService;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final MemberMapper memberMapper;
    private final RepresentativeService representativeService;
    @Autowired(required = false)
    private EmailService emailService;

    @GetMapping
    @Transactional(readOnly = true)
    public List<MemberDto> getAll() {
        return this.memberService.findAll().stream()
                       .map(this.memberMapper::toDto)
                       .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<MemberDto> getById(@PathVariable Integer id) {
        return this.memberService.findById(id)
                       .map(member -> ResponseEntity.ok(this.memberMapper.toDto(member)))
                       .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<MemberDto> create(
            @RequestBody Member member,
            @RequestParam(name = "sendWelcome", required = false, defaultValue = "false") boolean sendWelcome) {
        if (sendWelcome && this.emailService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        // The user-id scrub that stood here is gone because it is now unreachable:
        // Member.user is @JsonProperty(access = READ_ONLY), so Jackson -- the only
        // thing that constructs this parameter -- never populates it. Leaving it
        // would read as a live security control and imply that binding a user is
        // possible-but-mitigated. The annotation is the actual guard.
        // Resolve structure and role first before validation
        this.memberService.resolveStructureAndRole(member);

        // Enforce rule: Non-members cannot become representatives
        // Business logic validation: Ensure non-members cannot be saved as pure representatives
        // Since they are written into the member table, saving them naturally satisfies "rep is member by default"
        if (Boolean.TRUE.equals(member.getIsRepresentative()) && member.getRole() != null) {
            // Representative is a member by default, handled by saving to this table
            var memberRole = member.getRole();
            if (Boolean.TRUE.equals(memberRole.getRequiresStructure()) && member.getStructure() == null)
            {
                throw new IllegalArgumentException("Error: Selected role requires an assigned structure.");
            }
            // §4.3 — Election date is mandatory for elected roles
            if (Boolean.TRUE.equals(memberRole.getIsElectedRole()) && member.getElectionDate() == null)
            {
                throw new IllegalArgumentException("Error: Selected role is an elected position and requires an election date.");
            }
        }

        Member savedMember = this.memberService.create(member);

        if (sendWelcome) {
            this.emailService.sendWelcomeLetter(savedMember);
        }

        return ResponseEntity.ok(this.memberMapper.toDto(savedMember));
    }

    @GetMapping("/userRoles")
    public List<RepresentativeRoleDto> getAllRepresentativeRoles() {
        return this.representativeService.findAllRepresentativeRoles();
    }

    @GetMapping("/structures")
    public List<UnionStructureDto> getAllStructures() {
        return this.representativeService.findAllStructures();
    }

    /**
     * Returns the position/structure hierarchy as a tree.
     * Each node includes its children, enabling cascading dropdown selections on the frontend.
     */
    @GetMapping("/structures/tree")
    public List<UnionStructureDto> getStructuresTree() {
        return this.representativeService.findStructuresTree();
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<MemberDto> update(@PathVariable Integer id, @RequestBody Member member) {
        // Resolve structure and role first before validation
        this.memberService.resolveStructureAndRole(member);

        // §4.3 — Validate representative role requirements on update (mirrors create logic)
        if (Boolean.TRUE.equals(member.getIsRepresentative()) && member.getRole() != null) {
            var memberRole = member.getRole();
            if (Boolean.TRUE.equals(memberRole.getRequiresStructure()) && member.getStructure() == null) {
                throw new IllegalArgumentException("Error: Selected role requires an assigned structure.");
            }
            if (Boolean.TRUE.equals(memberRole.getIsElectedRole()) && member.getElectionDate() == null) {
                throw new IllegalArgumentException("Error: Selected role is an elected position and requires an election date.");
            }
        }
        return this.memberService.update(id, member)
                       .map(updated -> ResponseEntity.ok(this.memberMapper.toDto(updated)))
                       .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/banking")
    public ResponseEntity<MemberDto> updateBanking(@PathVariable Integer id, @RequestBody Member member) {
        return this.memberService.updateBanking(id, member)
                       .map(updated -> ResponseEntity.ok(this.memberMapper.toDto(updated)))
                       .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Maintenance endpoint for updating the monthly subscription value.
     * The subscription is read-only during member capture (auto-populated
     * from the Subscriptions reference table by bank) and is only changed
     * annually through this maintenance endpoint.
     */
    @PatchMapping("/{id}/subscription")
    public ResponseEntity<MemberDto> updateSubscription(
            @PathVariable Integer id,
            @RequestBody SubscriptionUpdateRequest request) {
        return this.memberService.updateMonthlySubscription(id, request.getMonthlySubscription())
                       .map(updated -> ResponseEntity.ok(this.memberMapper.toDto(updated)))
                       .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/send-welcome")
    public ResponseEntity<Void> sendWelcomeLetter(@PathVariable Integer id) {
        if (this.emailService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return this.memberService.findById(id)
                       .map(member -> {
                           this.emailService.sendWelcomeLetter(member);
                           return ResponseEntity.ok().<Void>build();
                       })
                       .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/welcome-letter-preview", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> postWelcomeLetterPreview(@RequestBody Member member) {
        if (this.emailService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        byte[] preview = this.emailService.buildWelcomeLetterPdf(member);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline().filename("Sasbo Welcome Letter.pdf").build());
        return ResponseEntity.ok().headers(headers).body(preview);
    }

    @GetMapping(value = "/{id}/welcome-letter-preview", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getWelcomeLetterPreview(@PathVariable Integer id) {
        if (this.emailService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return this.memberService.findById(id)
                       .map(member -> {
                           byte[] preview = this.emailService.buildWelcomeLetterPdf(member);
                           HttpHeaders headers = new HttpHeaders();
                           headers.setContentType(MediaType.APPLICATION_PDF);
                           headers.setContentDisposition(ContentDisposition.inline()
                                                                 .filename("Sasbo Welcome Letter.pdf")
                                                                 .build());
                           return ResponseEntity.ok().headers(headers).body(preview);
                       })
                       .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/metadata/payment-types")
    public ResponseEntity<List<PaymentTypeMetadataDto>> getPaymentTypeMetadata() {
        List<PaymentTypeMetadataDto> metadata = Arrays.stream(PaymentType.values())
                                                        .map(type -> {
                                                            PaymentTypeMetadataDto dto = new PaymentTypeMetadataDto();
                                                            dto.setName(type.name());
                                                            dto.setDisplayName(type.getDisplayName());
                                                            dto.setRequiresBankAccount(type.requiresBankAccount());
                                                            return dto;
                                                        })
                                                        .toList();

        return ResponseEntity.ok(metadata);
    }
}
