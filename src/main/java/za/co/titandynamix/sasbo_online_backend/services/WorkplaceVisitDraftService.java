package za.co.titandynamix.sasbo_online_backend.services;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.VisitDraftDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.VisitDraftPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.VisitDraftRequest;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.VisitDraftSubmitRequest;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceVisitPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceVisitRequest;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceLookupRepository;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceVisitDraftRepository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.ScopedPrincipal;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;

/**
 * The visit forms, saving and submitting (October build, 5 Oct Show &amp; Tell): an organiser saves a visit as a draft,
 * comes back to it, and submits it -- and a submitted visit is SASBO's record, read-only.
 *
 * <h2>A draft is its owner's</h2>
 * "My drafts" are the drafts the signed-in user saved: every read and write names them as the owner, so another
 * person's draft is never found. Who saved and who submitted are recorded as every history records its actor
 * ({@link AcbActor#current()}).
 *
 * <h2>Checked by a visit's rules, twice</h2>
 * A draft is held to the rules of the visit it will become -- by {@link WorkplaceVisitService#check}, the same code --
 * so a draft that saves is a draft that submits, but for its day: a draft may be saved before the day is chosen, and
 * is submitted only with one. The workplace is named by its R/A number, and must be one the caller's region or area
 * covers: a visit writes the workplace's complement, which is that area's figure.
 *
 * <h2>Two tabs, one draft</h2>
 * Every save and the submission name the version they were made from; the row is read {@code FOR UPDATE} and changed
 * only at that version, so a save made from an older one is refused {@code VISIT_DRAFT_STALE} -- never written over the
 * newer. A submission sent twice (a double click, a retry after a dropped answer) answers the visit submitted the first
 * time, not a second visit and not a refusal.
 *
 * <h2>Submitting appends the visit through the one path that records one</h2>
 * {@link WorkplaceVisitService#record} -- its complement written through to the workplace -- in the same transaction
 * as the draft is marked submitted with the visit's number: both, or neither.
 */
@Service
public class WorkplaceVisitDraftService {

    public static final int MAX_PAGE_SIZE = 200;

    static final int DEFAULT_PAGE_SIZE = 20;

    private final WorkplaceVisitDraftRepository drafts;
    private final WorkplaceLookupRepository places;
    private final WorkplaceVisitService visits;
    private final WorkplaceSwitch workplaceSwitch;
    private final Clock clock;

    public WorkplaceVisitDraftService(WorkplaceVisitDraftRepository drafts, WorkplaceLookupRepository places,
                                      WorkplaceVisitService visits, WorkplaceSwitch workplaceSwitch, Clock clock) {
        this.drafts = drafts;
        this.places = places;
        this.visits = visits;
        this.workplaceSwitch = workplaceSwitch;
        this.clock = clock;
    }

    /** A page of the caller's own drafts of one status -- {@code DRAFT} where none is named -- newest saved first. */
    public VisitDraftPageDto mine(String status, Integer page, Integer size) {
        String wanted = statusOf(status);
        int number = page == null ? 0 : page;
        int rows = size == null ? DEFAULT_PAGE_SIZE : size;
        if (number < 0 || rows < 1 || rows > MAX_PAGE_SIZE) {
            throw pageInvalid(number, rows);
        }
        WorkplaceVisitDraftRepository.Slice slice = drafts.mine(owner(), wanted, number, rows);
        if (slice.rows().isEmpty() && number > 0) {
            throw pageInvalid(number, rows);
        }
        return new VisitDraftPageDto(slice.rows(), wanted, number, rows, slice.total(),
                (int) ((slice.total() + rows - 1) / rows));
    }

    /** One of the caller's own drafts. */
    public VisitDraftDto one(Long id) {
        return drafts.one(idOf(id), owner()).orElseThrow(() -> notFound(id));
    }

