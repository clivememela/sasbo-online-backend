package za.co.titandynamix.sasbo_online_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.dto.subscription.SubscriptionsDto;
import za.co.titandynamix.sasbo_online_backend.services.SubscriptionsService;

import java.util.List;

/**
 * The subscription rows: SASBO's fee, and each bank's ACB day.
 *
 * <p>🔒 The three writes need {@code SUBSCRIPTIONS_MAINTAIN}: only staff change amounts (the 5 Oct Show &amp; Tell,
 * which also moved Subscriptions under Maintenance). While sign-in is open an anonymous caller is refused them, so the
 * demonstration link can read the rows but not change an amount until SASBO has accounts. The reads stay open: the
 * capture form shows the fee and the bank's debit day from them.
 */
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

    /** A23: the route the browser's create now reaches -- a row it cannot use is refused by field, before a write. */
    @PreAuthorize("hasAuthority('SUBSCRIPTIONS_MAINTAIN')")
    @PostMapping
    public ResponseEntity<SubscriptionsDto> create(@Valid @RequestBody SubscriptionsDto dto) {
        SubscriptionsDto created = this.subscriptionsService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * A changed row is held to the same rules as a new one. 🔴 The screen sent an emptied debit day as {@code null},
     * which the primitive took as 0 and saved: the bank's ACB day read "NaN" on the screen and 0 on record.
     */
    @PreAuthorize("hasAuthority('SUBSCRIPTIONS_MAINTAIN')")
    @PutMapping("/{id}")
    public ResponseEntity<SubscriptionsDto> update(@PathVariable Long id, @Valid @RequestBody SubscriptionsDto dto) {
        return this.subscriptionsService.update(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('SUBSCRIPTIONS_MAINTAIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boolean deleted = this.subscriptionsService.delete(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
