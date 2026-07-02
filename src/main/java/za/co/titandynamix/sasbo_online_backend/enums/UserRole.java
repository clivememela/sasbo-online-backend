package za.co.titandynamix.sasbo_online_backend.enums;

public enum UserRole {
    ADMIN(1,"Administrator"),
    RECRUITER(2,"Recruiter"),
    MEMBER(3, "Member");

    UserRole(int userRoleCode, String userRole) {
    }
}
