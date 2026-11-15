# Verification record

Verified locally on 14 November 2026 using Temurin Java 25.0.3, Maven 3.9.11, Spring Boot 3.5.7, Hibernate 6.6.33.Final and MySQL 8.0.46.

## Build

Command: `./mvnw.cmd clean verify`

Result: **BUILD SUCCESS**, exit code 0. JUnit: **24 tests, 0 failures, 0 errors, 0 skipped**. The complete final clean build output is in `.local-final-build.log` (ignored by Git). Surefire reports are in `target/surefire-reports/`.

`node --check src/main/resources/static/app.js` also passed.

Automated tests use an isolated in-memory H2 database in MySQL compatibility mode; they do not clear the MySQL database. The live checks below separately verified the real MySQL-backed application.

Coverage: registration and validation, duplicate/normalized emails, BCrypt and password non-disclosure, login/logout, CSRF and role enforcement, catalog search/filtering, per-user cart isolation, quantities/removal, checkout totals and stock, transaction rollback, historical item snapshots, admin CRUD/categories/users/orders, valid/invalid status transitions, and cancellation stock restoration.

## Live MySQL and browser checks

Started the application using `powershell -NoProfile -ExecutionPolicy Bypass -File ./scripts/start.ps1` with the user's locally encrypted database/admin credentials. No database reset was performed.

- Application connected to MySQL 8.0.46 and Tomcat started on port 8080.
- Homepage returned HTTP 200; catalog API returned HTTP 200 and eight active products.
- Browser homepage rendered real products and photographs.
- Registered `qa.browser@shopsphere.local` through the registration form and signed in through the login form.
- Added Classic Analog Watch to cart, changed quantity to two, confirmed total of INR 178.00.
- Restarted the application and verified the cart persisted in MySQL.
- Submitted a clearly labelled local QA cash-on-delivery checkout and verified order #1 in customer history with two items and INR 178.00 total.
- Browser search for `watch` returned one matching product.
- Used the existing admin password from Windows DPAPI storage without printing or changing it; admin login succeeded.
- Verified admin user directory, product creation/update/removal, and catalog exclusion after removal.
- Cancelled only the QA order and verified status CANCELLED. Confirmed watch stock returned to 25.
- Admin logout succeeded. The customer QA account was signed out after checks.

Test records retained intentionally: one QA customer, one cancelled QA order labelled `ShopSphere QA TEST DO NOT SHIP`, and one soft-deleted disposable QA product. No real delivery or online charge was initiated. Existing user accounts and application data were not reset.

## Startup defects corrected

1. Nested JPA product entity needed an explicit entity name matching its JPQL query. Added explicit names.
2. Bootstrap/controller code read fields directly from a CGLIB transactional service proxy, resulting in a NullPointerException during initialization. Calls now delegate through service methods to the initialized target dependencies.
3. Entity identifier access through Hibernate proxies broke cancellation restocking. Product identifier access uses its getter; public-field entity mappings explicitly materialize entities. Cart/order collection fetch graphs make response serialization work with open-in-view disabled.
4. Maven's default global cache and upward project-directory scan encountered sandbox access restrictions. The wrapper now uses project-local Maven/cache paths and a fixed project base directory.
5. Mockito runtime attachment was restricted in this environment. Surefire now supplies Mockito as an explicit startup agent; test mock-maker configuration avoids unnecessary inline mocking.
6. Launcher failures previously reported only a generic exit message. Build and Java output are now retained in `.local/build.log` and `.local/application.log`; failure messages point to the correct log.

Java 25 may print a Maven/Guice deprecated `sun.misc.Unsafe` warning. This is a tooling warning, not a failed build or a database connection error; the verified build exited 0.

## Recheck

Stop the running app before replacing its executable JAR. Run `./mvnw.cmd clean verify`, then `powershell -ExecutionPolicy Bypass -File ./scripts/start.ps1`. Browse http://localhost:8080. Use the administrator email `admin@shopsphere.local` and the password chosen during private setup. The test-suite account `admin@test.local` exists only inside the isolated automated tests, not in MySQL.
