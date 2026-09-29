package za.co.titandynamix.sasbo_online_backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the production marker and says, in one line, which mode this start is in -- the first thing
 * anyone will want to read in the service's log on the day production goes live (SD4-I).
 *
 * <p>Month end's switch is bound here too, and said in a second line: whether SASBO's own workplaces are on this
 * deployment is the other thing the person reading a hosted start needs to know first (MD45, MD62).
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({SasboEnvironment.class, WorkplaceSwitch.class})
public class EnvironmentConfig {

    public EnvironmentConfig(SasboEnvironment environment, WorkplaceSwitch workplaces) {
        log.info(environment.describe());
        log.info(workplaces.describe());
    }
}
