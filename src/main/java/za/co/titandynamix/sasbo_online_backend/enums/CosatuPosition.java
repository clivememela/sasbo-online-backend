package za.co.titandynamix.sasbo_online_backend.enums;

import lombok.Getter;

/**
 * What a COSATU member holds in COSATU's structure (V19's {@code cosatu_positions}), in SASBO's
 * spelling and in the order of their structures tree, 2026-09-30. The tree lists Locals and Region
 * among these too; those two are structures a member SITS ON, held yes/no in
 * {@code is_cosatu_local} and {@code is_cosatu_regional} since the stakeholder's word of 2026-09-17.
 *
 * <p>Published in {@code api-vocabulary.json} ({@code memberStructures.cosatuPositions}), where the
 * client's parity test holds its checkboxes to this list.
 */
@Getter
public enum CosatuPosition {

    PEC("PEC"),
    CEC("CEC"),
    CHAIRPERSON("Chairperson"),
    SECRETARY("Secretary"),
    TREASURER("Treasurer");

    private final String displayName;

    CosatuPosition(String displayName) {
        this.displayName = displayName;
    }
}
