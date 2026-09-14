package za.co.titandynamix.sasbo_online_backend.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import za.co.titandynamix.sasbo_online_backend.entity.location.City;
import za.co.titandynamix.sasbo_online_backend.entity.location.Province;
import za.co.titandynamix.sasbo_online_backend.entity.location.Town;
import za.co.titandynamix.sasbo_online_backend.fixtures.LocationFixture;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The five location repositories, in one class because they share one
 * hierarchy: a suburb test needs a town, which needs a city, which needs a
 * province. Splitting them into five files would mean building the same chain
 * five times, and each copy would be a place for the fixtures to drift.
 *
 * <p>Each repository gets its own {@code @Nested} block, so a failure still
 * names one repository rather than "the location tests".
 *
 * <p>Only the <b>declared</b> query methods are covered. Spring Data wrote
 * {@code findById} and {@code save}, and they are its tests to fail, not ours.
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
@DisplayName("The location repositories against the real migration chain")
class LocationIT extends AbstractWritingIT {

    @Autowired
    private ProvinceRepository provinceRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private TownRepository townRepository;

    @Autowired
    private SuburbRepository suburbRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Nested
    @DisplayName("ProvinceRepository")
    class Provinces {

        @Test
        @DisplayName("findByCode matches the exact code, and returns null when nothing does")
        void findByCode() {
            // Note the shape: this method returns the entity, not an Optional,
            // so a miss is a null the caller has to remember to check. Asserted
            // both ways because the null branch is the one that reaches
            // production as a NullPointerException.
            LocationIT.this.provinceRepository.save(
                    LocationFixture.province("Gauteng", "GP"));

            assertThat(LocationIT.this.provinceRepository.findByCode("GP"))
                    .isNotNull();
            assertThat(LocationIT.this.provinceRepository.findByCode("ZZ"))
                    .isNull();
        }

