package za.co.titandynamix.sasbo_online_backend.dto.monthend;

import java.util.List;

/**
 * A statistics pack (month end 04.10): a job the screen polls -- {@code PREPARING}, then {@code READY} with its two
 * workbooks, or {@code FAILED} saying why, once. Dates are ISO text (MD16).
 *
 * @param id         the pack's number, which the screen polls by
 * @param month      the month it is the statistics of
 * @param status     {@code PREPARING}, {@code READY} or {@code FAILED}
 * @param askedBy    who asked -- {@code system} until sign-in exists
 * @param startedAt  when its job started, ISO-8601
 * @param finishedAt when it finished, ISO-8601; {@code null} while preparing
 * @param failure    why it failed, one sentence; {@code null} unless {@code FAILED}
 * @param workbooks  the names of its workbooks, each fetched at {@code /api/month-end/packs/{id}/{name}}; empty until
 *                   {@code READY}
 */
public record MonthEndPackDto(long id, String month, String status, String askedBy, String startedAt,
                              String finishedAt, String failure, List<String> workbooks) {
}
