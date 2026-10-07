package za.co.titandynamix.sasbo_online_backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.dto.reference.CoreTierRequest;
import za.co.titandynamix.sasbo_online_backend.dto.reference.CoreTiersDto;
import za.co.titandynamix.sasbo_online_backend.dto.reference.TableCodeTierDto;
import za.co.titandynamix.sasbo_online_backend.services.CoreTiers;

/**
 * Core A, B and C (Stephne, MEN-31), under {@code /api/reference}: the tiers and their table codes, read by anyone --
 * organisations and codes -- and a table code's tier, set by an administrator who maintains SASBO's lists.
 */
@MonthEndApi
@RestController
@RequestMapping("/api/reference")
public class CoreTierController {

    private final CoreTiers tiers;

    public CoreTierController(CoreTiers tiers) {
        this.tiers = tiers;
    }

    @GetMapping("/core-tiers")
    public CoreTiersDto list() {
        return tiers.list();
    }

    @PreAuthorize("hasAuthority('REFERENCE_MAINTAIN')")
    @PutMapping("/table-codes/{code}/core-tier")
    public TableCodeTierDto setTier(@PathVariable("code") Integer code, @RequestBody CoreTierRequest request) {
        return tiers.setTier(code, request == null ? null : request.tier());
    }
}