    /** Saves a new draft. Refused whole, nothing written: see {@link #fieldsOf}. */
    @Transactional
    public VisitDraftDto create(VisitDraftRequest request) {
        WorkplaceLookupRepository.Place place = placeOf(request);
        WorkplaceVisitDraftRepository.Fields fields = fieldsOf(request);
        String owner = owner();
        long id = drafts.insert(place.id(), fields, owner, AcbActor.current(), clock.instant());
        return drafts.one(id, owner).orElseThrow(() -> notFound(id));
    }

    /** Saves over one of the caller's drafts, from the version they saw. */
    @Transactional
    public VisitDraftDto update(Long id, VisitDraftRequest request) {
        long draftId = idOf(id);
        Integer version = request == null ? null : request.version();
        if (version == null) {
            throw malformed("version");
        }
        String owner = owner();
        WorkplaceVisitDraftRepository.Head head = drafts.lock(draftId, owner).orElseThrow(() -> notFound(id));
        refuseUnlessDraftAt(head, version);
        WorkplaceLookupRepository.Place place = placeOf(request);
        WorkplaceVisitDraftRepository.Fields fields = fieldsOf(request);
        if (!drafts.update(draftId, owner, version, place.id(), fields, clock.instant())) {
            throw stale(head);
        }
        return drafts.one(draftId, owner).orElseThrow(() -> notFound(id));
    }

    /**
     * Submits one of the caller's drafts: the visit is recorded -- refused {@code VISIT_INVALID} as any visit is, the
     * draft kept as it was -- and the draft becomes read-only, naming it.
     */
    @Transactional
    public VisitDraftDto submit(Long id, VisitDraftSubmitRequest request) {
        long draftId = idOf(id);
        Integer version = request == null ? null : request.version();
        if (version == null) {
            throw malformed("version");
        }
        String owner = owner();
        WorkplaceVisitDraftRepository.Head head = drafts.lock(draftId, owner).orElseThrow(() -> notFound(id));
        if (WorkplaceVisitDraftRepository.SUBMITTED.equals(head.status()) && head.version() == version + 1) {
            // The same submission again: answered as it was answered the first time.
            return drafts.one(draftId, owner).orElseThrow(() -> notFound(id));
        }
        refuseUnlessDraftAt(head, version);
        WorkplaceLookupRepository.Place place = places.byId(head.workplaceId())
                .filter(found -> workplaceSwitch.workplacesOnRecord())
                .orElseThrow(() -> unknown(null));
        if (!CallerScope.current().allows(place.area(), place.region())) {
            throw WorkplaceListingService.outOfScope(place.id());
        }
        VisitDraftDto draft = drafts.one(draftId, owner).orElseThrow(() -> notFound(id));
        WorkplaceVisitPageDto.Visit visit = visits.record(place.id(), new WorkplaceVisitRequest(draft.visitedOn(),
                draft.timeIn(), draft.timeOut(), draft.organiser(), draft.complementFound(), draft.membershipFound(),
                draft.nonMembers(), draft.recruited(), draft.nonMembersInterviewed()));
        if (!drafts.submitted(draftId, owner, version, visit.id(), AcbActor.current(), clock.instant())) {
            throw stale(head);
        }
        return drafts.one(draftId, owner).orElseThrow(() -> notFound(id));
    }

    /**
     * The workplace a draft names, by its R/A number: needed, on record here, and in the caller's scope.
     */
    private WorkplaceLookupRepository.Place placeOf(VisitDraftRequest request) {
        Integer raNumber = request == null ? null : request.raNumber();
        if (raNumber == null) {
            throw invalid("raNumber", "The R/A number says which workplace was visited.");
        }
        if (raNumber < 1 || raNumber > 999_999_999) {
            throw invalid("raNumber", "An R/A number is a number of up to nine digits.");
        }
        WorkplaceLookupRepository.Place place = !workplaceSwitch.workplacesOnRecord() ? null
                : places.byRaNumber(raNumber).orElse(null);
        if (place == null) {
            throw unknown(raNumber);
        }
        if (!CallerScope.current().allows(place.area(), place.region())) {
            throw WorkplaceListingService.outOfScope(place.id());
        }
        return place;
    }

