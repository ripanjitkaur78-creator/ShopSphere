package com.shopsphere.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// Materialize associations: the application reads entity fields directly.
@org.hibernate.annotations.Proxy(lazy = false)
@Entity(name = "Cart")
@Table(name = "carts")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @JsonIgnore
    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    public User user;

    @OneToMany(
        mappedBy = "cart",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    public List<CartItem> items = new ArrayList<>();

    @Transient
    public BigDecimal getTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : items) {
            BigDecimal lineTotal = item.product.price.multiply(
                BigDecimal.valueOf(item.quantity)
            );
            total = total.add(lineTotal);
        }
        return total;
    }
}
