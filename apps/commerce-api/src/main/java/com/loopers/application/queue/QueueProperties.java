package com.loopers.application.queue;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(value = "queue")
public record QueueProperties(int batchSize, Duration tokenTtl) {
}
