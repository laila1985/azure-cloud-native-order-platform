package com.example.orderplatform.messaging.consumer;

import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.orderplatform.messaging.MessagingResources;
import com.example.orderplatform.service.OrderService;
import org.springframework.stereotype.Component;

/**
 * Consumes the "order-shipping" subscription and marks orders {@code SHIPPING}.
 */
@Component
public class ShippingProcessor extends AbstractOrderConsumer {

    private final ServiceBusReceiverClient receiver;

    public ShippingProcessor(ServiceBusReceiverClient shippingReceiver,
                             ObjectMapper objectMapper,
                             OrderService orderService,
                             MessagingResources messagingResources) {
        super(objectMapper, orderService, messagingResources);
        this.receiver = shippingReceiver;
    }

    @Override
    protected String consumerName() {
        return "order-shipping";
    }

    @Override
    protected String targetStatus() {
        return "SHIPPING";
    }

    @Override
    protected ServiceBusReceiverClient receiver() {
        return receiver;
    }
}
