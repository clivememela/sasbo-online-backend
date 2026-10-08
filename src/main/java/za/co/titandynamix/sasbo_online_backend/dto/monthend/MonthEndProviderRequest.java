package za.co.titandynamix.sasbo_online_backend.dto.monthend;

/** A provider switched on or off (MEN-35: "currently suspended but it can be used again"), naming the version read. */
public record MonthEndProviderRequest(Boolean active, Integer version) {
}
