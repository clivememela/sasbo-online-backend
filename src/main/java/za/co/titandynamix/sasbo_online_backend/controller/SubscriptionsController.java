package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.dto.subscription.SubscriptionsDto;
import za.co.titandynamix.sasbo_online_backend.services.SubscriptionsService;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionsController {

    private final SubscriptionsService subscriptionsService;

    @GetMapping
    public ResponseEntity<List<SubscriptionsDto>> getAll(@RequestParam(value = "bankId", required = false) Long bankId) {
        Long effectiveBankId = (bankId == null || bankId <= 0) ? null : bankId;
        List<SubscriptionsDto> subscriptions = this.subscriptionsService.findAll(effectiveBankId);
        return ResponseEntity.ok(subscriptions);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionsDto> getById(@PathVariable Long id) {
        return this.subscriptionsService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SubscriptionsDto> create(@RequestBody SubscriptionsDto dto) {
        SubscriptionsDto created = this.subscriptionsService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SubscriptionsDto> update(@PathVariable Long id, @RequestBody SubscriptionsDto dto) {
        return this.subscriptionsService.update(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boolean deleted = this.subscriptionsService.delete(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
