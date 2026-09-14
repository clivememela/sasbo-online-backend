package za.co.titandynamix.sasbo_online_backend.fixtures;

import za.co.titandynamix.sasbo_online_backend.entity.location.Area;
import za.co.titandynamix.sasbo_online_backend.entity.location.City;
import za.co.titandynamix.sasbo_online_backend.entity.location.Province;
import za.co.titandynamix.sasbo_online_backend.entity.location.Suburb;
import za.co.titandynamix.sasbo_online_backend.entity.location.Town;

/**
 * The location domain's fixture builders -- province, city, town, suburb, area.
 *
 * <p>One builder per type, each returning a <b>valid baseline</b>, so a test
 * overrides only the field it is about. The hierarchy is province to city to
 * town to suburb, and each level requires its parent, so the builders take it
 * rather than inventing one: a fixture that quietly creates its own parent
 * makes a test that looks like it covers two rows actually cover four.
 *
 * <p>Every name is collation-neutral -- plainly separated ASCII, no leading
 * lowercase particle, no apostrophe, hyphen or diacritic, no case-only pair.
 * These are real South African place names that happen to satisfy that, chosen
 * because {@code C}, {@code en_ZA.UTF-8} and {@code en_US.UTF-8} all agree on
 * their order.
 */
public final class LocationFixture {

    /** Provinces in an order all three collations agree on. */
    public static final String[] COLLATION_NEUTRAL_PROVINCES = {
        "Free State", "Gauteng", "Limpopo", "Mpumalanga",
    };

    private LocationFixture() {
    }

    public static Province province(String name, String code) {
        Province province = new Province();
        province.setName(name);
        province.setCode(code);
        return province;
    }

    public static Province aProvince() {
        return province("Gauteng", "GP");
    }

    public static City city(String name, Province province) {
        City city = new City();
        city.setName(name);
        city.setProvince(province);
        return city;
    }

    public static Town town(String name, City city) {
        Town town = new Town();
        town.setName(name);
        town.setCity(city);
        return town;
    }

    public static Suburb suburb(String name, String postalCode, Town town) {
        Suburb suburb = new Suburb();
        suburb.setName(name);
        suburb.setPostalCode(postalCode);
        suburb.setTown(town);
        return suburb;
    }

    public static Area area(String name) {
        Area area = new Area();
        area.setName(name);
        return area;
    }
}
