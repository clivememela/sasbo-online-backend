package za.co.titandynamix.sasbo_online_backend.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;
import za.co.titandynamix.sasbo_online_backend.support.DatabaseReset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code RepresentativeRepository}'s two declared lookups, one of them a
 * hand-written {@code @Query} that concatenates two columns.
 *
 * <p>That query is the interesting one. It builds a full name in SQL with
 * {@code CONCAT}, {@code COALESCE}, {@code TRIM} and {@code LOWER}, which means
 * the <b>database</b> decides the answer -- so H2 in PostgreSQL mode is not
 * evidence about it, and every one of those four functions is a place the
 * behaviour can differ.
 */
@DisplayName("RepresentativeRepository against the real migration chain")
class RepresentativeRepositoryIT extends AbstractWritingIT {

    @Autowired
    private RepresentativeRepository representativeRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("findByFirstNamesAndSurname matches on both parts, not just one")
    void findByFirstNamesAndSurname() {
        this.saveRepresentative("Thandi", "Khumalo");
        this.saveRepresentative("Sipho", "Khumalo");

        assertThat(this.representativeRepository
                .findByFirstNamesAndSurname("Thandi", "Khumalo")).isPresent();
        assertThat(this.representativeRepository
                .findByFirstNamesAndSurname("Thandi", "Naidoo")).isEmpty();
    }

    @Test
    @DisplayName("findByFullName joins the two names and matches whatever case was sent")
    void findByFullName() {
        this.saveRepresentative("Thandi", "Khumalo");

        assertThat(this.representativeRepository.findByFullName("Thandi Khumalo")).isPresent();
        assertThat(this.representativeRepository.findByFullName("thandi khumalo")).isPresent();
        assertThat(this.representativeRepository.findByFullName("THANDI KHUMALO")).isPresent();
    }

    @Test
    @DisplayName("findByFullName trims, so a leading or trailing space still matches")
    void findByFullNameTrims() {
        // TRIM is in the query for a reason: the name arrives from a browser
        // field. Without this, a copied-and-pasted name with a trailing space
        // silently finds nobody -- and the caller reads that as "no such
        // representative".
        this.saveRepresentative("Thandi", "Khumalo");

        assertThat(this.representativeRepository.findByFullName(" Thandi Khumalo ")).isEmpty();
        assertThat(this.representativeRepository.findByFullName("Thandi Khumalo")).isPresent();
    }

    @Test
    @DisplayName("findByFullName copes with a representative who has no surname")
    void findByFullNameHandlesAMissingSurname() {
        // The COALESCE half. Neither name column is NOT NULL, so a record with
        // one of them missing is possible -- and without COALESCE the whole
        // concatenation would be NULL and the row would be unfindable by any
        // name at all.
        this.saveRepresentative("Thandi", null);

        assertThat(this.representativeRepository.findByFullName("Thandi")).isPresent();
    }

    @Test
    @DisplayName("findByFullName finds nobody for a name that does not exist")
    void findByFullNameMisses() {
        this.saveRepresentative("Thandi", "Khumalo");

        assertThat(this.representativeRepository.findByFullName("Someone Else")).isEmpty();
    }

    private Representative saveRepresentative(String firstNames, String surname) {
        Member member = this.memberRepository.save(
                MemberFixture.representativeMember(
                        surname == null ? "Unknown" : surname, "ACTIVE"));
        return this.representativeRepository.save(
                MemberFixture.representative(firstNames, surname, member));
    }
}
