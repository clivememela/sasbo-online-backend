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
     * Every recruiter, by name.
     *
     * <p>Sorted HERE rather than in the browser, so two screens reading this list
     * cannot present it in two different orders -- "sorted" is a property of the
     * answer, not a preference of one caller. Case-insensitive, because the seed
     * mixes {@code SYDNEY BIYELA} with {@code Alice Smith} and a byte-order sort
     * files every capital ahead of every lower-case letter.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Recruiter> findAll() {
        return this.recruiterRepository.findAll().stream()
                .sorted(Comparator.comparing(
                        Recruiter::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }
}
