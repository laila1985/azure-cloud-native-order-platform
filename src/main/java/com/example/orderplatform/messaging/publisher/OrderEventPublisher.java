package com.example.orderplatform.messaging.publisher;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.orderplatform.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Publishes order events to the Service Bus topic (fire-and-forget). The topic
 * fans the message out to the five subscriptions (processor / lambda / email /
 * sms / shipping).
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final ServiceBusSenderClient sender;
    private final ObjectMapper objectMapper;

    public OrderEventPublisher(ServiceBusSenderClient sender, ObjectMapper objectMapper) {
        this.sender = sender;
        this.objectMapper = objectMapper;
    }

    public void publish(OrderCreatedEvent event) {
        try {
            String body = objectMapper.writeValueAsString(event);
            sender.sendMessage(new ServiceBusMessage(body));
            log.info("Published OrderCreatedEvent for order {}", event.orderId());
        } catch (Exception e) {
            // Publishing is best-effort; it must not fail the order creation flow.
            log.error("Failed to publish OrderCreatedEvent for order {}", event.orderId(), e);
        }
    }
}
