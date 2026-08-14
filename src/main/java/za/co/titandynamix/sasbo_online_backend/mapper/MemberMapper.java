package za.co.titandynamix.sasbo_online_backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberDto;
import za.co.titandynamix.sasbo_online_backend.entity.Member;

@Mapper(componentModel = "spring")
public interface MemberMapper {

    @Mapping(target = "recruitmentRepresentativeId", source = "recruitmentRepresentative.id")
    @Mapping(target = "areaId", source = "area.id")
    @Mapping(target = "userId", source = "user.id")
    MemberDto toDto(Member member);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "membershipNumber", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "recruitmentRepresentative", ignore = true)  // set manually in service
    @Mapping(target = "area", ignore = true)                      // set manually in service
    void updateFromMember(Member source, @MappingTarget Member target);
}

