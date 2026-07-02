package com.loopers.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Map;
import java.util.stream.IntStream;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;

import com.loopers.support.config.KafkaTopicConfig;

@SpringBootTest
class KafkaMessagePublisherIntegrationTest {

    @Autowired
    private KafkaMessagePublisher kafkaMessagePublisher;

    @Autowired
    private ProducerFactory<Object, Object> producerFactory;

    private record CatalogTestMessage(Long productId, String eventType) {
    }

    @DisplayName("같은 키의 메시지는 항상 같은 파티션으로 발행된다.")
    @Test
    void routesToSamePartition_whenKeyIsSame() {
        // arrange
        String key = "1";

        // act
        var partitions = IntStream.range(0, 10)
            .map(sequence -> kafkaMessagePublisher
                .publish(KafkaTopicConfig.CATALOG_EVENTS_TOPIC, key, new CatalogTestMessage(1L, "LIKE_CREATED"))
                .partition())
            .distinct()
            .boxed()
            .toList();

        // assert
        assertThat(partitions).hasSize(1);
    }

    @DisplayName("Producer는 acks=all과 멱등 발행이 설정되어 있다.")
    @Test
    void configuresAcksAllAndIdempotence() {
        // arrange
        Map<String, Object> configurationProperties = ((DefaultKafkaProducerFactory<Object, Object>) producerFactory)
            .getConfigurationProperties();

        // assert
        assertAll(
            () -> assertThat(configurationProperties.get(ProducerConfig.ACKS_CONFIG)).isEqualTo("all"),
            () -> assertThat(String.valueOf(configurationProperties.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)))
                .isEqualTo("true")
        );
    }
}
