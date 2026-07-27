package za.co.titandynamix.sasbo_online_backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import za.co.titandynamix.sasbo_online_backend.dto.subscription.SubscriptionsDto;
import za.co.titandynamix.sasbo_online_backend.entity.subscription.Subscriptions;

@Mapper(componentModel = "spring")
public interface SubscriptionsMapper {

    @Mapping(source = "bank.name", target = "bankName")
    SubscriptionsDto toDto(Subscriptions subscription);

    @Mapping(target = "bank", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "registrationDate", ignore = true)
    Subscriptions toEntity(SubscriptionsDto dto);

    @Mapping(target = "bank", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "registrationDate", ignore = true)
    void updateFromDto(SubscriptionsDto dto, @MappingTarget Subscriptions subscription);
}
