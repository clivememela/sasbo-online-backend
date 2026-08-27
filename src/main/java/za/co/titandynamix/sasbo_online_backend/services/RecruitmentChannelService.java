package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.recruitment.RecruitmentChannelDto;

import java.util.List;
import java.util.Optional;

public interface RecruitmentChannelService {
    List<RecruitmentChannelDto> findAll();
    RecruitmentChannelDto create(RecruitmentChannelDto dto);
    Optional<RecruitmentChannelDto> update(Long id, RecruitmentChannelDto dto);
    boolean delete(Long id);
}
