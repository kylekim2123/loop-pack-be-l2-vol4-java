package com.loopers.interfaces.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueProcessor;
import com.loopers.application.event.CouponIssueRequestedEvent;
import com.loopers.application.event.CouponIssueRequestedEventParser;
import com.loopers.confg.kafka.KafkaConfig;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private static final String COUPON_ISSUE_REQUESTS_TOPIC = "coupon-issue-requests";
    private static final String GROUP_ID = "coupon-issue";

    private final CouponIssueProcessor couponIssueProcessor;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = COUPON_ISSUE_REQUESTS_TOPIC,
        groupId = GROUP_ID,
        containerFactory = KafkaConfig.SINGLE_LISTENER
    )
    public void consume(ConsumerRecord<Object, Object> message, Acknowledgment acknowledgment) throws JsonProcessingException {
        CouponIssueRequestedEvent event = CouponIssueRequestedEventParser.parse(objectMapper.readTree(String.valueOf(message.value())));
        couponIssueProcessor.process(event);

        acknowledgment.acknowledge();
    }
}
