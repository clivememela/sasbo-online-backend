package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceListAnswer;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceRows;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceStatusDto;
import za.co.titandynamix.sasbo_online_backend.enums.ListEmptyReason;
import za.co.titandynamix.sasbo_online_backend.repository.SasboReferenceRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SasboReferenceRepository.ListRecord;
import za.co.titandynamix.sasbo_online_backend.services.ReferenceListService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * SASBO's lists and where each stands (01.1). A list read answers its rows in the server's order with the day its
 * report was printed and the day SASBO confirmed it; an empty list says why (ME-19).
 */
@Service
public class ReferenceListServiceImpl implements ReferenceListService {

    static final String WORKPLACES = "workplaces";

    private final SasboReferenceRepository repository;
    private final SasboEnvironment environment;
    private final WorkplaceSwitch workplaces;

    public ReferenceListServiceImpl(SasboReferenceRepository repository, SasboEnvironment environment,
                                    WorkplaceSwitch workplaces) {
        this.repository = repository;
        this.environment = environment;
        this.workplaces = workplaces;
    }

    @Override
    public ReferenceListAnswer<ReferenceRows.Region> regions() {
        return answer("regions", repository::regions);
    }

    @Override
    public ReferenceListAnswer<ReferenceRows.Area> areas() {
        return answer("areas", repository::areas);
    }

    @Override
    public ReferenceListAnswer<ReferenceRows.EmployerGroup> employerGroups() {
        return answer("employerGroups", repository::employerGroups);
    }

    @Override
    public ReferenceListAnswer<ReferenceRows.Employer> employers() {
        return answer("employers", repository::employers);
    }

    @Override
    public ReferenceListAnswer<ReferenceRows.TableCode> tableCodes() {
        return answer("tableCodes", repository::tableCodes);
    }

    @Override
    public ReferenceListAnswer<ReferenceRows.CommitteeProvince> committeeProvinces() {
        return answer("committeeProvinces", repository::committeeProvinces);
    }

    @Override
    public ReferenceListAnswer<ReferenceRows.Committee> committees() {
        return answer("committees", repository::committees);
    }

    @Override
    public ReferenceListAnswer<ReferenceRows.SuspensionCode> suspensionCodes() {
        return answer("suspensionCodes", repository::suspensionCodes);
    }

    @Override
    public ReferenceListAnswer<ReferenceRows.DeletionCode> deletionCodes() {
        return answer("deletionCodes", repository::deletionCodes);
    }

    @Override
    public ReferenceStatusDto status() {
        Map<String, ListRecord> recorded = repository.recorded();
        Map<String, Long> counts = repository.counts();
        List<ReferenceStatusDto.ListStatus> lists = new ArrayList<>();
        for (String key : SasboReferenceRepository.countedLists()) {
            if (!key.equals(WORKPLACES)) {
                lists.add(standing(key, counts.getOrDefault(key, 0L), recorded.get(key)));
            }
        }
        // The workplaces are answered as the switch allows (MD62): switched off, the rows at rest are not answered,
        // and the reason is that they are not loaded here -- whatever the table holds.
        ReferenceStatusDto.ListStatus workplaceStanding = workplaces.workplacesOnRecord()
                ? standing(WORKPLACES, counts.getOrDefault(WORKPLACES, 0L), recorded.get(WORKPLACES))
                : new ReferenceStatusDto.ListStatus(WORKPLACES, 0, null, null, null,
                        ListEmptyReason.NOT_LOADED.name(), null);
        return new ReferenceStatusDto(lists, workplaceStanding, workplaces.mode().name());
    }

    private <T> ReferenceListAnswer<T> answer(String key, Supplier<List<T>> read) {
        List<T> rows = read.get();
        ListRecord record = repository.recorded().get(key);
        ListEmptyReason reason = reasonFor(rows.size(), record, environment.isProduction());
        return new ReferenceListAnswer<>(key,
                record == null || record.printedOn() == null ? null : record.printedOn().toString(),
                record == null || record.confirmedOn() == null ? null : record.confirmedOn().toString(),
                reason == null ? null : reason.name(),
                rows);
    }

    private ReferenceStatusDto.ListStatus standing(String key, long rows, ListRecord record) {
        ListEmptyReason reason = reasonFor(rows, record, environment.isProduction());
        return new ReferenceStatusDto.ListStatus(key, rows,
                record == null || record.printedOn() == null ? null : record.printedOn().toString(),
                record == null || record.confirmedOn() == null ? null : record.confirmedOn().toString(),
                record == null || record.recordedAt() == null || !"SEEDED".equals(record.outcome())
                        ? null : record.recordedAt().toString(),
                reason == null ? null : reason.name(),
                reason == ListEmptyReason.FAILED ? record.failure() : null);
    }

    static ListEmptyReason reasonFor(long rows, ListRecord record, boolean production) {
        return ListEmptyReason.of(rows,
                record == null ? null : record.outcome(),
                record != null && record.confirmedOn() != null,
                production);
    }
}
