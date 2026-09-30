package za.co.titandynamix.sasbo_online_backend.enums;

import lombok.Getter;

/**
 * The structures a BEC Structure Representative represents (V55), in SASBO's spelling and in the
 * order of their structures tree, 2026-09-30. The member record stores the display names,
 * comma-separated in {@code bec_structures}, the way V19 stores COSATU positions.
 *
 * <p>Published in {@code api-vocabulary.json} ({@code memberStructures.becStructures}), where the
 * client's parity test holds its checkboxes to this list.
 */
@Getter
public enum BecStructure {

    INC_IC("INC / IC"),
    NCF_SJC("NCF / SJC"),
    VP("VP"),
    PRESIDENT("President"),
    MANCOM("MANCOM"),
    RESOLUTIONS_COMMITTEE("Resolutions Committee");

    private final String displayName;

    BecStructure(String displayName) {
        this.displayName = displayName;
    }
}
