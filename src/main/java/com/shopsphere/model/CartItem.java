package com.shopsphere.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// Materialize associations: the application reads entity fields directly.
@org.hibernate.annotations.Proxy(lazy = false)
@Entity(name = "CartItem")
@Table(
    name = "cart_items",
    uniqueConstraints = @UniqueConstraint(
        columnNames = { "cart_id", "product_id" }
    )
)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    public Cart cart;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    public Product product;

    @Column(nullable = false)
    public int quantity;
}
