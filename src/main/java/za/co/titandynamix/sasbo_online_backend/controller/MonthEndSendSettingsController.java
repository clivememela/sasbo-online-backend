package za.co.titandynamix.sasbo_online_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndBankDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndBankRequest;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndPackSetDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndPackSetRequest;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndProviderDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndProviderRequest;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndSendSettingsDto;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndSendSettings;

/**
 * Who gets what at month end, under {@code /api/month-end/settings} (October build): the recipients of the regions'
 * packs, each bank's file and each provider's list -- data, edited by a holder of MONTH_END_SEND (contracts/
 * permissions.md). Every save names the version the screen read.
 */
@MonthEndApi
@RestController
@RequestMapping("/api/month-end/settings")
public class MonthEndSendSettingsController {

    private final MonthEndSendSettings settings;

    public MonthEndSendSettingsController(MonthEndSendSettings settings) {
        this.settings = settings;
    }

    /** The recipients in the caller's scope; the banks and providers for a caller who sees every region. */
    @PreAuthorize("hasAuthority('MONTH_END_SEND')")
    @GetMapping
    public MonthEndSendSettingsDto settings() {
        return settings.settings();
    }

    /** A new recipient of a region's pack. */
    @PreAuthorize("hasAuthority('MONTH_END_SEND')")
    @PostMapping("/recipients")
    public ResponseEntity<MonthEndPackSetDto> createRecipient(@RequestBody(required = false) MonthEndPackSetRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(settings.createSet(body));
    }

    /** A recipient changed. */
    @PreAuthorize("hasAuthority('MONTH_END_SEND')")
    @PutMapping("/recipients/{code}")
    public MonthEndPackSetDto updateRecipient(@PathVariable("code") String code,
                                              @RequestBody(required = false) MonthEndPackSetRequest body) {
        return settings.updateSet(code, body);
    }

    /** A new bank's monthly file. */
    @PreAuthorize("hasAuthority('MONTH_END_SEND')")
    @PostMapping("/banks")
    public ResponseEntity<MonthEndBankDto> createBank(@RequestBody(required = false) MonthEndBankRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(settings.createBank(body));
    }

    /** A bank's file changed. */
    @PreAuthorize("hasAuthority('MONTH_END_SEND')")
    @PutMapping("/banks/{code}")
    public MonthEndBankDto updateBank(@PathVariable("code") String code,
                                      @RequestBody(required = false) MonthEndBankRequest body) {
        return settings.updateBank(code, body);
    }

    /** A provider switched on or off. */
    @PreAuthorize("hasAuthority('MONTH_END_SEND')")
    @PutMapping("/providers/{code}")
    public MonthEndProviderDto updateProvider(@PathVariable("code") String code,
                                              @RequestBody(required = false) MonthEndProviderRequest body) {
        return settings.updateProvider(code, body);
    }
}
