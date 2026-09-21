package com.example.orderplatform.repository;

import com.example.orderplatform.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access layer for {@link Customer} backed by Spring Data JPA (Azure SQL).
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, String> {

    Optional<Customer> findByCustomerId(String customerId);
}
