package za.co.titandynamix.sasbo_online_backend.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndDeliveryDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndFilesDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndProducedFileDto;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndSentFiles;

import java.io.IOException;

/**
 * The files that leave SASBO for the banks and the providers, under {@code /api/month-end} (October build; Stephne
 * MEN-05, MEN-32 to MEN-35): every one password-protected, its password in the answer that produced it and nowhere
 * else. Every route needs MONTH_END_SEND and a caller who sees every region: these files carry every region's members.
 */
@MonthEndApi
@RestController
@RequestMapping("/api/month-end")
public class MonthEndFilesController {

    private final MonthEndSentFiles files;

    public MonthEndFilesController(MonthEndSentFiles files) {
        this.files = files;
    }

    /** The month's banks and providers: what a file made now would hold, and the files made. */
    @PreAuthorize("hasAuthority('MONTH_END_SEND')")
    @GetMapping("/{month}/files")
    public MonthEndFilesDto list(@PathVariable("month") String month) {
        return files.list(month);
    }

    /** A bank's file, made now and encrypted -- and its password, shown once. Never cached. */
    @PreAuthorize("hasAuthority('MONTH_END_SEND')")
    @PostMapping("/{month}/files/banks/{code}")
    public ResponseEntity<MonthEndProducedFileDto> produceBank(@PathVariable("month") String month,
                                                               @PathVariable("code") String code) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(files.produceBank(month, code));
    }

    /** A provider's list, made now and encrypted -- and its password, shown once. Never cached. */
    @PreAuthorize("hasAuthority('MONTH_END_SEND')")
    @PostMapping("/{month}/files/providers/{code}")
    public ResponseEntity<MonthEndProducedFileDto> produceProvider(@PathVariable("month") String month,
                                                                   @PathVariable("code") String code) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(files.produceProvider(month, code));
    }

    /** The encrypted file, written to the response as the database hands it over. */
    @PreAuthorize("hasAuthority('MONTH_END_SEND')")
    @GetMapping("/files/{id}")
    public void download(@PathVariable("id") long id, HttpServletResponse response) throws IOException {
        MonthEndRegionPackController.write(files.download(id), response);
    }

    /** A provider's file put onto its SFTP server, where one is set up. */
    @PreAuthorize("hasAuthority('MONTH_END_SEND')")
    @PostMapping("/files/{id}/sftp")
    public MonthEndDeliveryDto upload(@PathVariable("id") long id) {
        return files.upload(id);
    }
}
