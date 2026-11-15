package com.shopsphere.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

// Materialize associations: the application reads entity fields directly.
@org.hibernate.annotations.Proxy(lazy = false)
@Entity(name = "Product")
@Table(name = "products")
public class Product {

    // Use the getter when identifying associated products, even if Hibernate uses a proxy.
    public Long getId() {
        return id;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 160)
    public String name;

    @Column(nullable = false, length = 4000)
    public String description;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal price;

    @Column(nullable = false)
    public int stock;

    @Column(length = 1000)
    public String imageUrl;

    @Column(nullable = false)
    public boolean featured;

    @Column(nullable = false)
    public boolean active = true;

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    public Category category;
}
