package com.shopsphere.repository;

import com.shopsphere.model.Order;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @EntityGraph(attributePaths = { "items" })
    List<Order> findByUserIdOrderByIdDesc(Long id);

    @EntityGraph(attributePaths = { "items" })
    List<Order> findAllByOrderByIdDesc();

    @EntityGraph(attributePaths = { "items" })
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ShopOrder o where o.id=:id")
    Optional<Order> lock(@Param("id") Long id);
}
