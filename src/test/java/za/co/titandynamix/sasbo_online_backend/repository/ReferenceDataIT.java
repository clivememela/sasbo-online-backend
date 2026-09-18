package za.co.titandynamix.sasbo_online_backend.repository;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.entity.codes.DeletionCode;
import za.co.titandynamix.sasbo_online_backend.entity.member.JobGrade;
import za.co.titandynamix.sasbo_online_backend.entity.recruitment.RecruitmentChannel;
import za.co.titandynamix.sasbo_online_backend.fixtures.BankFixture;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;
import za.co.titandynamix.sasbo_online_backend.support.DatabaseReset;
import za.co.titandynamix.sasbo_online_backend.support.JobGradeSeedFile;
import za.co.titandynamix.sasbo_online_backend.support.KnownDefects;
import za.co.titandynamix.sasbo_online_backend.utils.JobGradeSeeder;
import za.co.titandynamix.sasbo_online_backend.support.MigrationSeed;
import za.co.titandynamix.sasbo_online_backend.support.QueryCount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The reference-data repositories: job grades, deletion codes, recruitment
 * channels, representative roles and union structures.
 *
 * <p>Grouped because they share a shape -- small, seeded, looked up by name --
 * and because each has one or two declared methods. Every repository gets its
 * own {@code @Nested} block, so a failure names one of them rather than "the
 * reference data tests".
 */
/**
 * Named short on purpose. Gradle writes one JUnit XML file per class, named
 * TEST-<fully qualified name>.xml -- and a @Nested class appends
 * $<InnerName> to that. With this package name and a checkout more than a few
 * directories deep, the longer version of this class name pushed two of those
 * paths past the 260-character Windows limit: Gradle substituted a hash into
 * the filename and the files could not be opened at all.
 *
 * <p>The XML is the artifact every CI test-reporting integration reads, so
 * losing it silently is worse than a red build -- the run looks fine and the
 * report is simply short. Recorded as a finding for the CI contract, and
 * mitigated here by keeping the names short.
 */
@DisplayName("The reference-data repositories against the real migration chain")
class ReferenceDataIT extends AbstractWritingIT {

    @Autowired
    private JobGradeRepository jobGradeRepository;

    @Autowired
    private BankRepository bankRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private DeletionCodeRepository deletionCodeRepository;

    @Autowired
    private RecruitmentChannelRepository recruitmentChannelRepository;

    @Autowired
    private RepresentativeRoleRepository representativeRoleRepository;

    @Autowired
    private UnionStructureRepository unionStructureRepository;

    /** R5-F9: the bean that actually seeds job grades, so the fixture can exist. */
    @Autowired
    private JobGradeSeeder jobGradeSeeder;

    @Nested
    @DisplayName("JobGradeRepository")
    class JobGrades {

        @Test
        @DisplayName("findByBank_Id returns only that bank's grades")
        void findByBankId() {
            Bank absa = ReferenceDataIT.this.bankRepository.save(
                    BankFixture.named("Absa"));
            Bank capitec = ReferenceDataIT.this.bankRepository.save(
                    BankFixture.named("Capitec"));

            ReferenceDataIT.this.saveGrade(absa, "BA1");
            ReferenceDataIT.this.saveGrade(absa, "BA2");
            ReferenceDataIT.this.saveGrade(capitec, "C1");

            assertThat(ReferenceDataIT.this.jobGradeRepository
                    .findByBank_Id(absa.getId()))
                    .extracting(JobGrade::getGrade)
                    .containsExactlyInAnyOrder("BA1", "BA2");
        }

        @Test
        @DisplayName("findByBank_UniversalBranchCode walks the relation to the bank's code")
        void findByUniversalBranchCode() {
            // A two-level derived query -- job grade to bank to column -- which
            // is the shape that produces a wrong-column filter, and the shape
            // the known job-grades defect actually is.
            Bank absa = BankFixture.named("Absa");
            absa.setUniversalBranchCode("632005");
            ReferenceDataIT.this.bankRepository.save(absa);
            ReferenceDataIT.this.saveGrade(absa, "BA1");

            assertThat(ReferenceDataIT.this.jobGradeRepository
                    .findByBank_UniversalBranchCode("632005"))
                    .hasSize(1);
            assertThat(ReferenceDataIT.this.jobGradeRepository
                    .findByBank_UniversalBranchCode("000000"))
                    .isEmpty();
        }

