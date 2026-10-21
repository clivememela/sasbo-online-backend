package za.co.titandynamix.sasbo_online_backend.documents;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/**
 * The letters' typeface, read once at start-up from the one setting, {@value LetterTypeface#PROPERTY} -- so a font
 * that cannot be used stops the start-up with the reason, instead of failing a member's letter later.
 */
@Slf4j
@Configuration
public class LetterTypefaceConfiguration {

    @Bean
    public LetterTypeface letterTypeface(ResourceLoader loader,
                                         @Value("${" + LetterTypeface.PROPERTY + ":" + LetterTypeface.BUNDLED + "}")
                                         String location) {
        LetterTypeface typeface = LetterTypeface.load(loader, location);
        log.info("Debit order letters are set in {} ({})", typeface.family(), typeface.location());
        return typeface;
    }
}
