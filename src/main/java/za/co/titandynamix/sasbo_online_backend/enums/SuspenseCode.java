package za.co.titandynamix.sasbo_online_backend.enums;

public enum SuspenseCode {
    LEFT_SCHEME(1, "Left Scheme");

    private final int code;
    private final String reason;

    SuspenseCode(int code, String reason) {
        this.code = code;
        this.reason = reason;
    }
}
