package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberDto;
import za.co.titandynamix.sasbo_online_backend.dto.member.PaymentTypeMetadataDto;
import za.co.titandynamix.sasbo_online_backend.dto.member.SubscriptionUpdateRequest;
import za.co.titandynamix.sasbo_online_backend.dto.member.WelcomeLetterMetadataDto;
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeRoleDto;
import za.co.titandynamix.sasbo_online_backend.dto.representative.UnionStructureDto;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.enums.PaymentType;
import za.co.titandynamix.sasbo_online_backend.mapper.MemberMapper;
import za.co.titandynamix.sasbo_online_backend.services.EmailService;
import za.co.titandynamix.sasbo_online_backend.services.MemberService;
import za.co.titandynamix.sasbo_online_backend.services.MemberWorkplaces;
import za.co.titandynamix.sasbo_online_backend.services.RepresentativeService;
import za.co.titandynamix.sasbo_online_backend.services.impl.WelcomeLetterDispatcher;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final MemberMapper memberMapper;
    private final RepresentativeService representativeService;
    private final WelcomeLetterDispatcher welcomeLetters;
    private final TransactionOperations transactions;
    /** Month end 01.3: each answer names the member's workplace as the deployment's switch allows (MD62). */
    private final MemberWorkplaces workplaces;
    @Autowired(required = false)
    private EmailService emailService;

    @GetMapping
    @Transactional(readOnly = true)
    public List<MemberDto> getAll() {
        return this.memberService.findAll().stream()
                       .map(this::inAList)
                       .toList();
    }

    /** A member in the list: the workplace as three scalars, as the switch allows (month end 01.3, MD62). */
    private MemberDto inAList(Member member) {
        MemberDto answer = this.memberMapper.toDto(member);
        this.workplaces.inAList(answer);
        return answer;
    }

    /** The answer for one member: the workplace as the switch allows, and the workplace's lineage (01.3). */
    private MemberDto forOne(Member member) {
        MemberDto answer = this.memberMapper.toDto(member);
        this.workplaces.forOne(answer);
        return answer;
    }

    @GetMapping("/{id}")
    public ResponseEntity<MemberDto> getById(@PathVariable Integer id) {
        return this.memberService.findById(id)
                       .map(member -> ResponseEntity.ok(this.forOne(member)))
                       .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Captures a member, and -- when asked -- then attempts their welcome letter.
     *
     * <p>🔴 A64: THE LETTER IS NO LONGER PART OF THE CAPTURE. This answered 503 before saving wherever
     * mail was not configured, and sent inside the capture's own {@code @Transactional}, so a failed
     * send rolled the member back. The capture now runs in its own transaction and COMMITS; only then
     * is the letter attempted, and what happened to it comes back as {@code welcomeLetter} beside the
     * saved member. Nothing about a letter can refuse or undo a capture any more.
     *
     * <p>The transaction is programmatic rather than {@code @Transactional} because its boundary must
     * end INSIDE this method: the member is mapped while its lazy associations can still load
     * ({@code open-in-view} is off), and the letter goes out after the commit.
     */
    @PostMapping
    public ResponseEntity<MemberDto> create(
            @RequestBody Member member,
            @RequestParam(name = "sendWelcome", required = false, defaultValue = "false") boolean sendWelcome) {
        Captured captured = this.transactions.execute(status -> capture(member));

        if (sendWelcome) {
            captured.body().setWelcomeLetter(this.welcomeLetters.sendAfterCapture(captured.member()));
        }
        return ResponseEntity.ok(captured.body());
    }

    /** The saved member, and the answer mapped from it while its transaction was still open. */
    private record Captured(Member member, MemberDto body) {
    }

    private Captured capture(Member member) {
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
        return new Captured(savedMember, this.forOne(savedMember));
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
                       .map(updated -> ResponseEntity.ok(this.forOne(updated)))
                       .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/banking")
    public ResponseEntity<MemberDto> updateBanking(@PathVariable Integer id, @RequestBody Member member) {
        return this.memberService.updateBanking(id, member)
                       .map(updated -> ResponseEntity.ok(this.forOne(updated)))
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
                       .map(updated -> ResponseEntity.ok(this.forOne(updated)))
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

    /**
     * Whether a capture's welcome letter is emailed here (A64). The capture screen reads it to say so
     * BEFORE Send is pressed: where mail is not configured the member is still saved, and the clerk
     * must not be told that a letter is on its way.
     */
    @GetMapping("/metadata/welcome-letter")
    public WelcomeLetterMetadataDto getWelcomeLetterMetadata() {
        return new WelcomeLetterMetadataDto(this.welcomeLetters.emailEnabled());
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
