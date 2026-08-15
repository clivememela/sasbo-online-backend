package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberDto;
import za.co.titandynamix.sasbo_online_backend.dto.member.PaymentTypeMetadataDto;
import za.co.titandynamix.sasbo_online_backend.entity.Member;
import za.co.titandynamix.sasbo_online_backend.entity.PaymentType;
import za.co.titandynamix.sasbo_online_backend.mapper.MemberMapper;
import za.co.titandynamix.sasbo_online_backend.services.MemberService;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final MemberMapper memberMapper;

    @GetMapping
    public List<MemberDto> getAll() {
        return this.memberService.findAll().stream()
                       .map(this.memberMapper::toDto)
                       .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MemberDto> getById(@PathVariable Integer id) {
        return this.memberService.findById(id)
                       .map(member -> ResponseEntity.ok(this.memberMapper.toDto(member)))
                       .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public MemberDto create(@RequestBody Member member) {
        Member savedMember = this.memberService.create(member);
        return this.memberMapper.toDto(savedMember);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MemberDto> update(@PathVariable Integer id, @RequestBody Member member) {
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
                                                        .collect(Collectors.toList());

        return ResponseEntity.ok(metadata);
    }
}
