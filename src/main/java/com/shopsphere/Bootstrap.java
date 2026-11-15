package com.shopsphere;

import com.shopsphere.model.Category;
import com.shopsphere.model.Product;
import com.shopsphere.model.Role;
import com.shopsphere.model.User;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates an optional first administrator and a small starter catalog without replacing data. */
@Component
public class Bootstrap implements CommandLineRunner {

    private final UserRepository users;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final boolean seedCatalog;

    public Bootstrap(
        UserRepository users,
        CategoryRepository categories,
        ProductRepository products,
        PasswordEncoder passwordEncoder,
        @Value("${shopsphere.admin.email}") String adminEmail,
        @Value("${shopsphere.admin.password}") String adminPassword,
        @Value("${shopsphere.seed}") boolean seedCatalog
    ) {
        this.users = users;
        this.categories = categories;
        this.products = products;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.seedCatalog = seedCatalog;
    }

    @Override
    @Transactional
    public void run(String... args) {
        createInitialAdmin();
        if (seedCatalog && categories.count() == 0) {
            createStarterCatalog();
        }
    }

    private void createInitialAdmin() {
        String email = adminEmail.trim().toLowerCase(Locale.ROOT);
        if (
            email.isBlank() ||
            adminPassword.isBlank() ||
            users.findByEmail(email).isPresent()
        ) {
            return;
        }
        if (
            adminPassword.length() < 12 ||
            adminPassword.getBytes(StandardCharsets.UTF_8).length > 72
        ) {
            throw new IllegalStateException(
                "ADMIN_PASSWORD must be 12 to 72 UTF-8 bytes"
            );
        }
        User admin = new User();
        admin.name = "ShopSphere Admin";
        admin.email = email;
        admin.password = passwordEncoder.encode(adminPassword);
        admin.role = Role.ADMIN;
        users.save(admin);
    }

    private void createStarterCatalog() {
        String[] categoryNames = {
            "Electronics",
            "Home & Living",
            "Fashion",
            "Lifestyle",
        };
        Category[] starterCategories = new Category[categoryNames.length];
        for (int index = 0; index < categoryNames.length; index++) {
            Category category = new Category();
            category.name = categoryNames[index];
            starterCategories[index] = categories.save(category);
        }
        // Each row contains name, description, price, photo identifier and category index.
        String[][] data = {
            {
                "Studio Wireless Headphones",
                "Immersive sound, soft over-ear cushions and a clean minimalist finish.",
                "129.00",
                "photo-1505740420928-5e560c06d30e",
                "0",
            },
            {
                "Everyday Canvas Tote",
                "A spacious everyday carry with durable canvas and comfortable handles.",
                "29.00",
                "photo-1544816155-12df9643f363",
                "2",
            },
            {
                "Ceramic Pour-over Set",
                "Make your daily coffee ritual a little more considered.",
                "49.00",
                "photo-1495474472287-4d71bcdd2085",
                "1",
            },
            {
                "Classic Analog Watch",
                "A timeless everyday watch with a refined, easy-to-read dial.",
                "89.00",
                "photo-1524805444758-089113d48a6d",
                "2",
            },
            {
                "Portable Bluetooth Speaker",
                "Room-filling sound in a compact wireless design.",
                "69.00",
                "photo-1608043152269-423dbba4e7e1",
                "0",
            },
            {
                "Desk Companion Lamp",
                "Warm, focused light for a calm and productive workspace.",
                "59.00",
                "photo-1507473885765-e6ed057f782c",
                "1",
            },
            {
                "Essential Daypack",
                "A versatile lightweight backpack for the commute and beyond.",
                "79.00",
                "photo-1553062407-98eeb64c6a62",
                "3",
            },
            {
                "Minimal Leather Sneakers",
                "Clean lines and all-day comfort for your everyday rotation.",
                "99.00",
                "photo-1549298916-b41d501d3772",
                "2",
            },
        };

        for (int index = 0; index < data.length; index++) {
            String[] row = data[index];
            Product product = new Product();
            product.name = row[0];
            product.description = row[1];
            product.price = new BigDecimal(row[2]);
            product.stock = 25;
            product.imageUrl =
                "https://images.unsplash.com/" +
                row[3] +
                "?auto=format&fit=crop&w=800&q=80";
            product.featured = index < 4;
            product.category = starterCategories[Integer.parseInt(row[4])];
            products.save(product);
        }
    }
}
