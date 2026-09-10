package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.titandynamix.sasbo_online_backend.entity.recruitment.Recruiter;
import za.co.titandynamix.sasbo_online_backend.repository.RecruiterRepository;
import za.co.titandynamix.sasbo_online_backend.services.RecruiterService;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecruiterServiceImpl implements RecruiterService {

    private final RecruiterRepository recruiterRepository;

    /**
     * The recruiters a member may be attributed to, by name.
     *
     * <p>Sorted HERE rather than in the browser, so two screens reading this list
     * cannot present it in two different orders -- "sorted" is a property of the
     * answer, not a preference of one caller. Case-insensitive, because the seed
     * mixes {@code SYDNEY BIYELA} with {@code Alice Smith} and a byte-order sort
     * files every capital ahead of every lower-case letter.
     *
     * <p>🗣️ Vacancies are FILTERED, not deleted (2026-09-10, the owner). Three of
     * the seed's 33 rows are named {@code VACANT} and between them hold 228 members
     * recruited; {@link Recruiter#isVacancy} carries the reasoning. Filtered before
     * the sort purely so the comparator sees fewer rows -- the two are independent.
     *
     * <p>⚠️ The exclusion is HERE and in {@code MemberServiceImpl.resolveRecruiter},
     * because a drop-down is a suggestion and a POST is not obliged to honour it.
     * One rule, two places it has to hold, one predicate shared -- rather than a
     * browser-side filter that the first {@code curl} walks straight past.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Recruiter> findSelectable() {
        return this.recruiterRepository.findAll().stream()
                .filter(recruiter -> !Recruiter.isVacancy(recruiter))
                .sorted(Comparator.comparing(
                        Recruiter::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }
}
