package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.titandynamix.sasbo_online_backend.entity.monthend.NoDeductionReason;

import java.util.List;

/** The reasons for no payroll deduction (V79, Stephne MEN-17) -- a list of at most fifty, read whole. */
public interface NoDeductionReasonRepository extends JpaRepository<NoDeductionReason, Long> {

    /** Every reason, in the drop-down's order. */
    List<NoDeductionReason> findAllByOrderByOrdinalAscNameAsc();

    /** The reasons with this name, whatever its case or spaces -- the rule {@code no_deduction_reasons_name_unique} holds. */
    @Query("SELECT r FROM NoDeductionReason r WHERE LOWER(TRIM(r.name)) = LOWER(TRIM(:name))")
    List<NoDeductionReason> findByNameInAnyCase(@Param("name") String name);

    /** The last place on the list, or nought where the list is empty. */
    @Query("SELECT COALESCE(MAX(r.ordinal), 0) FROM NoDeductionReason r")
    int lastOrdinal();
}
