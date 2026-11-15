# ShopSphere

A simple e-commerce college project using Java, Spring Boot, Spring Security, Spring Data JPA, MySQL, HTML, CSS, JavaScript and Bootstrap 5.

The pages use a basic navbar, product cards, forms and tables. There is no frontend framework or Node build step. JavaScript calls the Spring Boot APIs. MySQL stores users, products, carts and orders.

Read **PROJECT_GUIDE.md** for a short explanation of how the application works.

## Run the project

Requirements: JDK 21 or newer and MySQL 8. Maven is included through the project launcher; it does not need to be installed on PATH.

Open PowerShell:

```powershell
cd C:\Users\ripan\Downloads\shopsphere
powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1
```

Open **http://localhost:8080**. Stop the application with Ctrl+C in its terminal.

Your database is already configured. Do not run setup again just to restart the application.

### First-time database setup on another Windows computer

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-mysql.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1
```

The setup script prompts privately for the local MySQL administrator password. It creates database `shopsphere` and a separate application account without resetting existing accounts or tables. The launcher prompts for an initial admin password when needed.

Local credentials are saved with Windows account-bound encryption under `.local`, which Git ignores. Do not share that folder. To configure another environment, set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `ADMIN_EMAIL` and `ADMIN_PASSWORD` as environment variables. `.env.example` lists them but is not loaded automatically.

### Admin login

- Email: `admin@shopsphere.local`, unless you configured another address.
- Password: the password you chose during setup.

There is no public default admin password. Changing the startup password does not reset an existing account. New customer registrations always receive the USER role.

## Features

- Register, log in and log out.
- Browse, search and filter products by category.
- See product details, price and stock.
- Add products to a cart, change quantities and remove items.
- Place cash-on-delivery orders and view order history/status.
- Admin: add, edit and remove products; add categories; view users; manage orders.

Checkout is cash on delivery only. Prices are shown in INR. There is no online payment gateway, tax calculation or courier integration.

Passwords are hashed with BCrypt. The backend checks roles, ownership, quantities, totals and stock. Cancelling a PLACED or PROCESSING order restores its stock. Product removal hides the product without deleting old order records.

## Where to look

```text
src/main/java/com/shopsphere/    Java application, entities, repositories,
                               controllers, services and security
src/main/resources/static/     HTML pages, CSS and JavaScript
src/main/resources/            Database and application configuration
src/test/                      Automated tests
scripts/                       Maven, MySQL setup and run helpers
pom.xml                        Java dependencies and build settings
PROJECT_GUIDE.md               Beginner-friendly explanation
TESTING.md                     Verification results
```

The database contains seven tables: `users`, `categories`, `products`, `carts`, `cart_items`, `orders` and `order_items`. JPA entity relationships define their keys. Hibernate uses `ddl-auto=update` locally; do not change it to `create` or `create-drop` for your MySQL database.

## Build and test

Stop the running application before rebuilding its JAR:

```powershell
.\mvnw.cmd clean verify
```

Tests use an isolated H2 database, not your MySQL data. The test-only admin account is `admin@test.local` / `TestAdmin!12345`; it is not a login for the running shop.

Maven and its downloaded libraries are stored in `.tools`. A first build needs internet access. Bootstrap and sample product images also use external URLs.

If startup fails, check `.local/build.log` and `.local/application.log` for the actual error. Keep database passwords out of JavaScript, HTML and source control.
