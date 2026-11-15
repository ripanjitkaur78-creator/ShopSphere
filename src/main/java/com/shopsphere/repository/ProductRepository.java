package com.shopsphere.repository;

import com.shopsphere.model.Product;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query(
        "select p from Product p where p.active=true and (:category is null or p.category.id=:category) and (lower(p.name) like lower(concat('%',:q,'%')) or lower(p.description) like lower(concat('%',:q,'%'))) order by p.id desc"
    )
    List<Product> search(
        @Param("q") String q,
        @Param("category") Long category
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id=:id")
    Optional<Product> lock(@Param("id") Long id);
}
