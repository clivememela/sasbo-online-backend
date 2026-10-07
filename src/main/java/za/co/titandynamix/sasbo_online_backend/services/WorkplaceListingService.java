package za.co.titandynamix.sasbo_online_backend.services;

import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceListingsDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceMemberPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceMembersDto;
import za.co.titandynamix.sasbo_online_backend.enums.ListEmptyReason;
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusKind;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceLookupRepository;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceMemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceRepository;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceVisitRepository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.utils.MemberStatuses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The Location Listing (October build, 5 Oct Show &amp; Tell): SASBO's workplaces looked up by R/A number -- many at
 * once, for the organisers' batch print -- and the members at each.
 *
 * <h2>The batch print: up to fifty numbers, two statements</h2>
 * "If you have a list of 20 ... put all 20 numbers in at once, click print" (5 Oct). The numbers are read, each once,
 * in the order sent; fifty at most, so a print is bounded whatever is pasted. The workplaces come back in one statement
 * on the R/A number's unique index and their newest visits in one more -- never a statement a number -- and a number
 * that is no workplace here is said, not dropped, so the rest still print.
 *
 * <h2>The members at a branch: a permission and a scope</h2>
 * A listing that names members needs {@code MEMBERS_VIEW} (the controller), and lists only a workplace in the caller's
 * region or area -- checked here, and filtered again in the statement itself ({@link WorkplaceMemberRepository}).
 */
@Service
public class WorkplaceListingService {

    /** The most R/A numbers one print takes: an organiser's month is about twenty (5 Oct). */
    public static final int MAX_NUMBERS = 50;

    /** How many of a workplace's newest visits a listing prints: the branch list's ten. */
    static final int LAST_VISITS = 10;

    /** The largest page of a workplace's members, as every page of the arc (MR-PERF-12). */
    public static final int MAX_PAGE_SIZE = 200;

    static final int DEFAULT_PAGE_SIZE = 50;

    /**
     * The most members a batch print lists under one branch. An organiser's branches have tens of members; a head
     * office has thousands, and a printed list of them is not what a visit needs -- the branch says how many it has.
     */
    public static final int MEMBERS_PER_BRANCH = 500;

    /** An R/A number as typed: up to nine digits, the column being an integer. */
    private static final Pattern RA_NUMBER = Pattern.compile("\\d{1,9}");

    private final WorkplaceRepository workplaces;
    private final WorkplaceVisitRepository visits;
    private final WorkplaceLookupRepository places;
    private final WorkplaceMemberRepository members;
    private final WorkplaceService workplacePages;
    private final WorkplaceSwitch workplaceSwitch;

    public WorkplaceListingService(WorkplaceRepository workplaces, WorkplaceVisitRepository visits,
                                   WorkplaceLookupRepository places, WorkplaceMemberRepository members,
                                   WorkplaceService workplacePages, WorkplaceSwitch workplaceSwitch) {
        this.workplaces = workplaces;
        this.visits = visits;
        this.places = places;
        this.members = members;
        this.workplacePages = workplacePages;
        this.workplaceSwitch = workplaceSwitch;
    }

    /**
     * The workplaces whose R/A numbers are asked for, in the order asked, with the numbers that are none.
     *
     * @throws MonthEndRefusal {@code RA_NUMBERS_INVALID}: none asked for, more than {@value #MAX_NUMBERS}, or one that
     *                         is not a number of up to nine digits
     */
    public WorkplaceListingsDto listings(List<String> raNumbers) {
        List<Integer> asked = List.copyOf(read(raNumbers));
        if (!workplaceSwitch.workplacesOnRecord()) {
            return new WorkplaceListingsDto(asked, List.of(), List.of(), workplaceSwitch.mode().name(),
                    ListEmptyReason.NOT_LOADED.name());
        }
        boolean staffNumbers = workplaceSwitch.staffNumbersServed();
        Map<Integer, WorkplaceDto> found = new HashMap<>();
        for (WorkplaceDto workplace : workplaces.byRaNumbers(asked, staffNumbers)) {
            found.put(workplace.raNumber(), workplace);
        }
        // Nothing made from a staff number below ALL (MD62, MR-SEC-19): no visit, as no complement.
        Map<Long, List<WorkplaceListingsDto.LastVisit>> newest = new HashMap<>();
        if (staffNumbers && !found.isEmpty()) {
            for (WorkplaceVisitRepository.Newest visit : visits.newest(
                    found.values().stream().map(WorkplaceDto::id).toList(), LAST_VISITS)) {
                newest.computeIfAbsent(visit.workplaceId(), id -> new ArrayList<>())
                        .add(new WorkplaceListingsDto.LastVisit(visit.visitedOn().toString(), visit.dayKnown()));
            }
        }
        List<WorkplaceListingsDto.Listing> listings = new ArrayList<>();
        List<Integer> unknown = new ArrayList<>();
        for (Integer number : asked) {
            WorkplaceDto workplace = found.get(number);
            if (workplace == null) {
                unknown.add(number);
            } else {
                listings.add(new WorkplaceListingsDto.Listing(workplace,
                        List.copyOf(newest.getOrDefault(workplace.id(), List.of()))));
            }
        }
        // ME-19: where not one was found, say why none could be -- the workplaces' own reason, or none where some
        // are on record and these numbers are simply not among them.
        String reason = found.isEmpty() ? workplacePages.page(0, 1, null, null, null, null, null).reason() : null;
        return new WorkplaceListingsDto(asked, List.copyOf(listings), List.copyOf(unknown),
                workplaceSwitch.mode().name(), reason);
    }

