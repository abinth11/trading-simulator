package com.tradingsim.matching.outbox;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Runs {@link OutboxRelay}'s polling loop. */
@Configuration
@EnableScheduling
public class OutboxConfig {
}
