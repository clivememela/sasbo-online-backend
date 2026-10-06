package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterOverviewDto;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterQuery;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRegisterRepository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;

import java.util.Arrays;
import java.util.List;

/**
 * The member register, a page at a time, and its figures (register row A10) -- within the caller's scope.
 */
public interface MemberRegisterService {

    /** A page where none is named. SASBO has not said yet how many a page should show (REG-25). */
    int DEFAULT_PAGE_SIZE = 50;

    /** The most a page may hold: the screen offers 25, 50 and 100. */
    int MAX_PAGE_SIZE = 100;

    /** The longest text a criterion takes; a longer one is {@code REQUEST_MALFORMED}, naming the field only. */
    int MAX_TERM_LENGTH = 100;

    /** The most branches the Branch criterion is offered. */
    int MAX_BRANCHES = 500;

    /** The orders a page may be read in -- the register's columns, by their names on the wire. */
    List<String> SORTS = Arrays.stream(MemberRegisterRepository.Order.values())
            .map(MemberRegisterRepository.Order::key)
            .toList();

    /** The register's own order, where none is named (SASBO's choice is REG-26, unanswered). */
    String DEFAULT_SORT = "surname,asc";

    /**
     * One page of the members the question matches, in the caller's scope.
     *
     * @param query null is the register's first page, in surname order
     * @throws za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal {@code REGISTER_PAGE_INVALID} for a
     *         page below nought or a size outside one to {@link #MAX_PAGE_SIZE}; {@code REGISTER_SORT_UNKNOWN} for an
     *         order not in {@link #SORTS}; {@code STATUS_UNKNOWN} for a status the record does not take;
     *         {@code REQUEST_MALFORMED} for a text over {@link #MAX_TERM_LENGTH}
     */
    MemberRegisterPageDto page(MemberRegisterQuery query, CallerScope scope);

    /** The figure strip and the branches, for the members in the caller's scope, as of today in South Africa. */
    MemberRegisterOverviewDto overview(CallerScope scope);
}
