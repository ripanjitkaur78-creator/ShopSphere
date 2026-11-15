package com.shopsphere.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Materialize associations: the application reads entity fields directly.
@org.hibernate.annotations.Proxy(lazy = false)
@Entity(name = "ShopOrder")
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    public User user;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Status status = Status.PLACED;

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal total;

    @Column(nullable = false, length = 80)
    public String shippingName;

    @Column(nullable = false, length = 500)
    public String shippingAddress;

    @Column(nullable = false, length = 25)
    public String phone;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    public List<OrderItem> items = new ArrayList<>();
}
