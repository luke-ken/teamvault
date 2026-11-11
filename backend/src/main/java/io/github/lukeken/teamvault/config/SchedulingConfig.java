package io.github.lukeken.teamvault.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled}. Its own class so a slice test never gets a scheduler by
 * importing something else; the first job is the refresh token sweep (ADR-007).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

}
