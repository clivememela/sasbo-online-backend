package za.co.titandynamix.sasbo_online_backend.dto.workplace;

/**
 * A workplace as {@code GET /api/workplaces} answers it (01.2): an organisation, with its lineage -- its table code and
 * how the files prove it, the employer and group that code belongs to, its area and region, its committee.
 *
 * <p>{@code staffComplement} is answered only where the deployment's switch says {@code all}: at {@code names} nothing
 * made from a staff number is answered (MD62, MR-SEC-19). It is never a count of members. Dates are ISO strings.
 */
public record WorkplaceDto(
        long id,
        int raNumber,
        String name,
        String employerPrinted,
        Long employerId,
        String employer,
        String group,
        Integer tableCode,
        String tableCodeBy,
        String area,
        String region,
        String committee,
        String committeeName,
        Integer staffComplement,
        String closedOn) {
}
