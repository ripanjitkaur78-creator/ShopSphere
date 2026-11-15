# Understanding ShopSphere

ShopSphere is a Java full-stack project. The browser shows HTML pages, JavaScript sends requests, Spring Boot handles them, and MySQL saves the data.

## Follow one request

When a user adds a product to the cart:

1. The user clicks **Add to cart** on a product.
2. JavaScript sends the product ID and quantity to `POST /api/cart/items`.
3. Spring Security checks that the user is logged in and the request has a valid CSRF token.
4. The controller receives the request and calls the service.
5. The service checks the product and stock, then updates that user's cart.
6. The repository saves the change in MySQL through JPA/Hibernate.
7. The server returns the updated cart and the page displays it.

The same pattern is used for registration, checkout and admin actions:

**HTML form → JavaScript → Controller → Service → Repository → MySQL**

## What each part does

| Part | Purpose |
| --- | --- |
| HTML | Page headings, navigation, forms, tables and product containers |
| CSS / Bootstrap | Page layout, spacing, colours and mobile layout |
| JavaScript | Load products, submit forms and show server responses |
| Controller | Connect a URL to a Java method |
| Service | Apply the rules, such as checking stock and calculating totals |
| Repository | Find and save database records |
| Entity | Describe a table and its relationships |
| Request DTO | Describe the fields accepted from a form and validate them |
| Security configuration | Decide which URLs require login or an ADMIN role |

## Database relationships

- A user has one cart. A cart contains cart items.
- Each cart item refers to a product and stores its quantity.
- Each product belongs to a category.
- A user can place several orders.
- An order contains order items and a delivery address.
- An order item saves the product name, price and quantity at checkout. Later price changes do not change an old order.

## Rules worth understanding

**Passwords:** Registration hashes the password with BCrypt. Login compares the supplied password with the stored hash. The application does not return passwords in API responses.

**Roles:** Customers can manage their own cart and orders. Only ADMIN users can change products or manage orders. Hiding an admin button is not enough; the backend also checks the role.

**CSRF:** A form that changes data sends a token. This helps stop another website from making changes using a signed-in user's browser session.

**Transactions:** Checkout creates an order, reduces stock and clears the cart as one operation. If a stock check fails, the transaction rolls back instead of saving half an order.

**Stock locks:** Two people could order the last item at the same time. Checkout locks the product while checking and updating its stock.

**Totals:** The backend calculates the total using database prices. It does not trust a price sent from the browser. Java uses `BigDecimal` for money.

**Order status:** An order moves from PLACED to PROCESSING, SHIPPED and DELIVERED. Cancelling a PLACED or PROCESSING order returns the quantities to stock once.

**Product removal:** Removing a product hides it from the catalog. Its old order records stay available.

## Suggested reading order

1. Open `src/main/resources/static/index.html` and `home.js`.
2. Read `common.js` to see how JavaScript calls the backend.
3. Follow the product-list request through `ApiController.java` and `ShopService.java`.
4. Look at `model/Product.java` and `repository/ProductRepository.java`.
5. Follow `cart.html` and `cart.js`, then the checkout method in `ShopService.java`.
6. Read `SecurityConfig.java` and `src/test/java/com/shopsphere/ApiIntegrationTest.java`.

Each entity and repository now has its own file. Form inputs have small request classes in `dto/`. Each page has its own JavaScript, while shared login, API and navigation helpers stay in `common.js`.

The project keeps the required features, but it does not need a frontend framework, a Node build step, online payment integration, or a complicated deployment system to run locally. Checkout is cash on delivery.
