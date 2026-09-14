package za.co.titandynamix.sasbo_online_backend.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.persistence.EntityManagerFactory;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.entity.bank.BankBranch;
import za.co.titandynamix.sasbo_online_backend.entity.location.City;
import za.co.titandynamix.sasbo_online_backend.entity.location.PhysicalAddress;
import za.co.titandynamix.sasbo_online_backend.entity.location.Province;
import za.co.titandynamix.sasbo_online_backend.fixtures.BankFixture;
import za.co.titandynamix.sasbo_online_backend.fixtures.LocationFixture;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;
import za.co.titandynamix.sasbo_online_backend.support.DatabaseReset;
import za.co.titandynamix.sasbo_online_backend.support.QueryCount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The seven declared methods on {@code BankBranchRepository}, two of them
 * hand-written {@code @Query}s that project a single column.
 *
 * <p>Four of them navigate an embedded address into a related city or province
 * -- {@code findByPhysicalAddress_Province_Name} crosses an
 * {@code @Embedded} and then a relation. That is the deepest derived-query path
 * in the application, and the shape most likely to compile into SQL nobody
 * expects.
 */
@DisplayName("BankBranchRepository against the real migration chain")
class BankBranchRepositoryIT extends AbstractWritingIT {

    @Autowired
    private BankBranchRepository bankBranchRepository;

    @Autowired
    private BankRepository bankRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ProvinceRepository provinceRepository;

    @Autowired
    private CityRepository cityRepository;

    @Test
    @DisplayName("existsByBank_NameAndBranchName needs both parts to match")
    void existsByBankNameAndBranchName() {
        Bank absa = this.bankRepository.save(BankFixture.named("Absa"));
        this.saveBranch(absa, "Sandton", "632005", null);

        assertThat(this.bankBranchRepository
                .existsByBank_NameAndBranchName("Absa", "Sandton")).isTrue();
        assertThat(this.bankBranchRepository
                .existsByBank_NameAndBranchName("Absa", "Rosebank")).isFalse();
        assertThat(this.bankBranchRepository
                .existsByBank_NameAndBranchName("Capitec", "Sandton")).isFalse();
    }

    @Test
    @DisplayName("findByBank_NameAndBranchCode finds the one branch, and nothing for a miss")
    void findByBankNameAndBranchCode() {
        Bank absa = this.bankRepository.save(BankFixture.named("Absa"));
        this.saveBranch(absa, "Sandton", "632005", null);

        assertThat(this.bankBranchRepository
                .findByBank_NameAndBranchCode("Absa", "632005")).isPresent();
        assertThat(this.bankBranchRepository
                .findByBank_NameAndBranchCode("Absa", "000000")).isEmpty();
    }

    @Test
    @DisplayName("the ignore-case variant matches where the exact one does not")
    void theIgnoreCaseVariantIsGenuinelyDifferent() {
        // Two nearly identical methods sit on this repository, one case
        // sensitive and one not. Asserting them side by side is what makes the
        // difference visible -- and stops someone "tidying" one away.
        Bank absa = this.bankRepository.save(BankFixture.named("Absa"));
        this.saveBranch(absa, "Sandton", "632005", null);

        assertThat(this.bankBranchRepository
                .findByBank_NameIgnoreCaseAndBranchNameIgnoreCase("absa", "sandton")).isPresent();
        assertThat(this.bankBranchRepository
                .existsByBank_NameAndBranchName("absa", "sandton")).isFalse();
    }

    @Test
    @DisplayName("findBankNameByBranchId projects just the bank's name")
    void findBankNameByBranchId() {
        Bank absa = this.bankRepository.save(BankFixture.named("Absa"));
        BankBranch branch = this.saveBranch(absa, "Sandton", "632005", null);

        assertThat(this.bankBranchRepository.findBankNameByBranchId(branch.getId()))
                .contains("Absa");
        assertThat(this.bankBranchRepository.findBankNameByBranchId(9999)).isEmpty();
    }

    @Test
    @DisplayName("findBankNameByBranchCode projects just the bank's name")
    void findBankNameByBranchCode() {
        Bank absa = this.bankRepository.save(BankFixture.named("Absa"));
        this.saveBranch(absa, "Sandton", "632005", null);

        assertThat(this.bankBranchRepository.findBankNameByBranchCode("632005"))
                .contains("Absa");
        assertThat(this.bankBranchRepository.findBankNameByBranchCode("000000")).isEmpty();
    }