    /**
     * A page of the members on record at a workplace, as far as the caller's scope reaches.
     *
     * @throws MonthEndRefusal {@code WORKPLACE_UNKNOWN} for a workplace not on record here (or none served here);
     *                         {@code WORKPLACE_OUT_OF_SCOPE} for one outside the caller's region or area;
     *                         {@code WORKPLACE_PAGE_INVALID} for a page that does not exist
     */
    public WorkplaceMemberPageDto members(Long workplaceId, Integer page, Integer size) {
        int number = page == null ? 0 : page;
        int rows = size == null ? DEFAULT_PAGE_SIZE : size;
        if (number < 0 || rows < 1 || rows > MAX_PAGE_SIZE) {
            throw pageInvalid(number, rows);
        }
        WorkplaceLookupRepository.Place place = workplaceId == null || !workplaceSwitch.workplacesOnRecord() ? null
                : places.byId(workplaceId).orElse(null);
        if (place == null) {
            throw new MonthEndRefusal(MonthEndRefusalCode.WORKPLACE_UNKNOWN,
                    "That workplace is not on record here. Choose another.",
                    Map.of("workplaceId", String.valueOf(workplaceId)));
        }
        CallerScope scope = CallerScope.current();
        if (!scope.allows(place.area(), place.region())) {
            throw WorkplaceVisitService.outOfScope(place.id());
        }
        WorkplaceMemberRepository.Slice slice = members.page(place.id(), scope, number, rows);
        if (slice.rows().isEmpty() && number > 0) {
            throw pageInvalid(number, rows);
        }
        List<WorkplaceMemberPageDto.Member> listed = slice.rows().stream().map(WorkplaceListingService::member)
                .toList();
        return new WorkplaceMemberPageDto(place.id(), listed, number, rows, slice.total(),
                (int) ((slice.total() + rows - 1) / rows));
    }

    /**
     * The members at each branch of a batch print, in one answer (October build review): the workplaces asked for by id
     * -- fifty at most, a batch's ceiling -- read in one statement with where each is, and their members in one more,
     * scoped in SQL and capped at {@value #MEMBERS_PER_BRANCH} a branch. A branch outside the caller's region or area is
     * answered as such and asked nothing of; an id that is no workplace is said.
     *
     * @throws MonthEndRefusal {@code REQUEST_MALFORMED} (field {@code ids}): none, more than {@value #MAX_NUMBERS}, or
     *                         one that is not a workplace's number
     */
    public WorkplaceMembersDto membersOf(List<String> ids) {
        List<Long> asked = List.copyOf(readIds(ids));
        if (!workplaceSwitch.workplacesOnRecord()) {
            return new WorkplaceMembersDto(List.of(), asked, MEMBERS_PER_BRANCH);
        }
        Map<Long, WorkplaceLookupRepository.Place> found = new HashMap<>();
        for (WorkplaceLookupRepository.Place place : places.byIds(asked)) {
            found.put(place.id(), place);
        }
        CallerScope scope = CallerScope.current();
        List<Long> inScope = asked.stream().filter(found::containsKey)
                .filter(id -> scope.allows(found.get(id).area(), found.get(id).region())).toList();
        Map<Long, List<WorkplaceMemberPageDto.Member>> listed = new HashMap<>();
        Map<Long, Long> totals = new HashMap<>();
        for (WorkplaceMemberRepository.BranchRow row : members.forWorkplaces(inScope, scope, MEMBERS_PER_BRANCH)) {
            listed.computeIfAbsent(row.workplaceId(), id -> new ArrayList<>()).add(member(row.member()));
            totals.put(row.workplaceId(), row.branchTotal());
        }
        List<WorkplaceMembersDto.Branch> branches = new ArrayList<>();
        List<Long> unknown = new ArrayList<>();
        for (Long id : asked) {
            if (!found.containsKey(id)) {
                unknown.add(id);
            } else if (!inScope.contains(id)) {
                branches.add(new WorkplaceMembersDto.Branch(id, true, List.of(), 0));
            } else {
                branches.add(new WorkplaceMembersDto.Branch(id, false, List.copyOf(listed.getOrDefault(id, List.of())),
                        totals.getOrDefault(id, 0L)));
            }
        }
        return new WorkplaceMembersDto(List.copyOf(branches), List.copyOf(unknown), MEMBERS_PER_BRANCH);
    }

