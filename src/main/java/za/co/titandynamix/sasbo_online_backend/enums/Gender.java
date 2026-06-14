package za.co.titandynamix.sasbo_online_backend.enums;

public enum Gender {
    // 1. Define constants with values in parentheses
    MALE(1, "Male"),
    FEMALE(2, "Female");

    private final int genderCode;
    private final String gender;

    Gender(int genderCode,String gender) {
        this.genderCode = genderCode;
        this.gender = gender;
    }
}
