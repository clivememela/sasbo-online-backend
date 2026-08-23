package za.co.titandynamix.sasbo_online_backend.enums;

import lombok.Getter;

/**
 * Gender enumeration for member demographics.
 */
@Getter
public enum Gender {
    MALE(1, "Male"),
    FEMALE(2, "Female");

    private final int genderCode;
    private final String displayName;

    Gender(int genderCode, String displayName) {
        this.genderCode = genderCode;
        this.displayName = displayName;
    }
}
