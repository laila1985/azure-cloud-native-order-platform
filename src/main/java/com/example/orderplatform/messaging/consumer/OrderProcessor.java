package com.example.orderplatform.messaging.consumer;

import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.orderplatform.messaging.MessagingResources;
import com.example.orderplatform.service.OrderService;
import org.springframework.stereotype.Component;

/**
 * Consumes the "order-processor" subscription and marks orders {@code PROCESSED}.
 */
@Component
public class OrderProcessor extends AbstractOrderConsumer {

    private final ServiceBusReceiverClient receiver;

    public OrderProcessor(ServiceBusReceiverClient processorReceiver,
                          ObjectMapper objectMapper,
                          OrderService orderService,
                          MessagingResources messagingResources) {
        super(objectMapper, orderService, messagingResources);
        this.receiver = processorReceiver;
    }

    @Override
    protected String consumerName() {
        return "order-processor";
    }

    @Override
    protected String targetStatus() {
        return "PROCESSED";
    }

    @Override
    protected ServiceBusReceiverClient receiver() {
        return receiver;
    }
}
