package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.entity.Member;
import za.co.titandynamix.sasbo_online_backend.services.MemberService;

import java.util.List;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    public List<Member> getAll() {
        return this.memberService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Member> getById(@PathVariable Integer id) {
        return this.memberService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Member> create(@RequestBody Member member) {
        return ResponseEntity.ok(this.memberService.create(member));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Member> update(@PathVariable Integer id, @RequestBody Member updated) {
        return this.memberService.update(id, updated)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/banking")
    public ResponseEntity<Member> updateBanking(@PathVariable Integer id, @RequestBody Member banking) {
        return this.memberService.updateBanking(id, banking)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
