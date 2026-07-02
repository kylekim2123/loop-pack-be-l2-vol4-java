package com.loopers.infrastructure.kafka;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KafkaMessagePublisher {

    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public RecordMetadata publish(String topic, String key, Object payload) {
        try {
            SendResult<Object, Object> sendResult = kafkaTemplate.send(topic, key, payload)
                .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            return sendResult.getRecordMetadata();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CoreException(ErrorType.INTERNAL_ERROR, "Kafka 발행이 중단되었습니다.");
        } catch (ExecutionException | TimeoutException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "Kafka 발행에 실패했습니다.");
        }
    }
}