    @Test
    @DisplayName("findByPhysicalAddress_Province_Name crosses the embedded address into a relation")
    void findByProvinceName() {
        // The deepest derived path here: branch to embedded address to province
        // to name. Two provinces are seeded so a query that lost its filter
        // fails rather than looking correct.
        Province gauteng = this.provinceRepository.save(
                LocationFixture.province("Gauteng", "GP"));
        Province limpopo = this.provinceRepository.save(
                LocationFixture.province("Limpopo", "LP"));
        Bank absa = this.bankRepository.save(BankFixture.named("Absa"));

        this.saveBranch(absa, "Sandton", "632005", addressIn(gauteng, null));
        this.saveBranch(absa, "Polokwane", "632006", addressIn(limpopo, null));

        assertThat(this.bankBranchRepository.findByPhysicalAddress_Province_Name("Gauteng"))
                .extracting(BankBranch::getBranchName)
                .containsExactly("Sandton");
    }

    @Test
    @DisplayName("findByBank_NameAndPhysicalAddress_City_Name filters on the bank and the city")
    void findByBankNameAndCityName() {
        Province gauteng = this.provinceRepository.save(
                LocationFixture.province("Gauteng", "GP"));
        City johannesburg = this.cityRepository.save(
                LocationFixture.city("Johannesburg", gauteng));
        City pretoria = this.cityRepository.save(LocationFixture.city("Pretoria", gauteng));

        Bank absa = this.bankRepository.save(BankFixture.named("Absa"));
        Bank capitec = this.bankRepository.save(BankFixture.named("Capitec"));

        this.saveBranch(absa, "Sandton", "632005", addressIn(gauteng, johannesburg));
        this.saveBranch(absa, "Centurion", "632006", addressIn(gauteng, pretoria));
        this.saveBranch(capitec, "Rosebank", "470010", addressIn(gauteng, johannesburg));

        assertThat(this.bankBranchRepository
                .findByBank_NameAndPhysicalAddress_City_Name("Absa", "Johannesburg"))
                .extracting(BankBranch::getBranchName)
                .containsExactly("Sandton");
    }

    @Test
    @DisplayName("refuses two branches with the same code at the same bank")
    void branchCodeIsUniquePerBank() {
        // The composite unique constraint the schema declares, and nothing was
        // asserting it. It has to be an IT: the fast lane's schema is one
        // Hibernate invents, so the same test there would prove the annotation
        // rather than the migration.
        Bank absa = this.bankRepository.save(BankFixture.named("Absa"));
        Bank capitec = this.bankRepository.save(BankFixture.named("Capitec"));

        this.saveBranch(absa, "Sandton", "632005", null);

        // The same code at a DIFFERENT bank is allowed -- the constraint is on
        // the pair, and asserting only the rejection would not show that.
        this.saveBranch(capitec, "Rosebank", "632005", null);

        assertThatThrownBy(() -> this.saveBranch(absa, "Rosebank", "632005", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("listing branches costs the same number of queries at 5 rows and at 50")
    void listingDoesNotGrowWithTheData() {
        // BankBranch.bank is FetchType.EAGER, which is the classic N+1 shape:
        // Hibernate fetches the branches, then issues one more statement per
        // branch to fill the relation. Whether it actually does depends on how
        // it chooses to plan the query, which is exactly why this is measured
        // rather than reasoned about.
        //
        // Two sizes, because one measurement cannot tell a constant cost from a
        // growing one.
        QueryCount.assertConstantAcrossSizes(
                this.entityManagerFactory,
                this::seedTo,
                () -> this.bankBranchRepository.findAll()
                        .forEach(branch -> branch.getBank().getName()),
                5, 50);
    }

    /** Rebuilds the table with exactly {@code rows} branches on one bank. */
    private void seedTo(int rows) {
        DatabaseReset.reset(this.jdbc);
        Bank bank = this.bankRepository.save(BankFixture.named("Absa"));
        for (int i = 1; i <= rows; i++) {
            this.saveBranch(bank, "Branch " + i, String.format("%06d", 600000 + i), null);
        }
    }

    private static PhysicalAddress addressIn(Province province, City city) {
        PhysicalAddress address = new PhysicalAddress();
        address.setProvince(province);
        address.setCity(city);
        return address;
    }

    private BankBranch saveBranch(Bank bank, String name, String code, PhysicalAddress address) {
        BankBranch branch = new BankBranch();
        branch.setBank(bank);
        branch.setBranchName(name);
        branch.setBranchCode(code);
        branch.setPhysicalAddress(address);
        return this.bankBranchRepository.saveAndFlush(branch);
    }
}
