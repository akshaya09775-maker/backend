package app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Lets emails be sent in the background so requests never wait for the mail server. */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