        @Test
        @DisplayName("listing job grades costs the same queries at 5 rows and at 50")
        void listingDoesNotGrowWithTheData() {
            // JobGrade.bank is FetchType.EAGER, the shape that produces one
            // extra statement per row. Whether it actually does is a planning
            // decision Hibernate makes, so it is measured rather than reasoned
            // about -- at two sizes, because one number cannot tell a constant
            // cost from a growing one.
            QueryCount.assertConstantAcrossSizes(
                    ReferenceDataIT.this.entityManagerFactory,
                    ReferenceDataIT.this::seedGradesTo,
                    () -> ReferenceDataIT.this.jobGradeRepository.findAll()
                            .forEach(grade -> grade.getBank().getName()),
                    5, 50);
        }

        @Test
        @Disabled(KnownDefects.JOB_GRADES_QUERY_FILTERS_WRONG_COLUMN)
        @DisplayName("the seeded job grades are all reachable through the lookup the API uses")
        void everySeededGradeIsReachable() throws java.io.IOException {
            // The endpoint answers with an empty list where the table holds the
            // seeded rows, because the lookup filters on a column that does not
            // carry the value being matched. It is a repository-level bug:
            // invisible to a mocked service test, and invisible to the fast lane,
            // because H2 would answer whatever schema Hibernate invented.
            //
            // 🔴 R5-F9 — THIS TEST PINNED A FIXTURE THAT DID NOT EXIST. It read
            // `SELECT count(*) FROM job_grades` and asserted `27`, "seeded by the
            // migration chain". NO MIGRATION INSERTS A JOB GRADE: there is no
            // `JOB_GRADES` in `MigrationSeed`, and the only thing that seeds them
            // is the `DataSeeder` that `AbstractPostgresIT` replaces with a mock.
            // The table was EMPTY here. Being @Disabled against the defect below,
            // nobody ever ran it -- so the day somebody fixed that query and
            // un-skipped this, it would have failed `0 != 27` and read as "the fix
            // did not work". A pin on a fixture that is absent is worse than no
            // pin: it accuses the next correct change.
            //
            // Now the fixture is seeded for real -- `JobGradeSeeder` became a bean
            // for R2-F4, which is what made this possible -- and the expectation is
            // derived from the seed file, so this test can only fail for the defect
            // it names.
            var seedRows = JobGradeSeedFile.rows();
            JobGradeSeedFile.createBanksFor(ReferenceDataIT.this.bankRepository, seedRows);
            ReferenceDataIT.this.jobGradeSeeder.seed();

            Integer seeded = ReferenceDataIT.this.jdbc.queryForObject(
                    "SELECT count(*) FROM job_grades", Integer.class);

            assertThat(seeded)
                    .as("job grades the seed file describes, seeded by JobGradeSeeder")
                    .isEqualTo(seedRows.size());
        }
    }

    @Nested
    @DisplayName("DeletionCodeRepository")
    class Codes {

        @Test
        @DisplayName("findByParentIsNull returns the root reasons and not the sub-reasons")
        void findByParentIsNull() {
            DeletionCode root = new DeletionCode();
            root.setName("Resigned Society");
            ReferenceDataIT.this.deletionCodeRepository.save(root);

            DeletionCode child = new DeletionCode();
            child.setName("Financial");
            child.setParent(root);
            ReferenceDataIT.this.deletionCodeRepository.save(child);

            assertThat(ReferenceDataIT.this.deletionCodeRepository.findByParentIsNull())
                    .extracting(DeletionCode::getName)
                    .containsExactly("Resigned Society");
        }
    }

    @Nested
    @DisplayName("RecruitmentChannelRepository")
    class Channels {

