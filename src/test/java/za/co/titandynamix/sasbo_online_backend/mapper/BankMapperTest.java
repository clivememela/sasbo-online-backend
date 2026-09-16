package za.co.titandynamix.sasbo_online_backend.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.dto.bank.BankDto;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.fixtures.BankFixture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reference mapper test. The other nine extend the same base and change
 * three things: the two types, the mapping method, and the floor.
 *
 * <p>The mapper is instantiated through its generated implementation rather
 * than injected: nothing here needs a Spring context. A mapper is a pure
 * function, and giving it a context would turn ten fast tests into ten slow
 * ones for no assertion gained.
 */
@DisplayName("BankMapper")
class BankMapperTest extends AbstractMapperContractTest<Bank, BankDto> {

    private static final BankMapper MAPPER = new BankMapperImpl();

    BankMapperTest() {
        super(Bank.class, BankDto.class, MAPPER::toDto, 2);
    }

    @Test
    @DisplayName("carries the values through unchanged")
    void carriesValuesThrough() {
        // The inherited reflective test proves nothing was dropped. This one is
        // the readable statement of what the mapper is for, against the
        // domain's own fixture rather than synthetic values.
        Bank bank = BankFixture.withId(7, "Capitec");

        BankDto dto = MAPPER.toDto(bank);

        assertThat(dto.getId()).isEqualTo(7);
        assertThat(dto.getName()).isEqualTo("Capitec");
        assertThat(dto.getUniversalBranchCode()).isEqualTo(BankFixture.UNIVERSAL_BRANCH_CODE);
    }
}
