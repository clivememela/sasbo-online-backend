package za.co.titandynamix.sasbo_online_backend.mapper;

import org.junit.jupiter.api.DisplayName;
import za.co.titandynamix.sasbo_online_backend.dto.subscription.SubscriptionsDto;
import za.co.titandynamix.sasbo_online_backend.entity.subscription.Subscriptions;

/**
 * Four lines, because {@link AbstractMapperContractTest} holds the two
 * assertions every mapper test makes. What is specific to this mapper is the
 * two types and the non-vacuity floor -- and the floor is deliberately below
 * {@code SubscriptionsDto}'s real field count, because it proves the walk walked rather
 * than setting a coverage target.
 */
@DisplayName("SubscriptionsMapper")
class SubscriptionsMapperTest extends AbstractMapperContractTest<Subscriptions, SubscriptionsDto> {

    SubscriptionsMapperTest() {
        super(Subscriptions.class, SubscriptionsDto.class, new SubscriptionsMapperImpl()::toDto, 5);
    }
}