    /** The workplace ids asked for, each once, in the order sent -- or the refusal that says they cannot be read. */
    static Set<Long> readIds(List<String> ids) {
        List<String> given = ids == null ? List.of()
                : ids.stream().filter(text -> text != null && !text.isBlank()).map(String::trim).toList();
        if (given.isEmpty() || given.size() > MAX_NUMBERS) {
            throw idsMalformed();
        }
        Set<Long> read = new LinkedHashSet<>();
        for (String text : given) {
            if (!text.matches("\\d{1,18}") || Long.parseLong(text) < 1) {
                throw idsMalformed();
            }
            read.add(Long.parseLong(text));
        }
        return read;
    }

    private static MonthEndRefusal idsMalformed() {
        return new MonthEndRefusal(MonthEndRefusalCode.REQUEST_MALFORMED,
                "This request could not be read. Send each field in the form it is expected in.",
                MonthEndRefusal.details("field", "ids", "max", String.valueOf(MAX_NUMBERS)));
    }

    /** The numbers asked for, each once, in the order sent -- or the refusal that says why they cannot be read. */
    static Set<Integer> read(List<String> raNumbers) {
        List<String> given = raNumbers == null ? List.of()
                : raNumbers.stream().filter(text -> text != null && !text.isBlank()).map(String::trim).toList();
        if (given.isEmpty()) {
            throw numbersInvalid("NONE", "Type at least one R/A number.");
        }
        if (given.size() > MAX_NUMBERS) {
            throw numbersInvalid("TOO_MANY", "Up to " + MAX_NUMBERS + " R/A numbers can be printed at a time.");
        }
        Set<Integer> numbers = new LinkedHashSet<>();
        for (String text : given) {
            if (!RA_NUMBER.matcher(text).matches() || Integer.parseInt(text) < 1) {
                throw numbersInvalid("NOT_A_NUMBER", "Each R/A number is a number of up to nine digits.");
            }
            numbers.add(Integer.parseInt(text));
        }
        return numbers;
    }

    private static WorkplaceMemberPageDto.Member member(WorkplaceMemberRepository.Row row) {
        String tail = row.employeeNumberTail();
        return new WorkplaceMemberPageDto.Member(row.id(), row.membershipNumber(), row.title(), row.initials(),
                row.surname(), row.status(), MemberStatuses.kindOf(row.status()) == MemberStatusKind.SUSPENDED,
                row.joinDate() == null ? null : row.joinDate().toLocalDate().toString(),
                tail == null ? null : "****" + tail, row.monthlySubscription(), row.email(), row.cell(),
                row.identity());
    }

    private static MonthEndRefusal numbersInvalid(String reason, String message) {
        return new MonthEndRefusal(MonthEndRefusalCode.RA_NUMBERS_INVALID, message + " Nothing was looked up.",
                MonthEndRefusal.details("reason", reason, "max", String.valueOf(MAX_NUMBERS)));
    }

    private static MonthEndRefusal pageInvalid(int page, int size) {
        return new MonthEndRefusal(MonthEndRefusalCode.WORKPLACE_PAGE_INVALID,
                "That page of the workplace's members does not exist.",
                MonthEndRefusal.details("page", String.valueOf(page), "size", String.valueOf(size),
                        "maxSize", String.valueOf(MAX_PAGE_SIZE)));
    }
}
