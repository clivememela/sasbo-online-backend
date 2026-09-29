package za.co.titandynamix.sasbo_online_backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceListAnswer;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceRows;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceStatusDto;
import za.co.titandynamix.sasbo_online_backend.services.ReferenceListService;

/**
 * SASBO's lists, one typed route a list (month end 01.1, MD41, MR-SEC-11) -- and the status read that says for each
 * list, and for the workplaces, how many rows are answered here and why none is where none is (ME-18, ME-19).
 *
 * <p>NO NAME OF A LIST ARRIVES IN AN ADDRESS: each list has a route and a shape of its own, as every reference read of
 * the application has. OPEN, as those are (MD4): a list of organisations and codes names nobody, and holds no count
 * of members. Each answer carries the day the list's report was printed and the day SASBO confirmed it (MD28).
 * Every list is bounded by nature -- the longest is the 137 employers -- and each answer is a list inside an envelope,
 * so no route returns a bare collection.
 */
@MonthEndApi
@RestController
@RequestMapping("/api/reference")
public class ReferenceListController {

    private final ReferenceListService lists;

    public ReferenceListController(ReferenceListService lists) {
        this.lists = lists;
    }

    @GetMapping("/regions")
    public ReferenceListAnswer<ReferenceRows.Region> regions() {
        return lists.regions();
    }

    @GetMapping("/areas")
    public ReferenceListAnswer<ReferenceRows.Area> areas() {
        return lists.areas();
    }

    @GetMapping("/employer-groups")
    public ReferenceListAnswer<ReferenceRows.EmployerGroup> employerGroups() {
        return lists.employerGroups();
    }

    @GetMapping("/employers")
    public ReferenceListAnswer<ReferenceRows.Employer> employers() {
        return lists.employers();
    }

    @GetMapping("/table-codes")
    public ReferenceListAnswer<ReferenceRows.TableCode> tableCodes() {
        return lists.tableCodes();
    }

    @GetMapping("/committee-provinces")
    public ReferenceListAnswer<ReferenceRows.CommitteeProvince> committeeProvinces() {
        return lists.committeeProvinces();
    }

    @GetMapping("/committees")
    public ReferenceListAnswer<ReferenceRows.Committee> committees() {
        return lists.committees();
    }

    @GetMapping("/suspension-codes")
    public ReferenceListAnswer<ReferenceRows.SuspensionCode> suspensionCodes() {
        return lists.suspensionCodes();
    }

    /** 03.3: by SASBO's numbers -- the one read of the deletion codes that says which number is SASBO's (RC7). */
    @GetMapping("/deletion-codes")
    public ReferenceListAnswer<ReferenceRows.DeletionCode> deletionCodes() {
        return lists.deletionCodes();
    }

    @GetMapping("/status")
    public ReferenceStatusDto status() {
        return lists.status();
    }
}
