package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.entity.recruitment.Recruiter;
import za.co.titandynamix.sasbo_online_backend.services.RecruiterService;

import java.util.List;

/**
 * THE RECRUITERS — the people who sign members up.
 *
 * <h2>🔴 Why this did not exist until 03.10</h2>
 * {@code recruiters} has been seeded from {@code recruiters.json} since long before
 * this arc — <b>33 real union names</b>, with the number each has recruited. It was
 * referenced by no foreign key and <b>served by no controller</b>, so nothing could
 * read it. Meanwhile the capture form's Recruiter drop-down called
 * {@code /api/representatives}, which upstream had pointed at members flagged
 * {@code is_representative} — a flag absent from the member seed entirely. <b>The
 * drop-down was empty for every user, and the union's own list of recruiters was
 * unreachable.</b>
 *
 * <h2>Why it is a separate endpoint and not a fix to /api/representatives</h2>
 * They answer different questions, and conflating them is what produced 03.10:
 *
 * <ul>
 *   <li><b>a recruiter</b> signed this member up. Historical, fixed at capture.</li>
 *   <li><b>a representative</b> sits on a structure with a role and an election
 *       date. That is Edit Member's business, {@code Recruiters.jsx} administers it
 *       through {@code /api/representatives}, and the stakeholder keeps it there.</li>
 * </ul>
 *
 * <p>So {@code /api/representatives} is untouched and keeps doing its own job.
 *
 * <h2>Read-only, deliberately</h2>
 * Only {@code GET}. Creating a recruiter from the capture screen would let a typo
 * become a permanent option in everybody's drop-down — the same failure 02.5's
 * {@code verified} flag exists to prevent for job grades. Recruiters are union
 * staff; they are administered where staff are administered, not invented mid-capture.
 */
@RestController
@RequestMapping("/api/recruiters")
@RequiredArgsConstructor
public class RecruiterController {

    private final RecruiterService recruiterService;

    /**
     * Every recruiter, by name.
     *
     * <p>⚠️ Straight through to the service, deliberately. The first draft queried
     * {@code RecruiterRepository} here and {@code LayeringTest} refused it: a
     * controller that queries a repository directly puts query logic where no
     * service test can see it. The ordering rule lives in {@code RecruiterServiceImpl}
     * with its reason.
     */
    @GetMapping
    public List<Recruiter> getAllRecruiters() {
        return this.recruiterService.findAll();
    }
}
