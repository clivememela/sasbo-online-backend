package za.co.titandynamix.sasbo_online_backend.enums;

import lombok.Getter;

/**
 * The position a BEC Executive holds (V55) -- one, never several -- in SASBO's spelling and in the
 * order of their structures tree, 2026-09-30. The member record stores the display name in
 * {@code bec_executive_position}.
 *
 * <p>Published in {@code api-vocabulary.json} ({@code memberStructures.becExecutivePositions}), where
 * the client's parity test holds its options to this list.
 */
@Getter
public enum BecExecutivePosition {

    CHAIRPERSON("Chairperson"),
    DEPUTY_CHAIRPERSON("Deputy Chairperson"),
    SECRETARY("Secretary"),
    DEPUTY_SECRETARY("Deputy Secretary"),
    TREASURER("Treasurer");

    private final String displayName;

    BecExecutivePosition(String displayName) {
        this.displayName = displayName;
    }
}
