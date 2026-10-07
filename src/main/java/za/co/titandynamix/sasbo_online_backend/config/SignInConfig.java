package za.co.titandynamix.sasbo_online_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;

/**
 * What sign-in is wired with (October build, contracts/signin.md): how a password is hashed, and where a session token
 * and a temporary password come from.
 */
@Configuration
public class SignInConfig {

    /** BCrypt at strength 12 (signin.md): about a quarter of a second a check on a server core -- slow for a guesser. */
    public static final int BCRYPT_STRENGTH = 12;

    @Bean
    public PasswordEncoder passwordEncoder(SecureRandom signInRandom) {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH, signInRandom);
    }

    /** The one source of tokens, salts and temporary passwords: the platform's strong generator. */
    @Bean
    public SecureRandom signInRandom() {
        return new SecureRandom();
    }
}
