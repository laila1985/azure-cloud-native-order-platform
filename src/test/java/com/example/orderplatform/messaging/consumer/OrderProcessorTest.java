package com.example.orderplatform.messaging.consumer;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.orderplatform.messaging.MessagingResources;
import com.example.orderplatform.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderProcessorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private ServiceBusReceiverClient receiver;

    @Mock
    private OrderService orderService;

    @Mock
    private MessagingResources messagingResources;

    private OrderProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new OrderProcessor(receiver, OBJECT_MAPPER, orderService, messagingResources);
        when(messagingResources.isReady()).thenReturn(true);
    }

    @Test
    @DisplayName("poll() marks an order PROCESSED and completes the message")
    void poll_marksOrderProcessedAndCompletesMessage() throws Exception {
        String body = "{\"orderId\":\"id-1\",\"customerId\":\"cust-1\",\"status\":\"CREATED\"}";
        ServiceBusReceivedMessage message = mockMessage(body);

        processor.poll();

        verify(orderService).updateStatus("id-1", "PROCESSED");
        verify(receiver).complete(message);
    }

    @Test
    @DisplayName("poll() abandons a message that fails to process")
    void poll_abandonsMessageOnFailure() throws Exception {
        String body = "{invalid json";
        ServiceBusReceivedMessage message = mockMessage(body);

        processor.poll();

        verify(receiver).abandon(message);
    }

    private ServiceBusReceivedMessage mockMessage(String body) {
        ServiceBusReceivedMessage message = org.mockito.Mockito.mock(ServiceBusReceivedMessage.class);
        when(message.getBody()).thenReturn(com.azure.core.util.BinaryData.fromString(body));
        // Return a single-element stream from receiveMessages.
        com.azure.core.util.IterableStream<ServiceBusReceivedMessage> stream =
                new com.azure.core.util.IterableStream<>(java.util.List.of(message));
        when(receiver.receiveMessages(eq(10), any(java.time.Duration.class))).thenReturn(stream);
        return message;
    }
}
