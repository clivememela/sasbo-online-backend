package za.co.titandynamix.sasbo_online_backend.dto.monthend;

/**
 * A file just produced, and ITS PASSWORD -- in this answer only (October build; Stephne MEN-05). The password is not
 * kept anywhere: lost, the file is produced again. Never printed by {@link #toString()}.
 */
public record MonthEndProducedFileDto(MonthEndFileDto file, String password, String passwordNote) {

    @Override
    public String toString() {
        return "MonthEndProducedFileDto[" + file.id() + ", password withheld]";
    }
}
