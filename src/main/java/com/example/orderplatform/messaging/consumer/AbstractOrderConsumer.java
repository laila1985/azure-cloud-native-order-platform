package com.example.orderplatform.messaging.consumer;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.orderplatform.event.OrderCreatedEvent;
import com.example.orderplatform.exception.OrderNotFoundException;
import com.example.orderplatform.messaging.MessagingResources;
import com.example.orderplatform.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Service Bus subscription consumer that processes {@link OrderCreatedEvent}s
 * from a subscription. On receipt it marks the order status (via the
 * cache-aware {@link OrderService#updateStatus}).
 */
public abstract class AbstractOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(AbstractOrderConsumer.class);

    private final ObjectMapper objectMapper;
    private final OrderService orderService;
    private final MessagingResources messagingResources;

    protected AbstractOrderConsumer(ObjectMapper objectMapper,
                                    OrderService orderService,
                                    MessagingResources messagingResources) {
        this.objectMapper = objectMapper;
        this.orderService = orderService;
        this.messagingResources = messagingResources;
    }

    /**
     * Receives and processes messages, completing (or abandoning) each one.
     * Implementations supply their receiver and the target status.
     */
    @Scheduled(fixedDelayString = "${azure.servicebus.poll-interval-ms:5000}")
    public void poll() {
        if (!messagingResources.isReady()) {
            return;
        }
        var receiver = receiver();
        if (receiver == null) {
            return;
        }
        try {
            receiver.receiveMessages(10, java.time.Duration.ofSeconds(5)).stream()
                    .forEach(message -> process(message, receiver));
        } catch (Exception e) {
            log.warn("[{}] Failed to receive messages", consumerName(), e);
        }
    }

    private void process(ServiceBusReceivedMessage message, com.azure.messaging.servicebus.ServiceBusReceiverClient receiver) {
        try {
            OrderCreatedEvent event = parseEvent(message.getBody().toString());
            try {
                orderService.updateStatus(event.orderId(), targetStatus());
                log.info("[{}] Order {} marked {}", consumerName(), event.orderId(), targetStatus());
            } catch (OrderNotFoundException e) {
                log.warn("[{}] Order {} not found; skipping {} update",
                        consumerName(), event.orderId(), targetStatus());
            }
            receiver.complete(message);
        } catch (Exception e) {
            log.error("[{}] Failed to process message {}", consumerName(), message.getMessageId(), e);
            try {
                receiver.abandon(message);
            } catch (Exception abandonEx) {
                log.warn("[{}] Failed to abandon message {}", consumerName(), message.getMessageId(), abandonEx);
            }
        }
    }

    private OrderCreatedEvent parseEvent(String body) throws Exception {
        return objectMapper.readValue(body, OrderCreatedEvent.class);
    }

    protected abstract String consumerName();

    protected abstract String targetStatus();

    protected abstract com.azure.messaging.servicebus.ServiceBusReceiverClient receiver();
}
