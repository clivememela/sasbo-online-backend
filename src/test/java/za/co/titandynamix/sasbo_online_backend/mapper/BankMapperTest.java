package za.co.titandynamix.sasbo_online_backend.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.dto.bank.BankDto;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.fixtures.BankFixture;
import za.co.titandynamix.sasbo_online_backend.support.MapperContract;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reference mapper test. The other nine copy this file and change three
 * things: the mapper, the two types, and the floor.
 *
 * <p>The mapper is instantiated through its generated implementation rather
 * than injected, because nothing here needs a Spring context. A mapper is a
 * pure function; giving it a context would make ten fast tests into ten slow
 * ones for no assertion gained.
 */
@DisplayName("BankMapper")
class BankMapperTest {

    private final BankMapper mapper = new BankMapperImpl();

    @Test
    @DisplayName("every field on BankDto arrives from a fully populated Bank")
    void everyTargetFieldArrives() {
        // No field list here on purpose. This walks BankDto by reflection, so
        // field 4 is covered the day someone adds it -- which is the whole
        // point, and the reason a hand-written list would be worse than
        // nothing.
        MapperContract.assertEveryTargetFieldArrives(
                Bank.class, BankDto.class, this.mapper::toDto, 2);
    }

    @Test
    @DisplayName("maps a null entity to null rather than an empty DTO")
    void mapsNullToNull() {
        // An empty DTO here would reach the client as a bank with no name,
        // which reads as a data problem rather than an absent record.
        assertThat(this.mapper.toDto(null)).isNull();
    }

    @Test
    @DisplayName("carries the values through unchanged")
    void carriesValuesThrough() {
        // The reflective test proves nothing was dropped. This one is the
        // readable statement of what the mapper is for, against the domain's
        // own fixture rather than synthetic values.
        Bank bank = BankFixture.withId(7, "Capitec");

        BankDto dto = this.mapper.toDto(bank);

        assertThat(dto.getId()).isEqualTo(7);
        assertThat(dto.getName()).isEqualTo("Capitec");
        assertThat(dto.getUniversalBranchCode()).isEqualTo(BankFixture.UNIVERSAL_BRANCH_CODE);
    }
}
