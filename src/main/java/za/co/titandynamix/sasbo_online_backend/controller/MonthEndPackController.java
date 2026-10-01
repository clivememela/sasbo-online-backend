package za.co.titandynamix.sasbo_online_backend.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndPackDto;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndPacks;

/**
 * The statistics pack, under {@code /api/month-end} (month end 04.10): asked for, polled, fetched. A bulk output is a
 * job -- {@code 202} while it is being prepared, the screen polling the pack until it is {@code READY} or
 * {@code FAILED} (ME-21). Open as the statistics are: counts only, rule C applied as the statistics apply it.
 */
@MonthEndApi
@RestController
@RequestMapping("/api/month-end")
public class MonthEndPackController {

    /** What a workbook is, to a browser. */
    static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final MonthEndPacks packs;

    public MonthEndPackController(MonthEndPacks packs) {
        this.packs = packs;
    }

    /** A pack of the month's statistics: {@code 202} where this ask started it, {@code 200} for one READY or in hand. */
    @PostMapping("/{month}/pack")
    public ResponseEntity<MonthEndPackDto> ask(@PathVariable("month") String month) {
        MonthEndPacks.Asked asked = packs.ask(month);
        return ResponseEntity.status(asked.started() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(asked.pack());
    }

    /** A pack, as the screen polls it. */
    @GetMapping("/packs/{id}")
    public MonthEndPackDto status(@PathVariable("id") long id) {
        return packs.status(id);
    }

    /** One workbook of a READY pack, as an {@code .xlsx} to save. */
    @GetMapping("/packs/{id}/{workbook}")
    public ResponseEntity<byte[]> file(@PathVariable("id") long id, @PathVariable("workbook") String workbook) {
        MonthEndPacks.File file = packs.file(id, workbook);
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.name()).build()
                        .toString())
                .body(file.bytes());
    }
}
