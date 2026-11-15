package com.shopsphere;

import com.shopsphere.dto.AddItemRequest;
import com.shopsphere.dto.CategoryRequest;
import com.shopsphere.dto.CheckoutRequest;
import com.shopsphere.dto.ProductRequest;
import com.shopsphere.dto.QuantityRequest;
import com.shopsphere.dto.RegistrationRequest;
import com.shopsphere.dto.StatusRequest;
import com.shopsphere.model.Cart;
import com.shopsphere.model.Category;
import com.shopsphere.model.Order;
import com.shopsphere.model.Product;
import com.shopsphere.model.User;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final ShopService shop;

    public ApiController(ShopService shop) {
        this.shop = shop;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of(
            "token",
            token.getToken(),
            "headerName",
            token.getHeaderName()
        );
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public User register(@Valid @RequestBody RegistrationRequest request) {
        return shop.register(request);
    }

    @GetMapping("/auth/me")
    public User me(Principal principal) {
        return shop.user(principal.getName());
    }

    @GetMapping("/categories")
    public List<Category> categories() {
        return shop.listCategories();
    }

    @GetMapping("/products")
    public List<Product> products(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(required = false) Long category
    ) {
        return shop.searchProducts(q, category);
    }

    @GetMapping("/products/{id}")
    public Product product(@PathVariable Long id) {
        return shop.available(id);
    }

    @GetMapping("/cart")
    public Cart cart(Principal principal) {
        return shop.cart(principal.getName());
    }

    @PostMapping("/cart/items")
    public Cart add(
        Principal principal,
        @Valid @RequestBody AddItemRequest request
    ) {
        return shop.add(principal.getName(), request);
    }

    @PutMapping("/cart/items/{id}")
    public Cart quantity(
        Principal principal,
        @PathVariable Long id,
        @Valid @RequestBody QuantityRequest request
    ) {
        return shop.quantity(principal.getName(), id, request.quantity());
    }

    @DeleteMapping("/cart/items/{id}")
    public Cart remove(Principal principal, @PathVariable Long id) {
        return shop.remove(principal.getName(), id);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Order checkout(
        Principal principal,
        @Valid @RequestBody CheckoutRequest request
    ) {
        return shop.checkout(principal.getName(), request);
    }

    @GetMapping("/orders")
    public List<Order> orders(Principal principal) {
        return shop.history(principal.getName());
    }

    @GetMapping("/admin/users")
    public List<User> users() {
        return shop.listUsers();
    }

    @GetMapping("/admin/orders")
    public List<Order> allOrders() {
        return shop.allOrders();
    }

    @PutMapping("/admin/orders/{id}/status")
    public Order status(
        @PathVariable Long id,
        @Valid @RequestBody StatusRequest request
    ) {
        return shop.status(id, request.status());
    }

    @PostMapping("/admin/products")
    @ResponseStatus(HttpStatus.CREATED)
    public Product create(@Valid @RequestBody ProductRequest request) {
        return shop.saveProduct(null, request);
    }

    @PutMapping("/admin/products/{id}")
    public Product update(
        @PathVariable Long id,
        @Valid @RequestBody ProductRequest request
    ) {
        return shop.saveProduct(id, request);
    }

    @DeleteMapping("/admin/products/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        shop.deleteProduct(id);
        return Map.of("message", "Product removed from catalog");
    }

    @PostMapping("/admin/categories")
    public Category category(@Valid @RequestBody CategoryRequest request) {
        return shop.category(request);
    }
}
