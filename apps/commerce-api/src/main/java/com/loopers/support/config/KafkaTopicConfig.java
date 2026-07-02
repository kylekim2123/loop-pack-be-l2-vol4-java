package com.loopers.support.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {

    public static final String CATALOG_EVENTS_TOPIC = "catalog-events";
    public static final String ORDER_EVENTS_TOPIC = "order-events";
    public static final String COUPON_ISSUE_REQUESTS_TOPIC = "coupon-issue-requests";

    private static final int DEFAULT_PARTITION_COUNT = 3;
    private static final int SEQUENTIAL_PARTITION_COUNT = 1;
    private static final short REPLICATION_FACTOR = 1;

    @Bean
    public NewTopic catalogEventsTopic() {
        return new NewTopic(CATALOG_EVENTS_TOPIC, DEFAULT_PARTITION_COUNT, REPLICATION_FACTOR);
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return new NewTopic(ORDER_EVENTS_TOPIC, DEFAULT_PARTITION_COUNT, REPLICATION_FACTOR);
    }

    @Bean
    public NewTopic couponIssueRequestsTopic() {
        return new NewTopic(COUPON_ISSUE_REQUESTS_TOPIC, SEQUENTIAL_PARTITION_COUNT, REPLICATION_FACTOR);
    }
}
