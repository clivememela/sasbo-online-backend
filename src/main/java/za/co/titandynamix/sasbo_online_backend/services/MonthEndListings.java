package za.co.titandynamix.sasbo_online_backend.services;

import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndListingDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndMonthDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository.TakenMonth;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndListingRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndListingRepository.Kind;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndListingRepository.Listed;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * A month's listings (month end 04.3, 04.7, 04.8) -- held until sign-in, because each names members (MD4), and opened
 * by {@code MEMBERS_VIEW} with sign-in (October build): the month's new members, its deleted members, the members a
 * recruiter signed up, the elected representatives.
 *
 * <ul>
 *   <li>only ever within the caller's scope ({@link CallerScope#current()}), filtered in SQL -- a Regional Secretary
 *       reads their region, an organiser their areas, and a member with no workplace is listed only for everything;</li>
 *   <li>a page at a time, at most {@value #MAX_SIZE};</li>
 *   <li>the month as the statistics take it: a taken month or the open month; a past month not taken is
 *       {@code MONTH_NOT_TAKEN};</li>
 *   <li>each page read is a line in the record of who did what -- who took members' details out (USR-38).</li>
 * </ul>
 */
@Service
public class MonthEndListings {

    static final int DEFAULT_SIZE = 50;
    static final int MAX_SIZE = 200;

    /** A listing: what it reads, its plain name (the client's), and the legacy report it answers. */
    record Definition(Kind kind, String title, String legacy) {
    }

    /** The four, by their route's word -- the client's {@code LISTINGS} keys. */
    static final Map<String, Definition> LISTINGS = Map.of(
            "new-members", new Definition(Kind.NEW_MEMBERS, "The month's new members",
                    "Print Member Roll (No Adding) -- the members enrolled"),
            "deleted-members", new Definition(Kind.DELETED_MEMBERS, "The month's deleted members",
                    "Print Member Roll (No Adding) -- the members deleted"),
            "recruited", new Definition(Kind.RECRUITED, "The members a recruiter signed up", "Print Recruiting Analyst"),
            "representatives", new Definition(Kind.REPRESENTATIVES, "The elected representatives",
                    "Print Shop Steward List"));

    private static final Pattern CODE = Pattern.compile("[A-Z0-9]+");

    private final MonthEndTakes takes;
    private final MonthEndFactsRepository facts;
    private final MonthEndListingRepository listings;
    private final AuditTrail audit;

    public MonthEndListings(MonthEndTakes takes, MonthEndFactsRepository facts, MonthEndListingRepository listings,
                            AuditTrail audit) {
        this.takes = takes;
        this.facts = facts;
        this.listings = listings;
        this.audit = audit;
    }

    /**
     * One page of a listing, within the caller's scope.
     *
     * @throws MonthEndRefusal {@code REQUEST_MALFORMED} for a listing that is none of the four, a page or size out of
     *                         range, or an area and a region both; {@code MONTH_NOT_BEGUN}; {@code MONTH_NOT_TAKEN};
     *                         {@code SUMMARY_AREA_UNKNOWN}
     */
    public MonthEndListingDto listing(String month, String listing, String area, String region, Integer page,
                                      Integer size) {
        Definition definition = LISTINGS.get(listing);
        if (definition == null) {
            throw new MonthEndRefusal(MonthEndRefusalCode.REQUEST_MALFORMED, "That is not one of the month's four "
                    + "listings.", MonthEndRefusal.details("field", "listing",
                    "listings", String.join(",", new TreeSet<>(LISTINGS.keySet()))));
        }
        String areaCode = code("area", area, MonthEndStatistics.AREA_CEILING);
        String regionCode = code("region", region, MonthEndStatistics.REGION_CEILING);
        if (areaCode != null && regionCode != null) {
            throw new MonthEndRefusal(MonthEndRefusalCode.REQUEST_MALFORMED, "Ask for an area or a region, not both.",
                    Map.of("field", "region"));
        }
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNumber < 0 || pageSize < 1 || pageSize > MAX_SIZE) {
            throw new MonthEndRefusal(MonthEndRefusalCode.REQUEST_MALFORMED, "That page cannot be read: pages are counted "
                    + "from 0 and hold 1 to " + MAX_SIZE + " members.", MonthEndRefusal.details(
                    "field", pageNumber < 0 ? "page" : "size", "maxSize", String.valueOf(MAX_SIZE)));
        }
        YearMonth asked = takes.month(month);
        Optional<TakenMonth> taken = takes.taken(asked);
        YearMonth open = takes.openMonth();
        if (taken.isEmpty() && !asked.equals(open)) {
            throw new MonthEndRefusal(MonthEndRefusalCode.MONTH_NOT_TAKEN,
                    "That month was not taken, and a past month is read only from what was taken.",
                    MonthEndRefusal.details("month", asked.toString(), "open", open.toString()));
        }
        if (areaCode != null && !facts.areaOnRecord(areaCode)) {
            throw new MonthEndRefusal(MonthEndRefusalCode.SUMMARY_AREA_UNKNOWN, "That area is not on record here.",
                    Map.of("field", "area"));
        }
        if (regionCode != null && !facts.regionOnRecord(regionCode)) {
            throw new MonthEndRefusal(MonthEndRefusalCode.SUMMARY_AREA_UNKNOWN, "That region is not on record here.",
                    Map.of("field", "region"));
        }

        CallerScope scope = CallerScope.current();
        MonthRange range = MonthRange.of(asked);
        long total = listings.count(definition.kind(), range, scope, areaCode, regionCode);
        List<MonthEndListingDto.Row> rows = listings.page(definition.kind(), range, scope, areaCode, regionCode,
                Math.multiplyExact(pageNumber, pageSize), pageSize).stream().map(MonthEndListings::row).toList();
        int totalPages = (int) ((total + pageSize - 1) / pageSize);
        audit.record("MONTH_END_LISTING_READ", "MONTH_END", asked.toString(), definition.title() + ", page "
                + (pageNumber + 1) + " of " + Math.max(totalPages, 1) + ": " + rows.size() + " of " + total
                + " member(s), scope " + describe(scope) + (areaCode == null ? "" : ", area " + areaCode)
                + (regionCode == null ? "" : ", region " + regionCode) + ".");
        return new MonthEndListingDto(listing, definition.title(), definition.legacy(), asked.toString(),
                taken.isPresent() ? MonthEndMonthDto.TAKEN : MonthEndMonthDto.OPEN, scope.type().name(), areaCode,
                regionCode, rows, pageNumber, pageSize, total, totalPages, notes(definition.kind(), taken.isPresent(),
                scope));
    }

    private static MonthEndListingDto.Row row(Listed listed) {
        String date = listed.left() != null ? listed.left().atZone(MonthRange.ZONE).toLocalDate().toString()
                : listed.joined() == null ? null : listed.joined().toString();
        String role = listed.shopSteward() && listed.committeeMember() ? "Shop steward and BEC member"
                : listed.shopSteward() ? "Shop steward" : listed.committeeMember() ? "BEC member" : null;
        return new MonthEndListingDto.Row(listed.membershipNumber(), listed.initials(), listed.surname(), date,
                listed.code(), listed.reason(), listed.raNumber(), listed.workplace(), listed.area(), listed.region(),
                listed.recruiter(), role);
    }

    private static List<String> notes(Kind kind, boolean taken, CallerScope scope) {
        List<String> notes = new ArrayList<>();
        notes.add(switch (kind) {
            case NEW_MEMBERS -> "A member is new in the month their Date Joined falls in, whatever their status now.";
            case DELETED_MEMBERS -> "A member is listed each time they left in the month, with SASBO's deletion code; "
                    + "a leaver with no code shows the reason as recorded.";
            case RECRUITED -> "The month's new members who have a recruiter on record, by recruiter.";
            case REPRESENTATIVES -> "Members on record marked as a shop steward or a BEC member, as they stand now.";
        });
        if (taken) {
            notes.add("Read from the members as they stand now: a member captured or changed after the month was taken "
                    + "can differ from the month's figures.");
        }
        if (!scope.isAll()) {
            notes.add("Only the members whose workplace is in your " + (scope.type() == CallerScope.Type.REGION
                    ? "region" : "areas") + " are listed; a member with no workplace on record is listed for head "
                    + "office only.");
        }
        return notes;
    }

    private static String describe(CallerScope scope) {
        return scope.isAll() ? "ALL" : scope.type().name() + " " + String.join(" ", new TreeSet<>(scope.codes()));
    }

    /** An area letter or region code as a request names it: trimmed, upper case, short, of letters and digits. */
    private static String code(String field, String value, int ceiling) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String code = value.trim().toUpperCase(Locale.ROOT);
        if (code.length() > ceiling || !CODE.matcher(code).matches()) {
            throw new MonthEndRefusal(MonthEndRefusalCode.REQUEST_MALFORMED, "That " + field + " could not be read.",
                    Map.of("field", field));
        }
        return code;
    }
}
