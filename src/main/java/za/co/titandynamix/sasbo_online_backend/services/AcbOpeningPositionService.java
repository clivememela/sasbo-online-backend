package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbOpeningPositionDto;

/**
 * The opening arrears position, captured by hand once at switch-over (D24). Its upload is refused
 * at the route until the API knows who is calling (SD3-B); what it does is built and proven here.
 */
public interface AcbOpeningPositionService {

    /** The capture as it stands. */
    AcbOpeningPositionDto current();

    /**
     * Validates {@code sheet} and, if every row is sound, replaces the capture with it -- all or
     * nothing. {@code OPENING_POSITION_INVALID} by row, {@code OPENING_POSITION_LOCKED} once the
     * first returns are in, {@code UPLOAD_TOO_LARGE} and {@code UPLOAD_NOT_A_SHEET} for the file.
     */
    AcbOpeningPositionDto capture(byte[] sheet, String actor);
}
