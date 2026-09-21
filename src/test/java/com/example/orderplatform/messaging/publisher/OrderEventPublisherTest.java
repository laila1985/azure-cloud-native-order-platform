package com.example.orderplatform.messaging.publisher;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.orderplatform.event.OrderCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Mock
    private ServiceBusSenderClient sender;

    @Test
    @DisplayName("publish() sends the event JSON to the Service Bus topic")
    void publish_sendsJsonToTopic() throws Exception {
        OrderEventPublisher publisher = new OrderEventPublisher(sender, OBJECT_MAPPER);
        OrderCreatedEvent event = new OrderCreatedEvent(
                "id-1", "cust-1", "CREATED", new BigDecimal("25.50"), Instant.parse("2026-01-01T00:00:00Z"));

        publisher.publish(event);

        ArgumentCaptor<ServiceBusMessage> captor = ArgumentCaptor.forClass(ServiceBusMessage.class);
        verify(sender).sendMessage(captor.capture());

        OrderCreatedEvent parsed = OBJECT_MAPPER.readValue(captor.getValue().getBody().toString(), OrderCreatedEvent.class);
        assertThat(parsed.orderId()).isEqualTo("id-1");
        assertThat(parsed.totalAmount()).isEqualByComparingTo("25.50");
    }

    @Test
    @DisplayName("publish() swallows Service Bus errors and does not propagate")
    void publish_swallowsErrors() {
        doThrow(new RuntimeException("Service Bus down")).when(sender).sendMessage(any(ServiceBusMessage.class));

        OrderEventPublisher publisher = new OrderEventPublisher(sender, OBJECT_MAPPER);
        // must not throw
        publisher.publish(new OrderCreatedEvent("id-1", "cust-1", "CREATED", null, null));
    }
}
