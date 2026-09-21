package com.example.orderplatform.repository;

import com.example.orderplatform.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access layer for {@link Order} backed by Spring Data JPA (Azure SQL).
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    Optional<Order> findByOrderId(String orderId);

    void deleteByOrderId(String orderId);
}