    /** What a draft keeps, read and held to a visit's rules -- all but the day being required (VISIT_INVALID). */
    private WorkplaceVisitDraftRepository.Fields fieldsOf(VisitDraftRequest request) {
        WorkplaceVisitService.Checked checked = visits.check(request.asVisit(), false);
        String organiser = request.organiser() == null || request.organiser().isBlank() ? null
                : request.organiser().trim();
        return new WorkplaceVisitDraftRepository.Fields(checked.day(), checked.in(), checked.out(), organiser,
                request.complementFound(), request.membershipFound(), request.nonMembers(), request.recruited(),
                request.nonMembersInterviewed());
    }

    private static void refuseUnlessDraftAt(WorkplaceVisitDraftRepository.Head head, int version) {
        if (WorkplaceVisitDraftRepository.SUBMITTED.equals(head.status())) {
            throw new MonthEndRefusal(MonthEndRefusalCode.VISIT_ALREADY_SUBMITTED,
                    "This visit has been submitted, and a submitted visit cannot be changed.",
                    MonthEndRefusal.details("visitId", String.valueOf(head.visitId()),
                            "submittedAt", head.submittedAt() == null ? null : head.submittedAt().toString()));
        }
        if (head.version() != version) {
            throw stale(head);
        }
    }

    /**
     * The username of the signed-in caller -- the draft's owner. Every route here needs {@code VISITS_CAPTURE}, so
     * there always is one; a call without one is a fault, never an anonymous draft.
     */
    static String owner() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("A visit draft is saved by a signed-in user only");
        }
        return authentication.getPrincipal() instanceof ScopedPrincipal principal ? principal.username()
                : authentication.getName();
    }

    private static String statusOf(String status) {
        if (status == null || status.isBlank()) {
            return WorkplaceVisitDraftRepository.DRAFT;
        }
        String wanted = status.trim().toUpperCase(Locale.ROOT);
        if (!wanted.equals(WorkplaceVisitDraftRepository.DRAFT) && !wanted.equals(WorkplaceVisitDraftRepository.SUBMITTED)) {
            throw malformed("status");
        }
        return wanted;
    }

    private static long idOf(Long id) {
        if (id == null) {
            throw notFound(null);
        }
        return id;
    }

    private static MonthEndRefusal stale(WorkplaceVisitDraftRepository.Head head) {
        return new MonthEndRefusal(MonthEndRefusalCode.VISIT_DRAFT_STALE,
                "This draft was saved again after you opened it, in another tab or on another device. Nothing was "
                        + "written over it: open it again to see it as it is now.",
                MonthEndRefusal.details("version", String.valueOf(head.version()),
                        "updatedAt", head.updatedAt().toString()));
    }

    private static MonthEndRefusal notFound(Long id) {
        return new MonthEndRefusal(MonthEndRefusalCode.VISIT_DRAFT_NOT_FOUND,
                "That visit draft is not one of yours, or is no longer there.",
                Map.of("draftId", String.valueOf(id)));
    }

    private static MonthEndRefusal unknown(Integer raNumber) {
        return new MonthEndRefusal(MonthEndRefusalCode.WORKPLACE_UNKNOWN,
                "No workplace with that R/A number is on record here. Check the number.",
                MonthEndRefusal.details("field", "raNumber", "raNumber", raNumber == null ? null : raNumber.toString()));
    }

    private static MonthEndRefusal invalid(String field, String message) {
        return new MonthEndRefusal(MonthEndRefusalCode.VISIT_INVALID, message + " Nothing was saved.",
                Map.of("field", field));
    }

    private static MonthEndRefusal malformed(String field) {
        return new MonthEndRefusal(MonthEndRefusalCode.REQUEST_MALFORMED,
                "This request could not be read. Send each field in the form it is expected in.",
                Map.of("field", field));
    }

    private static MonthEndRefusal pageInvalid(int page, int size) {
        return new MonthEndRefusal(MonthEndRefusalCode.WORKPLACE_PAGE_INVALID,
                "That page of your visit drafts does not exist.",
                MonthEndRefusal.details("page", String.valueOf(page), "size", String.valueOf(size),
                        "maxSize", String.valueOf(MAX_PAGE_SIZE)));
    }
}
