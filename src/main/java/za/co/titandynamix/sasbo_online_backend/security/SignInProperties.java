package za.co.titandynamix.sasbo_online_backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sign-in's settings, {@code sasbo.signin.*} -- each bound explicitly in {@code application.yaml}.
 *
 * @param mode           {@code open} (the default) or {@code required} -- {@link SignInMode}; anything else stops the
 *                       start ({@code SASBO_SIGNIN})
 * @param demoUsers      seed one demonstration user per provisional position -- only where this is {@code true}, a
 *                       {@code demoPassword} is set, and the production marker is not. No hosted stack sets it: the
 *                       link is public (signin.md) ({@code SASBO_SIGNIN_DEMO_USERS})
 * @param demoPassword   the demonstration users' one password, from the environment only -- never a default in code or
 *                       in any committed file ({@code SASBO_SIGNIN_DEMO_PASSWORD}); the suite's compose and a local run
 *                       set it
 * @param bootstrapAdmin the first System Administrator, created when there are no users at all and both of its values
 *                       are set ({@code SASBO_BOOTSTRAP_ADMIN_USERNAME}, {@code SASBO_BOOTSTRAP_ADMIN_PASSWORD}) -- set
 *                       on a host by the owner, never committed
 */
@ConfigurationProperties(prefix = "sasbo.signin")
public record SignInProperties(String mode, boolean demoUsers, String demoPassword, BootstrapAdmin bootstrapAdmin) {

    public SignInProperties {
        SignInMode.parse(mode);
        bootstrapAdmin = bootstrapAdmin == null ? new BootstrapAdmin(null, null) : bootstrapAdmin;
    }

    /** Sign-in's mode, parsed. */
    public SignInMode signInMode() {
        return SignInMode.parse(mode);
    }

    /** Whether a demonstration password is set at all. */
    public boolean demoPasswordSet() {
        return demoPassword != null && !demoPassword.isEmpty();
    }

    /** 🔒 Never prints a password -- a record would print every component. */
    @Override
    public String toString() {
        return "SignInProperties[mode=" + signInMode().word() + ", demoUsers=" + demoUsers + ", demoPassword="
                + (demoPasswordSet() ? "set" : "unset") + ", bootstrapAdmin=" + bootstrapAdmin + "]";
    }

    /** The first administrator's sign-in, from the host's environment. */
    public record BootstrapAdmin(String username, String password) {

        /** Both values given: a username and a password. */
        public boolean configured() {
            return username != null && !username.isBlank() && password != null && !password.isEmpty();
        }

        /** Exactly one of the two given -- a host set up halfway. */
        public boolean halfConfigured() {
            boolean name = username != null && !username.isBlank();
            boolean secret = password != null && !password.isEmpty();
            return name != secret;
        }

        /** 🔒 Never prints the password -- a record would print every component. */
        @Override
        public String toString() {
            return "BootstrapAdmin[username=" + (username == null || username.isBlank() ? "unset" : "set")
                    + ", password=" + (password == null || password.isEmpty() ? "unset" : "set") + "]";
        }
    }
}
