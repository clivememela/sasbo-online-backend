package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.RecruitmentChannelDto;
import za.co.titandynamix.sasbo_online_backend.entity.recruitment.RecruitmentChannel;
import za.co.titandynamix.sasbo_online_backend.repository.RecruitmentChannelRepository;
import za.co.titandynamix.sasbo_online_backend.services.RecruitmentChannelService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecruitmentChannelServiceImpl implements RecruitmentChannelService {

    private final RecruitmentChannelRepository repository;
    private final JdbcTemplate jdbc;

    @Override
    public List<RecruitmentChannelDto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    @Override
    public RecruitmentChannelDto create(RecruitmentChannelDto dto) {
        if (repository.existsByNameIgnoreCase(dto.getName())) {
            throw new IllegalArgumentException("A recruitment type with that name already exists.");
        }
        RecruitmentChannel entity = RecruitmentChannel.builder()
                .name(dto.getName().trim())
                .enabled(true)
                .build();
        return toDto(repository.save(entity));
    }

    @Override
    public Optional<RecruitmentChannelDto> update(Long id, RecruitmentChannelDto dto) {
        return repository.findById(id).map(existing -> {
            if (!existing.getName().equalsIgnoreCase(dto.getName()) &&
                    repository.existsByNameIgnoreCase(dto.getName())) {
                throw new IllegalArgumentException("A recruitment type with that name already exists.");
            }
            existing.setName(dto.getName().trim());
            existing.setEnabled(dto.isEnabled());
            return toDto(repository.save(existing));
        });
    }

    /**
     * Removes a recruitment channel -- what the Recruiters screen lists and calls a recruiter (register A49).
     *
     * <p>🔒 MO21, the owner's decision of 2026-10-01: the channel's members KEEP THEIR STATUS, with only their channel
     * cleared. Both happen here, in one transaction, so a removal is whole or nothing: the screen used to write each
     * member through the PUT -- setting a status, {@code Inactive}, that no list knows -- and then delete a
     * representative that happened to share the channel's number. Without the clearing, the database refuses the
     * delete of a channel any member names ({@code fk_members_recruitment_channel}).
     */
    @Override
    @Transactional
    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        jdbc.update("UPDATE members SET recruitment_channel_id = NULL WHERE recruitment_channel_id = ?", id);
        repository.deleteById(id);
        return true;
    }

    private RecruitmentChannelDto toDto(RecruitmentChannel r) {
        RecruitmentChannelDto dto = new RecruitmentChannelDto();
        dto.setId(String.valueOf(r.getId()));
        dto.setName(r.getName());
        dto.setEnabled(r.isEnabled());
        return dto;
    }
}
