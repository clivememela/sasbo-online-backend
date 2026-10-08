package za.co.titandynamix.sasbo_online_backend.dto.migration;

import java.util.List;

/** A page of batches, newest first, and how many there are. */
public record MigrationBatchPageDto(List<MigrationBatchDto> items, int page, int size, long total) {
}