        @Test
        @DisplayName("existsByNameIgnoreCase matches whatever case the caller sent")
        void existsByNameIgnoreCase() {
            RecruitmentChannel channel = new RecruitmentChannel();
            channel.setName("Walk in");
            ReferenceDataIT.this.recruitmentChannelRepository.save(channel);

            assertThat(ReferenceDataIT.this.recruitmentChannelRepository
                    .existsByNameIgnoreCase("walk in")).isTrue();
            assertThat(ReferenceDataIT.this.recruitmentChannelRepository
                    .existsByNameIgnoreCase("WALK IN")).isTrue();
            assertThat(ReferenceDataIT.this.recruitmentChannelRepository
                    .existsByNameIgnoreCase("Walk")).isFalse();
        }

        @Test
        @DisplayName("refuses a second channel with the same name")
        void nameIsUnique() {
            RecruitmentChannel first = new RecruitmentChannel();
            first.setName("Walk in");
            ReferenceDataIT.this.recruitmentChannelRepository.saveAndFlush(first);

            RecruitmentChannel duplicate = new RecruitmentChannel();
            duplicate.setName("Walk in");

            assertThatThrownBy(() -> ReferenceDataIT.this.recruitmentChannelRepository
                    .saveAndFlush(duplicate))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("RepresentativeRoleRepository")
    class Roles {

        @Test
        @DisplayName("the migration chain seeds exactly the roles it says it does")
        void theSeedIsIntact() {
            // The count comes from the one place that records what the
            // migrations seed, so this is a real assertion: the number is a
            // specification, not an observation of the database.
            assertThat(ReferenceDataIT.this.representativeRoleRepository.count())
                    .isEqualTo(MigrationSeed.REPRESENTATIVE_ROLES);
        }

        @Test
        @DisplayName("findByNameIgnoreCase matches a seeded role whatever case the caller sent")
        void findByNameIgnoreCase() {
            ReferenceDataIT.this.representativeRoleRepository.save(
                    MemberFixture.role("Branch Representative"));

            assertThat(ReferenceDataIT.this.representativeRoleRepository
                    .findByNameIgnoreCase("branch representative")).isPresent();
            assertThat(ReferenceDataIT.this.representativeRoleRepository
                    .findByNameIgnoreCase("Branch")).isEmpty();
        }
    }

    @Nested
    @DisplayName("UnionStructureRepository")
    class Structures {

        @Test
        @DisplayName("the migration chain seeds exactly the structures it says it does")
        void theSeedIsIntact() {
            assertThat(ReferenceDataIT.this.unionStructureRepository.count())
                    .isEqualTo(MigrationSeed.UNION_STRUCTURES);
        }

        @Test
        @DisplayName("findByNameIgnoreCase finds the root structure the seed guarantees")
        void findsTheSeededRoot() {
            // Uses the seeded row rather than one this test writes, so it also
            // proves the reset restored the seed rather than merely sparing it.
            assertThat(ReferenceDataIT.this.unionStructureRepository
                    .findByNameIgnoreCase(MigrationSeed.ROOT_STRUCTURE.toLowerCase()))
                    .isPresent();
        }

        @Test
        @DisplayName("refuses a second structure with the same name")
        void nameIsUnique() {
            ReferenceDataIT.this.unionStructureRepository.saveAndFlush(
                    MemberFixture.structure("Provincial Committee"));

            assertThatThrownBy(() -> ReferenceDataIT.this.unionStructureRepository
                    .saveAndFlush(MemberFixture.structure("Provincial Committee")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    /** Rebuilds the job grades with exactly {@code rows} entries on one bank. */
    private void seedGradesTo(int rows) {
        DatabaseReset.reset(this.jdbc);
        Bank bank = this.bankRepository.save(BankFixture.named("Absa"));
        for (int i = 1; i <= rows; i++) {
            this.saveGrade(bank, "BA" + i);
        }
    }

    private void saveGrade(Bank bank, String grade) {
        JobGrade jobGrade = new JobGrade();
        jobGrade.setBank(bank);
        jobGrade.setGrade(grade);
        this.jobGradeRepository.save(jobGrade);
    }
}
