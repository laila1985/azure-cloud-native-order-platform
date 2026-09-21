package com.example.orderplatform.integration;

import com.example.orderplatform.model.Customer;
import com.example.orderplatform.model.Order;
import com.example.orderplatform.model.OrderItem;
import com.example.orderplatform.repository.CustomerRepository;
import com.example.orderplatform.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-level integration test against H2 (MSSQL compatibility mode).
 * Verifies the Spring Data JPA mappings and CRUD operations.
 */
@DataJpaTest
class OrderRepositoryIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private Order order(String id, String customerId) {
        Order order = new Order();
        order.setOrderId(id);
        order.setCustomerId(customerId);
        order.setStatus("CREATED");

        OrderItem item = new OrderItem();
        item.setProductId("p-1");
        item.setProductName("Laptop");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("10.50"));
        order.setItems(List.of(item));
        order.setTotalAmount(new BigDecimal("21.00"));
        return order;
    }

    @Test
    @DisplayName("save then findByOrderId round-trips an order with its items")
    void saveAndFindById() {
        orderRepository.save(order("int-1", "cust-1"));

        Optional<Order> found = orderRepository.findByOrderId("int-1");

        assertThat(found).isPresent();
        assertThat(found.get().getCustomerId()).isEqualTo("cust-1");
        assertThat(found.get().getItems()).hasSize(1);
        assertThat(found.get().getTotalAmount()).isEqualByComparingTo("21.00");
    }

    @Test
    @DisplayName("findByOrderId returns empty for an unknown order")
    void findById_returnsEmptyWhenMissing() {
        assertThat(orderRepository.findByOrderId("does-not-exist")).isEmpty();
    }

    @Test
    @DisplayName("findAll returns all saved orders")
    void findAll_returnsAllOrders() {
        orderRepository.save(order("int-2", "cust-2"));
        orderRepository.save(order("int-3", "cust-3"));

        List<Order> all = orderRepository.findAll();

        assertThat(all).extracting(Order::getOrderId)
                .contains("int-2", "int-3");
    }

    @Test
    @DisplayName("deleteByOrderId removes an order")
    void delete_removesOrder() {
        orderRepository.save(order("int-4", "cust-4"));
        assertThat(orderRepository.findByOrderId("int-4")).isPresent();

        orderRepository.deleteByOrderId("int-4");

        assertThat(orderRepository.findByOrderId("int-4")).isEmpty();
    }

    @Test
    @DisplayName("customer save then findByCustomerId round-trips")
    void customerSaveAndFind() {
        Customer customer = new Customer();
        customer.setCustomerId("cust-10");
        customer.setName("Alice");
        customer.setEmail("alice@example.com");
        customerRepository.save(customer);

        assertThat(customerRepository.findByCustomerId("cust-10")).isPresent();
    }
}
