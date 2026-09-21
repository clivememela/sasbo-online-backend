package za.co.titandynamix.sasbo_online_backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the production marker and says, in one line, which mode this start is in -- the first thing
 * anyone will want to read in the service's log on the day production goes live (SD4-I).
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SasboEnvironment.class)
public class EnvironmentConfig {

    public EnvironmentConfig(SasboEnvironment environment) {
        log.info(environment.describe());
    }
}
