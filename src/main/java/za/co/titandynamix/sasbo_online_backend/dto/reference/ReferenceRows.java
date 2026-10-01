package za.co.titandynamix.sasbo_online_backend.dto.reference;

import java.util.List;

/**
 * The rows of SASBO's lists, one shape a list (01.1, MD41). Organisations and codes, as printed on SASBO's reports of
 * 31 August 2026 -- never a person and never a count of members.
 */
public final class ReferenceRows {

    private ReferenceRows() {
    }

    /** A region: {@code JHB}, as {@code prt_mem_area} heads it. */
    public record Region(String code, String name) {
    }

    /** An area: its letter, and its region's code where the reports print one. */
    public record Area(String letter, String region) {
    }

    /** An employer group, and whether it is one of SASBO's core groups. */
    public record EmployerGroup(String name, boolean core) {
    }

    /**
     * An employer, in its group, with the table codes the files prove are its members' -- one, or two together (MD42).
     * {@code id} is what a filter names: two employers print the same name.
     */
    public record Employer(long id, String name, String group, boolean core, List<Integer> tableCodes) {
    }

    /** A table code, its employer where the files prove one, and the part of it the code is, where the name says. */
    public record TableCode(int code, String name, Long employerId, String employer, String part) {
    }

    /** A province's letter; its name is empty until SASBO says which province it is (MM62). */
    public record CommitteeProvince(String letter, String name) {
    }

    /** A committee: its code, its province's letter, its name as printed, and the workbook's recruiting number. */
    public record Committee(String code, String province, String name, Integer recruitingNumber) {
    }

    /** Why a member is suspended: SASBO's code, and its words as printed. */
    public record SuspensionCode(int code, String name) {
    }

    /**
     * Why a member left (03.3): SASBO's NUMBER for it -- never the table's id (RC7) -- and its words; or, for one of the
     * reasons SASBO keeps under a code (the reasons for resigning, under 2), no number and the code it stands {@code under}.
     */
    public record DeletionCode(Integer code, String name, Integer under) {
    }
}
