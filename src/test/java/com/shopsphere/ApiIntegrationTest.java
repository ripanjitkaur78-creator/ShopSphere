package com.shopsphere;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.model.*;
import com.shopsphere.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real security filters, controllers, services and H2 persistence. Deliberately NOT
 * @Transactional: an ambient test transaction would hide detached/lazy serialization
 * failures, transaction-boundary errors and missing persistence of service changes.
 * Each test owns unique users/products/categories; shared bootstrap records are read-only.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:shopsphere-integration;MODE=MYSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "shopsphere.admin.email=admin@test.local",
    "shopsphere.admin.password=TestAdmin!12345", "shopsphere.seed=true"
})
@AutoConfigureMockMvc
class ApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired CartRepository carts;
    @Autowired OrderRepository orders;
    @Autowired PasswordEncoder encoder;

    private static final String PASSWORD = "Customer!12345";
    private static final Map<String, String> SHIPPING = Map.of(
        "shippingName", "Test Customer", "shippingAddress", "123 Integration Test Road",
        "phone", "+1 555 123 4567");
    private String unique() { return UUID.randomUUID().toString().replace("-", ""); }
    private String customer() {
        User u = new User(); u.name = "Test Customer"; u.email = unique() + "@test.local";
        u.password = encoder.encode(PASSWORD); return users.saveAndFlush(u).email;
    }
    private Category category() {
        Category c = new Category(); c.name = "Category " + unique(); return categories.saveAndFlush(c);
    }
    private Product product(String name, String price, int stock, Category c) {
        Product p = new Product(); p.name = name; p.description = "Integration test description";
        p.price = new BigDecimal(price); p.stock = stock; p.category = c;
        p.imageUrl = "/images/test.svg"; return products.saveAndFlush(p);
    }
    private Product product(String price, int stock) {
        return product("Product " + unique(), price, stock, category());
    }
    private String body(Object value) throws Exception { return json.writeValueAsString(value); }
    private JsonNode result(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
    private JsonNode addItem(String email, Product p, int qty) throws Exception {
        return result(mvc.perform(post("/api/cart/items").with(user(email)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("productId", p.id, "quantity", qty))))
            .andExpect(status().isOk()).andReturn());
    }
    private JsonNode checkout(String email) throws Exception {
        return result(mvc.perform(post("/api/orders").with(user(email)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body(SHIPPING)))
            .andExpect(status().isCreated()).andReturn());
    }
    private void transition(long id, String next, int expected) throws Exception {
        mvc.perform(put("/api/admin/orders/{id}/status", id).with(user("admin@test.local").roles("ADMIN"))
            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("status", next))))
            .andExpect(status().is(expected));
    }
    private Map<String, Object> productInput(Category c, String name, String price, int stock) {
        return Map.of("name", name, "description", "A detailed product description", "price", price,
            "stock", stock, "imageUrl", "https://example.com/image.jpg", "featured", true, "categoryId", c.id);
    }

    @Test void seededCatalogAndAdminExistWithEncodedPassword() throws Exception {
        assertThat(categories.count()).isGreaterThanOrEqualTo(4);
        assertThat(products.count()).isGreaterThanOrEqualTo(8);
        User admin = users.findByEmail("admin@test.local").orElseThrow();
        assertThat(admin.role).isEqualTo(Role.ADMIN);
        assertThat(encoder.matches("TestAdmin!12345", admin.password)).isTrue();
        mvc.perform(get("/api/categories")).andExpect(status().isOk()).andExpect(jsonPath("$", not(empty())));
    }

    @Test void registrationNormalizesEmailAndNeverLeaksPassword() throws Exception {
        String email = unique() + "@test.local";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("name", " Alice ", "email", email.toUpperCase(), "password", PASSWORD))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.name").value("Alice")).andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.password").doesNotExist());
        User stored = users.findByEmail(email).orElseThrow();
        assertThat(stored.password).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, stored.password)).isTrue();
    }

    @Test void duplicateRegistrationIsRejectedCaseInsensitively() throws Exception {
        String email = customer(); long before = users.count();
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("name", "Duplicate", "email", email.toUpperCase(), "password", PASSWORD))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("already exists")));
        assertThat(users.count()).isEqualTo(before);
    }

    @Test void registrationValidatesFieldsAndUtf8PasswordByteLimit() throws Exception {
        for (Map<String, String> input : java.util.List.of(
            Map.of("name", " ", "email", "bad", "password", "short"),
            Map.of("name", "Valid", "email", unique()+"@test.local", "password", "x".repeat(73)),
            Map.of("name", "Valid", "email", unique()+"@test.local", "password", "界".repeat(25)))) {
            mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body(input))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        }
    }

    @Test void loginCreatesUsableSessionAndLogoutInvalidatesIt() throws Exception {
        String email = customer();
        MvcResult login = mvc.perform(post("/api/auth/login").with(csrf())
            .param("username", email).param("password", PASSWORD))
            .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Signed in")).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isOk());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void invalidLoginIsUnauthorized() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", customer()).param("password", "wrong"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Incorrect email or password"));
    }

    @Test void anonymousRequestsAreUnauthorizedAndCustomersCannotAccessAdmin() throws Exception {
        for (String path : new String[]{"/api/auth/me", "/api/cart", "/api/orders", "/api/admin/users"})
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        String email = customer();
        for (String path : new String[]{"/api/admin/users", "/api/admin/orders"})
            mvc.perform(get(path).with(user(email))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/categories").with(user(email)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("name", "Forbidden"))))
            .andExpect(status().isForbidden());
    }

    @Test void writesRequireCsrfIncludingLoginAndRegistration() throws Exception {
        mvc.perform(get("/api/csrf")).andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty()).andExpect(jsonPath("$.headerName").isNotEmpty());
        mvc.perform(post("/api/auth/login").param("username", "admin@test.local").param("password", "TestAdmin!12345"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("name", "No CSRF", "email", unique()+"@test.local", "password", PASSWORD))))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/cart/items").with(user(customer())).contentType(MediaType.APPLICATION_JSON)
            .content("{\"productId\":1,\"quantity\":1}")).andExpect(status().isForbidden());
    }

    @Test void catalogSearchAndCategoryFiltersCombineAndExcludeInactiveProducts() throws Exception {
        String token = unique(); Category first = category(), second = category();
        Product match = product("Match " + token, "10.00", 5, first);
        product("Other " + token, "20.00", 5, second);
        Product inactive = product("Inactive " + token, "30.00", 5, first);
        inactive.active = false; products.saveAndFlush(inactive);
        mvc.perform(get("/api/products").param("q", token.toUpperCase()).param("category", first.id.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(match.id));
        mvc.perform(get("/api/products/{id}", match.id)).andExpect(status().isOk());
        mvc.perform(get("/api/products/{id}", inactive.id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products").param("q", "x".repeat(201))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("category", "not-a-number")).andExpect(status().isBadRequest());
    }

    @Test void cartAddsMergesUpdatesAndRemovesItemsAndPersistsTotals() throws Exception {
        String email = customer(); Product p = product("12.50", 10);
        JsonNode first = addItem(email, p, 2); long item = first.path("items").get(0).path("id").asLong();
        assertThat(first.path("total").decimalValue()).isEqualByComparingTo("25.00");
        JsonNode merged = addItem(email, p, 1);
        assertThat(merged.path("items").size()).isEqualTo(1);
        assertThat(merged.path("items").get(0).path("quantity").asInt()).isEqualTo(3);
        mvc.perform(put("/api/cart/items/{id}", item).with(user(email)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":4}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(50.0));
        mvc.perform(get("/api/cart").with(user(email))).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].quantity").value(4));
        mvc.perform(delete("/api/cart/items/{id}", item).with(user(email)).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items", empty())).andExpect(jsonPath("$.total").value(0));
        assertThat(products.findById(p.id).orElseThrow().stock).isEqualTo(10);
    }

    @Test void cartRejectsInvalidQuantitiesAndInsufficientStock() throws Exception {
        String email = customer(); Product p = product("2.00", 3);
        for (int qty : new int[]{0, -1, 100, 4})
            mvc.perform(post("/api/cart/items").with(user(email)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("productId", p.id, "quantity", qty)))).andExpect(status().isBadRequest());
        JsonNode cart = addItem(email, p, 2); long id = cart.path("items").get(0).path("id").asLong();
        mvc.perform(put("/api/cart/items/{id}", id).with(user(email)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/cart/items/{id}", id).with(user(email)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":4}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/cart/items").with(user(email)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("productId", p.id, "quantity", 2)))).andExpect(status().isBadRequest());
    }

    @Test void cartItemOwnershipIsEnforced() throws Exception {
        String owner = customer(), stranger = customer(); Product p = product("3.00", 5);
        long id = addItem(owner, p, 1).path("items").get(0).path("id").asLong();
        mvc.perform(put("/api/cart/items/{id}", id).with(user(stranger)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/cart/items/{id}", id).with(user(stranger)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/cart").with(user(owner))).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].quantity").value(1));
        mvc.perform(get("/api/cart").with(user(stranger))).andExpect(status().isOk()).andExpect(jsonPath("$.items", empty()));
    }

    @Test void checkoutComputesTotalsDecrementsStockClearsCartAndIsolatesHistory() throws Exception {
        String email = customer(), stranger = customer();
        Product a = product("12.50", 10), b = product("3.25", 5);
        addItem(email, a, 2); addItem(email, b, 3);
        JsonNode order = checkout(email);
        assertThat(order.path("status").asText()).isEqualTo("PLACED");
        assertThat(order.path("total").decimalValue()).isEqualByComparingTo("34.75");
        assertThat(order.path("items").size()).isEqualTo(2);
        assertThat(order.has("user")).isFalse();
        assertThat(products.findById(a.id).orElseThrow().stock).isEqualTo(8);
        assertThat(products.findById(b.id).orElseThrow().stock).isEqualTo(2);
        mvc.perform(get("/api/cart").with(user(email))).andExpect(status().isOk()).andExpect(jsonPath("$.items", empty()));
        mvc.perform(get("/api/orders").with(user(email))).andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(order.path("id").asLong()))
            .andExpect(jsonPath("$[0].total").value(34.75));
        mvc.perform(get("/api/orders").with(user(stranger))).andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
    }

    @Test void checkoutRejectsEmptyCartAndInvalidShipping() throws Exception {
        String email = customer();
        mvc.perform(post("/api/orders").with(user(email)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(body(SHIPPING))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Your cart is empty"));
        mvc.perform(post("/api/orders").with(user(email)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("shippingName", "", "shippingAddress", "short", "phone", "letters"))))
            .andExpect(status().isBadRequest());
    }

    @Test void checkoutRechecksStockAndRollsBackAllEarlierChanges() throws Exception {
        String email = customer(); Product first = product("10.00", 5), later = product("20.00", 5);
        addItem(email, first, 2); addItem(email, later, 2);
        later.stock = 1; products.saveAndFlush(later);
        mvc.perform(post("/api/orders").with(user(email)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(body(SHIPPING))).andExpect(status().isBadRequest());
        assertThat(products.findById(first.id).orElseThrow().stock).isEqualTo(5);
        assertThat(products.findById(later.id).orElseThrow().stock).isEqualTo(1);
        mvc.perform(get("/api/orders").with(user(email))).andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
        mvc.perform(get("/api/cart").with(user(email))).andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test void orderSnapshotsSurviveSubsequentProductPriceAndNameChanges() throws Exception {
        String email = customer(); Product p = product("14.25", 5); String original = p.name;
        addItem(email, p, 2); checkout(email);
        p = products.findById(p.id).orElseThrow(); p.name = "Renamed"; p.price = new BigDecimal("99.99"); products.saveAndFlush(p);
        mvc.perform(get("/api/orders").with(user(email))).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].total").value(28.5)).andExpect(jsonPath("$[0].items[0].productName").value(original))
            .andExpect(jsonPath("$[0].items[0].unitPrice").value(14.25));
    }

    @Test void adminCanCreateUpdateAndSoftDeleteProducts() throws Exception {
        Category c = category(); String name = "Admin " + unique();
        JsonNode created = result(mvc.perform(post("/api/admin/products").with(user("admin@test.local").roles("ADMIN"))
            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(productInput(c, name, "19.95", 7))))
            .andExpect(status().isCreated()).andReturn());
        long id = created.path("id").asLong();
        mvc.perform(put("/api/admin/products/{id}", id).with(user("admin@test.local").roles("ADMIN"))
            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(productInput(c, name+" updated", "24.50", 9))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.stock").value(9)).andExpect(jsonPath("$.price").value(24.5));
        mvc.perform(delete("/api/admin/products/{id}", id).with(user("admin@test.local").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk());
        assertThat(products.findById(id).orElseThrow().active).isFalse();
        mvc.perform(get("/api/products/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products").param("q", name)).andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
        mvc.perform(post("/api/cart/items").with(user(customer())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("productId", id, "quantity", 1)))).andExpect(status().isNotFound());
    }

    @Test void adminCategoriesValidateDuplicatesAndProductsValidateInput() throws Exception {
        String name = "New " + unique();
        mvc.perform(post("/api/admin/categories").with(user("admin@test.local").roles("ADMIN")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("name", name))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value(name));
        mvc.perform(post("/api/admin/categories").with(user("admin@test.local").roles("ADMIN")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("name", name.toUpperCase())))).andExpect(status().isBadRequest());
        for (Map<String,Object> input : java.util.List.of(
            productInput(category(), "Bad price", "0.00", 1),
            productInput(category(), "Bad stock", "1.00", -1),
            productInput(category(), "Bad precision", "1.001", 1))) {
            mvc.perform(post("/api/admin/products").with(user("admin@test.local").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(input))).andExpect(status().isBadRequest());
        }
        Map<String,Object> missingCategory = new java.util.HashMap<>(productInput(category(), "Bad category", "1.00", 1));
        missingCategory.put("categoryId", Long.MAX_VALUE);
        mvc.perform(post("/api/admin/products").with(user("admin@test.local").roles("ADMIN")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body(missingCategory))).andExpect(status().isBadRequest());
    }

    @Test void adminCanListUsersAndOrdersWithoutPasswordDisclosure() throws Exception {
        String email = customer(); Product p = product("5.00", 2); addItem(email, p, 1); JsonNode order = checkout(email);
        mvc.perform(get("/api/admin/users").with(user("admin@test.local").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[*].email", hasItem(email)))
            .andExpect(jsonPath("$[*].password", empty()));
        mvc.perform(get("/api/admin/orders").with(user("admin@test.local").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", hasItem(order.path("id").asInt())));
    }

    @Test void cancellationRestoresStockExactlyOnceAndCannotBeReopened() throws Exception {
        String email = customer(); Product p = product("7.50", 10);
        addItem(email, p, 3); long id = checkout(email).path("id").asLong();
        transition(id, "PROCESSING", 200); transition(id, "CANCELLED", 200);
        assertThat(products.findById(p.id).orElseThrow().stock).isEqualTo(10);
        transition(id, "CANCELLED", 200);
        assertThat(products.findById(p.id).orElseThrow().stock).isEqualTo(10);
        transition(id, "PROCESSING", 400);
        assertThat(orders.findById(id).orElseThrow().status).isEqualTo(Status.CANCELLED);
    }

    @Test void directCancellationFromPlacedRestoresStock() throws Exception {
        String email = customer(); Product p = product("7.50", 10);
        addItem(email, p, 2); long id = checkout(email).path("id").asLong();
        transition(id, "CANCELLED", 200);
        assertThat(products.findById(p.id).orElseThrow().stock).isEqualTo(10);
    }

    @Test void shippingMustFollowValidTransitionsAndDeliveredIsTerminal() throws Exception {
        String email = customer(); Product p = product("7.50", 10);
        addItem(email, p, 1); long id = checkout(email).path("id").asLong();
        transition(id, "DELIVERED", 400); transition(id, "SHIPPED", 400);
        transition(id, "PROCESSING", 200); transition(id, "PLACED", 400);
        transition(id, "SHIPPED", 200); transition(id, "CANCELLED", 400);
        transition(id, "DELIVERED", 200); transition(id, "PROCESSING", 400);
        assertThat(orders.findById(id).orElseThrow().status).isEqualTo(Status.DELIVERED);
        assertThat(products.findById(p.id).orElseThrow().stock).isEqualTo(9);
    }

    @Test void registrationCannotSelfAssignAnAdminRoleAndMalformedJsonIsRejected() throws Exception {
        String email = unique() + "@test.local";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("name", "Attacker", "email", email, "password", PASSWORD, "role", "ADMIN"))))
            .andExpect(status().isBadRequest());
        assertThat(users.findByEmail(email)).isEmpty();
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{broken json")).andExpect(status().isBadRequest());
    }

    @Test void adminCredentialsAuthenticateAndUserListingRemainsProtected() throws Exception {
        MvcResult login = mvc.perform(post("/api/auth/login").with(csrf())
            .param("username", "admin@test.local").param("password", "TestAdmin!12345"))
            .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$[*].password", empty()));
        mvc.perform(post("/api/admin/categories").session(session)
            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("name", "Missing csrf"))))
            .andExpect(status().isForbidden());
    }
}
