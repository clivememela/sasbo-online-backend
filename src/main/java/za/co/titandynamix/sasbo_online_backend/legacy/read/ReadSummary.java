package za.co.titandynamix.sasbo_online_backend.legacy.read;

import java.util.List;

/** What a read found: how many data rows it handed on, and the columns they were read under. */
public record ReadSummary(int rows, List<String> columns) {

    public ReadSummary {
        columns = List.copyOf(columns);
    }
}