        @Test
        @DisplayName("findByCode is case sensitive, unlike the name lookups elsewhere")
        void findByCodeIsCaseSensitive() {
            // Worth pinning because every other lookup in this application is
            // IgnoreCase. A caller moving between them will assume this one is
            // too, and it silently is not.
            LocationIT.this.provinceRepository.save(
                    LocationFixture.province("Gauteng", "GP"));

            assertThat(LocationIT.this.provinceRepository.findByCode("gp"))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("CityRepository")
    class Cities {

        @Test
        @DisplayName("findByProvinceId returns only that province's cities")
        void findByProvinceId() {
            // The negative half matters more than the positive one: a derived
            // query with the filter dropped returns everything, and a test that
            // only seeds one province cannot tell the difference.
            Province gauteng = LocationIT.this.provinceRepository.save(
                    LocationFixture.province("Gauteng", "GP"));
            Province limpopo = LocationIT.this.provinceRepository.save(
                    LocationFixture.province("Limpopo", "LP"));

            LocationIT.this.cityRepository.save(
                    LocationFixture.city("Pretoria", gauteng));
            LocationIT.this.cityRepository.save(
                    LocationFixture.city("Johannesburg", gauteng));
            LocationIT.this.cityRepository.save(
                    LocationFixture.city("Polokwane", limpopo));

            assertThat(LocationIT.this.cityRepository.findByProvinceId(gauteng.getId()))
                    .extracting(City::getName)
                    .containsExactlyInAnyOrder("Pretoria", "Johannesburg");
        }

        @Test
        @DisplayName("a city cannot exist without a province")
        void provinceIsRequired() {
            City orphan = LocationFixture.city("Nowhere", null);

            assertThatThrownBy(() ->
                    LocationIT.this.cityRepository.saveAndFlush(orphan))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("TownRepository")
    class Towns {

        @Test
        @DisplayName("findByCityId returns only that city's towns")
        void findByCityId() {
            Province province = LocationIT.this.provinceRepository.save(
                    LocationFixture.aProvince());
            City pretoria = LocationIT.this.cityRepository.save(
                    LocationFixture.city("Pretoria", province));
            City johannesburg = LocationIT.this.cityRepository.save(
                    LocationFixture.city("Johannesburg", province));

            LocationIT.this.townRepository.save(
                    LocationFixture.town("Centurion", pretoria));
            LocationIT.this.townRepository.save(
                    LocationFixture.town("Sandton", johannesburg));

            assertThat(LocationIT.this.townRepository.findByCityId(pretoria.getId()))
                    .extracting(Town::getName)
                    .containsExactly("Centurion");
        }
    }

    @Nested
    @DisplayName("SuburbRepository")
    class Suburbs {

        @Test
        @DisplayName("findByTownId returns only that town's suburbs")
        void findByTownId() {
            Town sandton = LocationIT.this.aTown("Sandton");
            Town centurion = LocationIT.this.aTown("Centurion");

            LocationIT.this.suburbRepository.save(
                    LocationFixture.suburb("Rivonia", "2128", sandton));
            LocationIT.this.suburbRepository.save(
                    LocationFixture.suburb("Lyttelton", "0157", centurion));

            assertThat(LocationIT.this.suburbRepository
                    .findByTownId(sandton.getId()))
                    .hasSize(1);
        }

        @Test
        @DisplayName("findByPostalCode finds every suburb sharing a code, across towns")
        void findByPostalCode() {
            // South African postal codes are not unique to a suburb, so this
            // method has to return a list and callers have to expect more than
            // one. A fixture with a single match would hide that.
            Town sandton = LocationIT.this.aTown("Sandton");
            Town centurion = LocationIT.this.aTown("Centurion");

            LocationIT.this.suburbRepository.save(
                    LocationFixture.suburb("Rivonia", "2128", sandton));
            LocationIT.this.suburbRepository.save(
                    LocationFixture.suburb("Wendywood", "2128", centurion));
            LocationIT.this.suburbRepository.save(
                    LocationFixture.suburb("Lyttelton", "0157", centurion));

            assertThat(LocationIT.this.suburbRepository.findByPostalCode("2128"))
                    .hasSize(2);
            assertThat(LocationIT.this.suburbRepository.findByPostalCode("9999"))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("AreaRepository")
    class Areas {

        @Test
        @DisplayName("findByNameIgnoreCase matches whatever case the caller sent")
        void findByNameIgnoreCase() {
            LocationIT.this.areaRepository.save(
                    LocationFixture.area("Northern Gauteng"));

            assertThat(LocationIT.this.areaRepository
                    .findByNameIgnoreCase("northern gauteng")).isPresent();
            assertThat(LocationIT.this.areaRepository
                    .findByNameIgnoreCase("NORTHERN GAUTENG")).isPresent();
        }

        @Test
        @DisplayName("findByNameIgnoreCase is exact about everything except case")
        void findByNameIsNotASubstringMatch() {
            LocationIT.this.areaRepository.save(
                    LocationFixture.area("Northern Gauteng"));

            assertThat(LocationIT.this.areaRepository
                    .findByNameIgnoreCase("Northern")).isEmpty();
        }

        @Test
        @DisplayName("refuses a second area with the same name")
        void nameIsUnique() {
            LocationIT.this.areaRepository.saveAndFlush(
                    LocationFixture.area("Northern Gauteng"));

            assertThatThrownBy(() -> LocationIT.this.areaRepository
                    .saveAndFlush(LocationFixture.area("Northern Gauteng")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    /** A saved town, with the province and city it needs behind it. */
    private Town aTown(String name) {
        Province province = this.provinceRepository.save(
                LocationFixture.province(name + " Province", name.substring(0, 2).toUpperCase()));
        City city = this.cityRepository.save(LocationFixture.city(name + " City", province));
        return this.townRepository.save(LocationFixture.town(name, city));
    }
}
