package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbHoliday;

import java.time.LocalDate;
import java.util.List;

public interface AcbHolidayRepository extends JpaRepository<AcbHoliday, LocalDate> {

    /** One year's holidays, in date order -- a year holds a dozen or so, bounded by the calendar itself. */
    List<AcbHoliday> findByHolidayDateBetweenOrderByHolidayDate(LocalDate from, LocalDate to);
}
