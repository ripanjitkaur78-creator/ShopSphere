package com.shopsphere;

import com.shopsphere.dto.AddItemRequest;
import com.shopsphere.dto.CategoryRequest;
import com.shopsphere.dto.CheckoutRequest;
import com.shopsphere.dto.ProductRequest;
import com.shopsphere.dto.RegistrationRequest;
import com.shopsphere.model.Cart;
import com.shopsphere.model.CartItem;
import com.shopsphere.model.Category;
import com.shopsphere.model.Order;
import com.shopsphere.model.OrderItem;
import com.shopsphere.model.Product;
import com.shopsphere.model.Status;
import com.shopsphere.model.User;
import com.shopsphere.repository.CartRepository;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.OrderRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ShopService {

    private final UserRepository users;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final CartRepository carts;
    private final OrderRepository orders;
    private final PasswordEncoder passwordEncoder;

    public ShopService(
        UserRepository users,
        CategoryRepository categories,
        ProductRepository products,
        CartRepository carts,
        OrderRepository orders,
        PasswordEncoder passwordEncoder
    ) {
        this.users = users;
        this.categories = categories;
        this.products = products;
        this.carts = carts;
        this.orders = orders;
        this.passwordEncoder = passwordEncoder;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
    }

    public User user(String email) {
        return users.findByEmail(email).orElseThrow(ShopService::notFound);
    }

    public List<Category> listCategories() {
        return categories.findAll();
    }

    public List<User> listUsers() {
        return users.findAll();
    }

    public List<Product> searchProducts(String query, Long categoryId) {
        if (query.length() > 200) {
            throw badRequest("Search must be at most 200 characters");
        }
        return products.search(query, categoryId);
    }

    public User register(RegistrationRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.findByEmail(email).isPresent()) {
            throw badRequest("An account with this email already exists");
        }
        // BCrypt accepts at most 72 bytes, not necessarily 72 characters.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw badRequest("Password must be at most 72 UTF-8 bytes");
        }
        User customer = new User();
        customer.name = request.name().trim();
        customer.email = email;
        customer.password = passwordEncoder.encode(request.password());
        return users.saveAndFlush(customer);
    }

    public Cart cart(String email) {
        // Serialize changes for this customer, including creation of their first cart.
        User customer = users.lock(email).orElseThrow(ShopService::notFound);
        return carts.findByUserId(customer.id).orElseGet(() -> {
            Cart newCart = new Cart();
            newCart.user = customer;
            return carts.save(newCart);
        });
    }

    public Product available(Long productId) {
        Product product = products
            .findById(productId)
            .orElseThrow(ShopService::notFound);
        if (!product.active) {
            throw notFound();
        }
        return product;
    }

    public Cart add(String email, AddItemRequest request) {
        Cart cart = cart(email);
        Product product = available(request.productId());
        CartItem existingItem = null;
        for (CartItem item : cart.items) {
            if (item.product.getId().equals(product.id)) {
                existingItem = item;
                break;
            }
        }
        int quantity = request.quantity();
        if (existingItem != null) {
            quantity += existingItem.quantity;
        }
        if (quantity > 99 || quantity > product.stock) {
            throw badRequest(
                "Requested quantity exceeds available stock or limit of 99"
            );
        }
        if (existingItem == null) {
            existingItem = new CartItem();
            existingItem.cart = cart;
            existingItem.product = product;
            cart.items.add(existingItem);
        }
        existingItem.quantity = quantity;
        return carts.saveAndFlush(cart);
    }

    private CartItem findCartItem(Cart cart, Long itemId) {
        // Search only the signed-in customer's cart, never another customer's items.
        for (CartItem item : cart.items) {
            if (item.id.equals(itemId)) {
                return item;
            }
        }
        throw notFound();
    }

    public Cart quantity(String email, Long itemId, int quantity) {
        Cart cart = cart(email);
        CartItem item = findCartItem(cart, itemId);
        if (!item.product.active || quantity > item.product.stock) {
            throw badRequest(
                "Product is unavailable or quantity exceeds stock"
            );
        }
        item.quantity = quantity;
        return cart;
    }

    public Cart remove(String email, Long itemId) {
        Cart cart = cart(email);
        CartItem item = findCartItem(cart, itemId);
        cart.items.remove(item);
        return cart;
    }

    public Order checkout(String email, CheckoutRequest request) {
        Cart cart = cart(email);
        if (cart.items.isEmpty()) {
            throw badRequest("Your cart is empty");
        }
        Order order = new Order();
        order.user = cart.user;
        order.shippingName = request.shippingName().trim();
        order.shippingAddress = request.shippingAddress().trim();
        order.phone = request.phone().trim();
        order.total = BigDecimal.ZERO;

        // Always lock products in ID order to reduce deadlocks between checkouts.
        List<CartItem> sortedItems = new ArrayList<>(cart.items);
        sortedItems.sort(Comparator.comparing(item -> item.product.getId()));
        for (CartItem cartItem : sortedItems) {
            Product product = products
                .lock(cartItem.product.getId())
                .orElseThrow(ShopService::notFound);
            if (!product.active || product.stock < cartItem.quantity) {
                throw badRequest("Insufficient stock for " + product.name);
            }
            product.stock -= cartItem.quantity;

            // Snapshot the name and price so later catalog edits cannot change an order.
            OrderItem orderItem = new OrderItem();
            orderItem.order = order;
            orderItem.product = product;
            orderItem.productName = product.name;
            orderItem.unitPrice = product.price;
            orderItem.quantity = cartItem.quantity;
            order.items.add(orderItem);
            BigDecimal lineTotal = product.price.multiply(
                BigDecimal.valueOf(cartItem.quantity)
            );
            order.total = order.total.add(lineTotal);
        }
        // One transaction saves the order, updates stock and empties the cart.
        orders.save(order);
        cart.items.clear();
        carts.flush();
        return order;
    }

    public List<Order> history(String email) {
        return orders.findByUserIdOrderByIdDesc(user(email).id);
    }

    public Product saveProduct(Long productId, ProductRequest request) {
        Product product =
            productId == null
                ? new Product()
                : products.lock(productId).orElseThrow(ShopService::notFound);
        if (!product.active) {
            throw notFound();
        }
        product.name = request.name().trim();
        product.description = request.description().trim();
        product.price = request.price();
        product.stock = request.stock();
        product.imageUrl = request.imageUrl();
        product.featured = request.featured();
        product.category = categories
            .findById(request.categoryId())
            .orElseThrow(() -> badRequest("Select an existing category"));
        return products.save(product);
    }

    public void deleteProduct(Long productId) {
        Product product = products
            .lock(productId)
            .orElseThrow(ShopService::notFound);
        // Keep historical order references; hide the product instead of deleting its row.
        product.active = false;
    }

    public Category category(CategoryRequest request) {
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw badRequest("Category already exists");
        }
        Category category = new Category();
        category.name = name;
        return categories.save(category);
    }

    public List<Order> allOrders() {
        return orders.findAllByOrderByIdDesc();
    }

    public Order status(Long orderId, Status nextStatus) {
        Order order = orders.lock(orderId).orElseThrow(ShopService::notFound);
        // A repeated request is harmless, particularly for cancellation stock restoration.
        if (order.status == nextStatus) {
            return order;
        }
        if (!canTransition(order.status, nextStatus)) {
            throw badRequest(
                "Invalid order transition from " +
                    order.status +
                    " to " +
                    nextStatus
            );
        }
        if (nextStatus == Status.CANCELLED) {
            restoreStock(order);
        }
        order.status = nextStatus;
        return order;
    }

    private boolean canTransition(Status currentStatus, Status nextStatus) {
        return switch (currentStatus) {
            case PLACED -> nextStatus == Status.PROCESSING ||
                nextStatus == Status.CANCELLED;
            case PROCESSING -> nextStatus == Status.SHIPPED ||
                nextStatus == Status.CANCELLED;
            case SHIPPED -> nextStatus == Status.DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    private void restoreStock(Order order) {
        List<OrderItem> sortedItems = new ArrayList<>(order.items);
        sortedItems.sort(Comparator.comparing(item -> item.product.getId()));
        for (OrderItem item : sortedItems) {
            Product product = products
                .lock(item.product.getId())
                .orElseThrow(ShopService::notFound);
            product.stock = Math.addExact(product.stock, item.quantity);
        }
    }
}
