package za.co.titandynamix.sasbo_online_backend.dto.acb;

/** How a held letter was dealt with by hand (Q46). Checked by the service, so a refusal is coded (EH2-B). */
public record HandledRequest(String note) {
}
