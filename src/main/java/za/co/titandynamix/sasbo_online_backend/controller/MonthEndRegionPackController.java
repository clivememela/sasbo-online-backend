package za.co.titandynamix.sasbo_online_backend.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndBulkEmailDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndBulkEmailRequest;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndDeliveriesPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndDeliveryDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndEmailRequest;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndProduceRequest;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndRegionPackDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndRegionPacksDto;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndRegionPacks;

import java.io.IOException;

/**
 * The regions' packs, under {@code /api/month-end} (October build; Stephne MEN-02, MEN-04, MEN-05, MEN-08): the month's
 * packs a recipient at a time, made, downloaded or e-mailed -- chosen each month -- and the record of what was sent. Every
 * route needs MONTH_END_SEND, and a region's user sees their own region's packs alone (contracts/permissions.md).
 */
@MonthEndApi
@RestController
@RequestMapping("/api/month-end")
public class MonthEndRegionPackController {

    /** A file's SHA-256, for a reader to check what they saved against the record (exposed cross-origin by base). */
    static final String SHA256_HEADER = "X-File-Sha256";

    private final MonthEndRegionPacks packs;

    public MonthEndRegionPackController(MonthEndRegionPacks packs) {
        this.packs = packs;
    }

    /** The month's packs: a row a recipient, its newest pack and that pack's deliveries. */
    @PreAuthorize("hasAuthority('MONTH_END_SEND')")
    @GetMapping("/{month}/region-packs")
    public MonthEndRegionPacksDto list(@PathVariable("month") String month) {
        return packs.list(month);
    }

    /** A recipient's pack: {@code 201} where this ask made it, {@code 200} for the one made already. */
    @PreAuthorize("hasAuthority('MONTH_END_SEND')")
    @PostMapping("/{month}/region-packs/{set}")
    public ResponseEntity<MonthEndRegionPackDto> produce(@PathVariable("month") String month,
                                                         @PathVariable("set") String set,
                                                         @RequestBody(required = false) MonthEndProduceRequest body) {
        MonthEndRegionPacks.Produced produced = packs.produce(month, set, body != null
                && Boolean.TRUE.equals(body.again()));
        return ResponseEntity.status(produced.created() ? HttpStatus.CREATED : HttpStatus.OK).body(produced.pack());
    }

    /** The month's packs e-mailed, a recipient at a time -- a result each, never a failure of the whole. */
    @PreAuthorize("hasAuthority('MONTH_END_SEND')")
    @PostMapping("/{month}/region-packs/send")
    public MonthEndBulkEmailDto send(@PathVariable("month") String month,
                                     @RequestBody(required = false) MonthEndBulkEmailRequest body) {
        return packs.emailAll(month, body == null ? null : body.sets(), body == null ? null : body.formats());
    }

    /** One of a pack's files, {@code pdf} or {@code xlsx}, written to the response as the database hands it over. */
    @PreAuthorize("hasAuthority('MONTH_END_SEND')")
    @GetMapping("/region-packs/{id}/{format}")
    public void download(@PathVariable("id") long id, @PathVariable("format") String format,
                         HttpServletResponse response) throws IOException {
        write(packs.download(id, format), response);
    }

    /** A pack e-mailed to its recipient, in the formats chosen. */
    @PreAuthorize("hasAuthority('MONTH_END_SEND')")
    @PostMapping("/region-packs/{id}/email")
    public MonthEndDeliveryDto email(@PathVariable("id") long id,
                                     @RequestBody(required = false) MonthEndEmailRequest body) {
        return packs.email(id, body == null ? null : body.formats());
    }

    /** The month's record of what was sent to whom, the newest first. */
    @PreAuthorize("hasAuthority('MONTH_END_SEND')")
    @GetMapping("/{month}/deliveries")
    public MonthEndDeliveriesPageDto deliveries(@PathVariable("month") String month,
                                                @RequestParam(value = "page", defaultValue = "0") int page,
                                                @RequestParam(value = "size", defaultValue = "50") int size) {
        return packs.deliveries(month, page, size);
    }

    /** A file to the response: its type, its name to save it by, its size and hash -- and never cached. */
    static void write(MonthEndRegionPacks.Download file, HttpServletResponse response) throws IOException {
        response.setContentType(file.mediaType());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName())
                .build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(SHA256_HEADER, file.sha256());
        response.setContentLengthLong(file.bytes());
        file.body().accept(response.getOutputStream());
    }
}
