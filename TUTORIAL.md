# Building this project from scratch — full step-by-step tutorial

This is a complete, ordered walkthrough that recreates this exact project: a
**Spring Boot 3.5 (Java 21) + PostgreSQL** REST API and an **Angular 22** frontend,
both with automated tests. Every shell command and every file's full content is
included in the order they were actually created, so you can follow it top to
bottom and end up with a working, tested CRUD app — and reuse the same recipe
for any other entity later.

> Convention used below: `$` lines are shell commands you run; everything else
> is file content to save at the given path.

---

## Part 0 — Prerequisites

Check what you already have:

```bash
java -version      # need 21+
node -v             # need 18+ (22.x used here)
npm -v
psql --version      # any recent PostgreSQL
```

### Install JDK 21 (if you don't have it)

On macOS, Homebrew's keg-only `openjdk@21` is the safest option — it won't
overwrite or conflict with any JDK you already have installed:

```bash
$ brew install openjdk@21
$ export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
$ export PATH="$JAVA_HOME/bin:$PATH"
$ java -version   # should now print 21.x
```

Add the two `export` lines to `~/.zshrc` so every new terminal has them, or
re-run them once per session before touching the backend.

### Install PostgreSQL (if you don't have it)

Any local Postgres works — a native Homebrew install, or an app like
Postgres.app. This tutorial assumes Postgres is already running on
`localhost:5432` and you have a superuser to connect with.

> **macOS + Postgres.app note:** the first time a new client process (the
> `java` binary running this app) connects, Postgres.app pops up a one-time
> "Allow this app to connect?" dialog. You must click **Allow** on screen —
> it can't be scripted or approved from a script. It's only asked once per
> client binary.

---

## Part 1 — Database setup

Create a dedicated, low-privilege role and two databases (one for running the
app, one for the test suite, so tests never touch your dev data):

```bash
$ psql -h localhost -U "$(whoami)" -d postgres
```
```sql
CREATE ROLE product_app LOGIN PASSWORD 'product_app_pwd';
CREATE DATABASE productcrud      OWNER product_app;
CREATE DATABASE productcrud_test OWNER product_app;
```

Verify:
```bash
$ psql -h localhost -U "$(whoami)" -d postgres -c "\du" -c "select datname from pg_database where datistemplate=false;"
```

---

## Part 2 — Backend: generate the Spring Boot skeleton

This single `curl` to Spring Initializr's API is the scriptable equivalent of
filling out the form at [start.spring.io](https://start.spring.io):

```bash
$ mkdir -p product-crud && cd product-crud
$ curl -s "https://start.spring.io/starter.zip" \
  -d type=maven-project \
  -d language=java \
  -d bootVersion=3.5.15 \
  -d baseDir=backend \
  -d groupId=com.example \
  -d artifactId=product-crud \
  -d name=product-crud \
  -d description="Product CRUD demo" \
  -d packageName=com.example.productcrud \
  -d packaging=jar \
  -d javaVersion=21 \
  -d dependencies=web,data-jpa,postgresql,validation,lombok,devtools,flyway \
  -o backend.zip
$ unzip -q backend.zip && rm backend.zip
```

**Why `bootVersion=3.5.15` and not the default?** At the time this was built,
start.spring.io defaulted to the brand-new Spring Boot 4.x line (renamed
starters like `spring-boot-starter-webmvc`, split per-starter test
dependencies). 3.5.15 is the latest release of the well-established 3.x line —
far better documented, and what virtually every existing tutorial assumes.
Pin a version explicitly; don't take whatever the generator defaults to.

This produces `backend/pom.xml`, `backend/mvnw` (the Maven Wrapper — you never
need Maven installed globally), and an empty `src/` skeleton with one
`ProductCrudApplication.java` and one placeholder test.

### Add the Testcontainers-alternative test dependency note

No extra dependency needed here — we'll point integration tests at a real
local Postgres database instead of Testcontainers (which needs Docker). See
the troubleshooting section at the end for upgrading this later.

### Create the package structure

```bash
$ cd backend
$ mkdir -p src/main/java/com/example/productcrud/product/dto
$ mkdir -p src/main/java/com/example/productcrud/web
$ mkdir -p src/main/java/com/example/productcrud/config
$ mkdir -p src/main/resources/db/migration
$ mkdir -p src/test/resources
```

---

## Part 3 — Backend: the database migration

Flyway owns the schema — Hibernate is configured later to only `validate`
against it, never auto-generate DDL. This keeps the SQL as the single source
of truth for the table shape.

**`backend/src/main/resources/db/migration/V1__create_products_table.sql`**
```sql
CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120)   NOT NULL,
    description VARCHAR(1000),
    price       NUMERIC(12, 2) NOT NULL CHECK (price > 0),
    quantity    INTEGER        NOT NULL CHECK (quantity >= 0),
    created_at  TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX idx_products_name ON products (name);
```

---

## Part 4 — Backend: the entity

**`backend/src/main/java/com/example/productcrud/product/Product.java`**
```java
package com.example.productcrud.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer quantity;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

---

## Part 5 — Backend: the repository

**`backend/src/main/java/com/example/productcrud/product/ProductRepository.java`**
```java
package com.example.productcrud.product;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
```

That's it — Spring Data JPA generates the implementation. No custom query
methods needed for basic CRUD.

---

## Part 6 — Backend: the DTOs

DTOs are kept separate from the entity so the public API contract doesn't
change every time the persistence model does.

**`backend/src/main/java/com/example/productcrud/product/dto/ProductRequest.java`**
```java
package com.example.productcrud.product.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(

        @NotBlank(message = "name is required")
        @Size(max = 120, message = "name must be at most 120 characters")
        String name,

        @Size(max = 1000, message = "description must be at most 1000 characters")
        String description,

        @NotNull(message = "price is required")
        @jakarta.validation.constraints.DecimalMin(value = "0.01", message = "price must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "price must have at most 2 decimal places")
        BigDecimal price,

        @NotNull(message = "quantity is required")
        @PositiveOrZero(message = "quantity cannot be negative")
        Integer quantity
) {
}
```

**`backend/src/main/java/com/example/productcrud/product/dto/ProductResponse.java`**
```java
package com.example.productcrud.product.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer quantity,
        Instant createdAt,
        Instant updatedAt
) {
}
```

---

## Part 7 — Backend: the mapper

A plain static utility — no MapStruct needed for one entity (worth adding once
you have several entities to map; see the improvements list at the end).

**`backend/src/main/java/com/example/productcrud/product/ProductMapper.java`**
```java
package com.example.productcrud.product;

import com.example.productcrud.product.dto.ProductRequest;
import com.example.productcrud.product.dto.ProductResponse;

final class ProductMapper {

    private ProductMapper() {
    }

    static Product toEntity(ProductRequest request) {
        Product product = new Product();
        applyRequest(product, request);
        return product;
    }

    static void applyRequest(Product product, ProductRequest request) {
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setQuantity(request.quantity());
    }

    static ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getQuantity(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
```

---

## Part 8 — Backend: the custom exception

**`backend/src/main/java/com/example/productcrud/product/ProductNotFoundException.java`**
```java
package com.example.productcrud.product;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long id) {
        super("Product not found with id " + id);
    }
}
```

---

## Part 9 — Backend: the service layer

**`backend/src/main/java/com/example/productcrud/product/ProductService.java`**
```java
package com.example.productcrud.product;

import com.example.productcrud.product.dto.ProductRequest;
import com.example.productcrud.product.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public Page<ProductResponse> findAll(Pageable pageable) {
        return productRepository.findAll(pageable).map(ProductMapper::toResponse);
    }

    public ProductResponse findById(Long id) {
        return ProductMapper.toResponse(getOrThrow(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = ProductMapper.toEntity(request);
        return ProductMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getOrThrow(id);
        ProductMapper.applyRequest(product, request);
        return ProductMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public void delete(Long id) {
        Product product = getOrThrow(id);
        productRepository.delete(product);
    }

    private Product getOrThrow(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }
}
```

`@Transactional(readOnly = true)` on the class with per-method `@Transactional`
overrides on the writes is a small but important pattern: reads default to
read-only transactions (a hint some drivers/pools optimize for), writes opt
into a real transaction explicitly.

---

## Part 10 — Backend: the REST controller

**`backend/src/main/java/com/example/productcrud/product/ProductController.java`**
```java
package com.example.productcrud.product;

import com.example.productcrud.product.dto.ProductRequest;
import com.example.productcrud.product.dto.ProductResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public Page<ProductResponse> findAll(@PageableDefault(size = 20) Pageable pageable) {
        return productService.findAll(pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable Long id) {
        return productService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity.created(URI.create("/api/products/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
```

---

## Part 11 — Backend: uniform error responses

**`backend/src/main/java/com/example/productcrud/web/ApiError.java`**
```java
package com.example.productcrud.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> fieldErrors
) {
    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, List.of());
    }

    public static ApiError ofFieldErrors(int status, String error, String message, String path,
                                          Map<String, String> violations) {
        List<FieldViolation> fieldErrors = violations.entrySet().stream()
                .map(e -> new FieldViolation(e.getKey(), e.getValue()))
                .toList();
        return new ApiError(Instant.now(), status, error, message, path, fieldErrors);
    }
}
```

**`backend/src/main/java/com/example/productcrud/web/GlobalExceptionHandler.java`**
```java
package com.example.productcrud.web;

import com.example.productcrud.product.ProductNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ProductNotFoundException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> violations = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            violations.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ApiError body = ApiError.ofFieldErrors(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed", request.getRequestURI(), violations);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        ApiError body = ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Unexpected error occurred", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
```

---

## Part 12 — Backend: CORS so the Angular dev server can call this API

**`backend/src/main/java/com/example/productcrud/web/WebConfig.java`**
```java
package com.example.productcrud.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
```

---

## Part 13 — Backend: JPA auditing (and the gotcha around it)

**`backend/src/main/java/com/example/productcrud/config/JpaAuditingConfig.java`**
```java
package com.example.productcrud.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Kept separate from the @SpringBootApplication class: that class doubles as
 * the @SpringBootConfiguration picked up by slice tests like @WebMvcTest, which
 * have no EntityManagerFactory and would fail to build the auditing handler bean.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
```

> **Gotcha worth knowing:** if you instead put `@EnableJpaAuditing` directly on
> the `@SpringBootApplication` class, every `@WebMvcTest` slice test in the
> project will fail to start with `JPA metamodel must not be empty` — because
> that class is also the `@SpringBootConfiguration` those slice tests load, and
> a web-only slice has no real JPA context. Splitting it into its own
> `@Configuration` class avoids that entirely. We hit exactly this error
> building this project; see Part 16.

Make sure `ProductCrudApplication.java` does **not** carry `@EnableJpaAuditing`
— it should just be the plain generated:
```java
package com.example.productcrud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProductCrudApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProductCrudApplication.class, args);
	}

}
```

---

## Part 14 — Backend: configuration

**`backend/src/main/resources/application.properties`**
```properties
spring.application.name=product-crud

# --- Datasource ---------------------------------------------------------
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:productcrud}
spring.datasource.username=${DB_USERNAME:product_app}
spring.datasource.password=${DB_PASSWORD:product_app_pwd}

# --- JPA / Hibernate ------------------------------------------------------
# Schema is owned by Flyway migrations, never by Hibernate.
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.format_sql=true

# --- Flyway ---------------------------------------------------------------
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# --- Web / CORS -------------------------------------------------------
server.port=${SERVER_PORT:8080}
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:4200}
```

Everything is overridable via environment variables with sane local defaults
baked in — this is what lets the exact same JAR run against a different
database in a different environment with zero code changes.

**`backend/src/test/resources/application.properties`** (overrides the
datasource for the test classpath only — this is what makes integration tests
safe to run repeatedly without ever touching `productcrud`):
```properties
spring.application.name=product-crud

spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:productcrud_test}
spring.datasource.username=${DB_USERNAME:product_app}
spring.datasource.password=${DB_PASSWORD:product_app_pwd}

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false

spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

app.cors.allowed-origins=http://localhost:4200
```

---

## Part 15 — Backend: compile and sanity-check

```bash
$ cd backend
$ export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
$ export PATH="$JAVA_HOME/bin:$PATH"
$ chmod +x mvnw
$ ./mvnw -q -DskipTests compile
```
No output means success. Confirm the classes were produced:
```bash
$ find target/classes -name "*.class"
```

---

## Part 16 — Backend: automated tests (4 layers)

Four test classes, each exercising a different layer. Together: unit logic →
HTTP/validation → real SQL → full stack over real HTTP.

**`backend/src/test/java/com/example/productcrud/product/ProductServiceTest.java`**
— pure Mockito, no Spring context, no database:
```java
package com.example.productcrud.product;

import com.example.productcrud.product.dto.ProductRequest;
import com.example.productcrud.product.dto.ProductResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    private ProductService productService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        productService = new ProductService(productRepository);
    }

    @Test
    void createPersistsAndReturnsMappedResponse() {
        ProductRequest request = new ProductRequest("Keyboard", "Mechanical", new BigDecimal("199.90"), 10);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        ProductResponse response = productService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Keyboard");
        assertThat(response.price()).isEqualTo(new BigDecimal("199.90"));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualTo(10);
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(productRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(42L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void updateAppliesChangesToExistingProduct() {
        Product existing = new Product();
        existing.setId(7L);
        existing.setName("Old name");
        existing.setPrice(new BigDecimal("10.00"));
        existing.setQuantity(1);
        when(productRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductRequest request = new ProductRequest("New name", "desc", new BigDecimal("25.50"), 5);
        ProductResponse response = productService.update(7L, request);

        assertThat(response.name()).isEqualTo("New name");
        assertThat(response.price()).isEqualTo(new BigDecimal("25.50"));
        assertThat(response.quantity()).isEqualTo(5);
    }

    @Test
    void deleteThrowsWhenProductMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(99L))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).delete(any());
    }
}
```

**`backend/src/test/java/com/example/productcrud/product/ProductControllerWebMvcTest.java`**
— `@WebMvcTest`: real HTTP layer + validation, mocked service, no database:
```java
package com.example.productcrud.product;

import com.example.productcrud.product.dto.ProductRequest;
import com.example.productcrud.product.dto.ProductResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    @Test
    void findAllReturnsPageOfProducts() throws Exception {
        ProductResponse response = new ProductResponse(1L, "Mouse", "Wireless", new BigDecimal("99.90"), 3,
                Instant.now(), Instant.now());
        when(productService.findAll(any())).thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Mouse"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void findByIdReturns404WhenMissing() throws Exception {
        when(productService.findById(99L)).thenThrow(new ProductNotFoundException(99L));

        mockMvc.perform(get("/api/products/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Product not found with id 99"));
    }

    @Test
    void createReturns201WithLocationHeader() throws Exception {
        ProductRequest request = new ProductRequest("Monitor", "27 inch", new BigDecimal("899.00"), 4);
        ProductResponse created = new ProductResponse(5L, "Monitor", "27 inch", new BigDecimal("899.00"), 4,
                Instant.now(), Instant.now());
        when(productService.create(eq(request))).thenReturn(created);

        mockMvc.perform(post("/api/products")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/products/5"))
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void createReturns400WhenNameBlank() throws Exception {
        ProductRequest invalid = new ProductRequest("", "desc", new BigDecimal("10.00"), 1);

        mockMvc.perform(post("/api/products")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void createReturns400WhenPriceIsZero() throws Exception {
        ProductRequest invalid = new ProductRequest("Cable", "desc", new BigDecimal("0.00"), 1);

        mockMvc.perform(post("/api/products")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/products/{id}", 1L))
                .andExpect(status().isNoContent());
    }
}
```

> Note `@MockitoBean` (from `org.springframework.test.context.bean.override.mockito`),
> not the older `@MockBean` (`org.springframework.boot.test.mock.mockito`) —
> Spring Boot 3.4+ deprecated the latter in favor of the framework-level annotation.

**`backend/src/test/java/com/example/productcrud/product/ProductRepositoryIntegrationTest.java`**
— `@DataJpaTest` against the real `productcrud_test` database, exercising
Flyway migrations and column constraints exactly as in production:
```java
package com.example.productcrud.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void savesAndReloadsProductWithTimestamps() {
        Product product = new Product();
        product.setName("Webcam");
        product.setDescription("1080p");
        product.setPrice(new BigDecimal("149.99"));
        product.setQuantity(8);

        Product saved = productRepository.save(product);

        assertThat(saved.getId()).isNotNull();

        Product reloaded = productRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Webcam");
        assertThat(reloaded.getPrice()).isEqualByComparingTo("149.99");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void deleteRemovesProduct() {
        Product product = new Product();
        product.setName("Headset");
        product.setPrice(new BigDecimal("59.00"));
        product.setQuantity(2);
        Product saved = productRepository.save(product);

        productRepository.delete(saved);

        assertThat(productRepository.findById(saved.getId())).isEmpty();
    }
}
```
`@AutoConfigureTestDatabase(replace = NONE)` is the key line — without it,
`@DataJpaTest` silently swaps in an embedded in-memory database instead of
using the real Postgres configured in `application.properties`.

**`backend/src/test/java/com/example/productcrud/product/ProductControllerIntegrationTest.java`**
— full stack, real HTTP, random port, real database:
```java
package com.example.productcrud.product;

import com.example.productcrud.product.dto.ProductRequest;
import com.example.productcrud.product.dto.ProductResponse;
import com.example.productcrud.web.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void fullCrudLifecycle() {
        ProductRequest createRequest = new ProductRequest("Laptop", "14-inch", new BigDecimal("1499.00"), 5);

        ResponseEntity<ProductResponse> createResponse =
                restTemplate.postForEntity("/api/products", createRequest, ProductResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long id = createResponse.getBody().id();
        assertThat(id).isNotNull();

        ResponseEntity<ProductResponse> getResponse =
                restTemplate.getForEntity("/api/products/{id}", ProductResponse.class, id);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().name()).isEqualTo("Laptop");

        ProductRequest updateRequest = new ProductRequest("Laptop Pro", "16-inch", new BigDecimal("1999.00"), 3);
        ResponseEntity<ProductResponse> updateResponse = restTemplate.exchange(
                "/api/products/{id}",
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(updateRequest),
                ProductResponse.class,
                id);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().name()).isEqualTo("Laptop Pro");
        assertThat(updateResponse.getBody().quantity()).isEqualTo(3);

        restTemplate.delete("/api/products/{id}", id);

        ResponseEntity<ApiError> afterDelete =
                restTemplate.getForEntity("/api/products/{id}", ApiError.class, id);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
```

Run all four:
```bash
$ ./mvnw test
```
Expected: `Tests run: 14, Failures: 0, Errors: 0`. Per-class breakdown:
```bash
$ for f in target/surefire-reports/*.txt; do grep "Tests run" "$f"; done
```

---

## Part 17 — Backend: package and run

```bash
# Executable JAR -> target/product-crud-0.0.1-SNAPSHOT.jar
$ ./mvnw clean package

# Run it (dev mode, auto-reload via devtools)
$ ./mvnw spring-boot:run

# ...or run the packaged jar directly
$ java -jar target/product-crud-0.0.1-SNAPSHOT.jar
```

Smoke test with curl once it's up on `:8080`:
```bash
$ curl -s -X POST http://localhost:8080/api/products \
    -H "Content-Type: application/json" \
    -d '{"name":"Keyboard","description":"Mechanical","price":199.90,"quantity":10}'
$ curl -s http://localhost:8080/api/products
```

---

## Part 18 — Frontend: generate the Angular skeleton

```bash
$ cd .. # back to product-crud/
$ npx @angular/cli@latest new frontend \
  --routing \
  --style=css \
  --ssr=false \
  --skip-git \
  --package-manager=npm \
  --test-runner=vitest \
  --file-name-style-guide=2016
```

Flag rationale:
- `--test-runner=vitest` — Angular 22's current default (replaces Karma/Jasmine).
- `--file-name-style-guide=2016` — keeps classic `*.component.ts`/`*.service.ts`
  file names instead of the new terse `2025` style (`product-list.ts`). Chosen
  deliberately: it's what almost every existing Angular tutorial uses.
- `--ssr=false` — no server-side rendering, keeps this a plain SPA.
- `--skip-git` — this tutorial doesn't assume a git repo at this level.

> **Class names are still terse regardless of the file-naming flag** — e.g. a
> generated component class is `ProductList`, not `ProductListComponent`, and
> a generated service class is just `Product`. The file-naming flag only
> controls the *file* suffix. Watch out for the service case specifically: a
> service named after its entity (`Product`) collides with the `Product`
> model interface the moment both are imported in the same file — rename the
> generated service class to `ProductService` by hand (done in Part 23).

---

## Part 19 — Frontend: environment configuration

```bash
$ cd frontend
$ npx ng generate environments
```

This creates `src/environments/environment.ts` and
`environment.development.ts`, and wires `fileReplacements` into `angular.json`
automatically. Fill them in:

**`frontend/src/environments/environment.ts`** (production):
```ts
export const environment = {
  production: true,
  apiBaseUrl: '/api',
};
```

**`frontend/src/environments/environment.development.ts`** (dev server):
```ts
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080/api',
};
```

---

## Part 20 — Frontend: wire up HttpClient

**`frontend/src/app/app.config.ts`**
```ts
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';

import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient()
  ]
};
```

---

## Part 21 — Frontend: generate the model, service, and components

```bash
$ npx ng generate interface core/models/product
$ npx ng generate service core/services/product
$ npx ng generate component features/products/product-list --standalone
$ npx ng generate component features/products/product-form --standalone
```

---

## Part 22 — Frontend: the model

**`frontend/src/app/core/models/product.ts`**
```ts
export interface Product {
  id: number;
  name: string;
  description: string | null;
  price: number;
  quantity: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProductRequest {
  name: string;
  description: string | null;
  price: number;
  quantity: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
```

---

## Part 23 — Frontend: the HTTP service

**`frontend/src/app/core/services/product.service.ts`**
```ts
import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Page, Product, ProductRequest } from '../models/product';

@Service()
export class ProductService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/products`;

  findAll(page = 0, size = 20): Observable<Page<Product>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<Product>>(this.baseUrl, { params });
  }

  findById(id: number): Observable<Product> {
    return this.http.get<Product>(`${this.baseUrl}/${id}`);
  }

  create(request: ProductRequest): Observable<Product> {
    return this.http.post<Product>(this.baseUrl, request);
  }

  update(id: number, request: ProductRequest): Observable<Product> {
    return this.http.put<Product>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
```

> `@Service()` is this Angular version's modern decorator for an
> auto-provided injectable (equivalent to the older
> `@Injectable({ providedIn: 'root' })`). `Injectable` still exists if you
> prefer the familiar spelling — both work.

---

## Part 24 — Frontend: the product list component

**`frontend/src/app/features/products/product-list/product-list.component.ts`**
```ts
import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { Product } from '../../../core/models/product';
import { ProductService } from '../../../core/services/product.service';

@Component({
  selector: 'app-product-list',
  imports: [RouterLink, DecimalPipe],
  templateUrl: './product-list.component.html',
  styleUrl: './product-list.component.css',
})
export class ProductList implements OnInit {
  private readonly productService = inject(ProductService);

  readonly products = signal<Product[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadProducts();
  }

  loadProducts(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.productService.findAll().subscribe({
      next: (page) => {
        this.products.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not load products. Is the backend running?');
        this.loading.set(false);
      },
    });
  }

  deleteProduct(product: Product): void {
    if (!confirm(`Delete "${product.name}"?`)) {
      return;
    }
    this.productService.delete(product.id).subscribe({
      next: () => this.products.update((current) => current.filter((p) => p.id !== product.id)),
      error: () => this.errorMessage.set('Could not delete the product.'),
    });
  }
}
```

**`frontend/src/app/features/products/product-list/product-list.component.html`**
```html
<div class="page">
  <header class="page-header">
    <h1>Products</h1>
    <a class="btn btn-primary" routerLink="/products/new">+ New product</a>
  </header>

  @if (loading()) {
    <p>Loading...</p>
  }

  @if (errorMessage()) {
    <p class="error">{{ errorMessage() }}</p>
  }

  @if (!loading() && products().length === 0 && !errorMessage()) {
    <p>No products yet. Create the first one.</p>
  }

  @if (products().length > 0) {
    <table class="table">
      <thead>
        <tr>
          <th>Name</th>
          <th>Description</th>
          <th>Price</th>
          <th>Quantity</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        @for (product of products(); track product.id) {
          <tr>
            <td>{{ product.name }}</td>
            <td>{{ product.description || '-' }}</td>
            <td>{{ product.price | number: '1.2-2' }}</td>
            <td>{{ product.quantity }}</td>
            <td class="actions">
              <a class="btn btn-small" [routerLink]="['/products', product.id, 'edit']">Edit</a>
              <button class="btn btn-small btn-danger" type="button" (click)="deleteProduct(product)">Delete</button>
            </td>
          </tr>
        }
      </tbody>
    </table>
  }
</div>
```

---

## Part 25 — Frontend: the product form component (create + edit)

One component handles both modes — presence of an `:id` route param switches
it into edit mode and pre-loads the existing product.

**`frontend/src/app/features/products/product-form/product-form.component.ts`**
```ts
import { Component, OnInit, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { ProductService } from '../../../core/services/product.service';

@Component({
  selector: 'app-product-form',
  imports: [ReactiveFormsModule],
  templateUrl: './product-form.component.html',
  styleUrl: './product-form.component.css',
})
export class ProductForm implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly productService = inject(ProductService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly productId = signal<number | null>(null);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    description: ['', [Validators.maxLength(1000)]],
    price: [0, [Validators.required, Validators.min(0.01)]],
    quantity: [0, [Validators.required, Validators.min(0)]],
  });

  get isEditMode(): boolean {
    return this.productId() !== null;
  }

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) {
      return;
    }
    const id = Number(idParam);
    this.productId.set(id);
    this.productService.findById(id).subscribe({
      next: (product) =>
        this.form.patchValue({
          name: product.name,
          description: product.description ?? '',
          price: product.price,
          quantity: product.quantity,
        }),
      error: () => this.errorMessage.set('Could not load the product to edit.'),
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.errorMessage.set(null);
    const request = this.form.getRawValue();

    const result$ = this.isEditMode
      ? this.productService.update(this.productId()!, request)
      : this.productService.create(request);

    result$.subscribe({
      next: () => this.router.navigate(['/products']),
      error: () => {
        this.errorMessage.set('Could not save the product. Check the field values.');
        this.saving.set(false);
      },
    });
  }

  cancel(): void {
    this.router.navigate(['/products']);
  }
}
```

**`frontend/src/app/features/products/product-form/product-form.component.html`**
```html
<div class="page">
  <h1>{{ isEditMode ? 'Edit product' : 'New product' }}</h1>

  @if (errorMessage()) {
    <p class="error">{{ errorMessage() }}</p>
  }

  <form [formGroup]="form" (ngSubmit)="submit()" class="form">
    <label class="field">
      <span>Name</span>
      <input type="text" formControlName="name" />
      @if (form.controls.name.invalid && form.controls.name.touched) {
        <small class="error">Name is required (max 120 characters).</small>
      }
    </label>

    <label class="field">
      <span>Description</span>
      <textarea formControlName="description" rows="3"></textarea>
    </label>

    <label class="field">
      <span>Price</span>
      <input type="number" step="0.01" formControlName="price" />
      @if (form.controls.price.invalid && form.controls.price.touched) {
        <small class="error">Price must be greater than zero.</small>
      }
    </label>

    <label class="field">
      <span>Quantity</span>
      <input type="number" formControlName="quantity" />
      @if (form.controls.quantity.invalid && form.controls.quantity.touched) {
        <small class="error">Quantity cannot be negative.</small>
      }
    </label>

    <div class="actions">
      <button type="submit" class="btn btn-primary" [disabled]="saving()">
        {{ saving() ? 'Saving...' : 'Save' }}
      </button>
      <button type="button" class="btn" (click)="cancel()">Cancel</button>
    </div>
  </form>
</div>
```

---

## Part 26 — Frontend: routes and the app shell

**`frontend/src/app/app.routes.ts`**
```ts
import { Routes } from '@angular/router';

import { ProductList } from './features/products/product-list/product-list.component';
import { ProductForm } from './features/products/product-form/product-form.component';

export const routes: Routes = [
  { path: '', redirectTo: 'products', pathMatch: 'full' },
  { path: 'products', component: ProductList },
  { path: 'products/new', component: ProductForm },
  { path: 'products/:id/edit', component: ProductForm },
];
```

**`frontend/src/app/app.component.ts`** (edit the generated one — just the title):
```ts
import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class App {
  protected readonly title = signal('Product CRUD');
}
```

**`frontend/src/app/app.component.html`** (replace the generated placeholder
content entirely):
```html
<nav class="navbar">
  <span class="brand">{{ title() }}</span>
</nav>
<main>
  <router-outlet />
</main>
```

---

## Part 27 — Frontend: global styles

**`frontend/src/styles.css`**
```css
* {
  box-sizing: border-box;
}

body {
  margin: 0;
  font-family: system-ui, -apple-system, sans-serif;
  color: #1f2933;
  background: #f5f7fa;
}

.navbar {
  background: #1f2933;
  color: white;
  padding: 0.75rem 1.5rem;
}

.brand {
  font-weight: 600;
  font-size: 1.1rem;
}

main {
  padding: 1.5rem;
  max-width: 960px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1rem;
}

.table {
  width: 100%;
  border-collapse: collapse;
  background: white;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.table th,
.table td {
  text-align: left;
  padding: 0.6rem 0.8rem;
  border-bottom: 1px solid #e4e7eb;
}

.table .actions {
  display: flex;
  gap: 0.5rem;
}

.btn {
  display: inline-block;
  padding: 0.45rem 0.9rem;
  border-radius: 4px;
  border: 1px solid #cbd2d9;
  background: white;
  color: #1f2933;
  text-decoration: none;
  cursor: pointer;
  font-size: 0.9rem;
}

.btn-small {
  padding: 0.3rem 0.6rem;
  font-size: 0.8rem;
}

.btn-primary {
  background: #2563eb;
  border-color: #2563eb;
  color: white;
}

.btn-danger {
  background: #dc2626;
  border-color: #dc2626;
  color: white;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 480px;
  background: white;
  padding: 1.5rem;
  border-radius: 6px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.field input,
.field textarea {
  padding: 0.5rem;
  border: 1px solid #cbd2d9;
  border-radius: 4px;
  font-size: 0.95rem;
}

.actions {
  display: flex;
  gap: 0.75rem;
}

.error {
  color: #dc2626;
}
```

---

## Part 28 — Frontend: build check

```bash
$ npx ng build
```
Expect `Application bundle generation complete` and output under `dist/frontend/`.

---

## Part 29 — Frontend: automated tests

Angular 22's default runner is **Vitest**, not Karma/Jasmine — use `vi.fn()` /
`vi.spyOn()`, not `jasmine.createSpyObj`/`spyOn`. `describe`/`it`/`expect`/`vi`
are globally available (no import needed), matching the CLI-generated specs.

**`frontend/src/app/app.component.spec.ts`**
```ts
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app.component';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the navbar brand', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.brand')?.textContent).toContain('Product CRUD');
  });
});
```

**`frontend/src/app/core/services/product.service.spec.ts`**
```ts
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { ProductService } from './product.service';
import { Product, ProductRequest } from '../models/product';

describe('ProductService', () => {
  let service: ProductService;
  let httpMock: HttpTestingController;
  const apiUrl = 'http://localhost:8080/api/products';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProductService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('findAll sends a GET request with paging params', () => {
    service.findAll(1, 10).subscribe();

    const req = httpMock.expectOne((r) => r.url === apiUrl);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 1, size: 10 });
  });

  it('findById sends a GET request to /products/:id', () => {
    const product: Product = {
      id: 1,
      name: 'Mouse',
      description: null,
      price: 50,
      quantity: 3,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    };

    service.findById(1).subscribe((result) => expect(result).toEqual(product));

    const req = httpMock.expectOne(`${apiUrl}/1`);
    expect(req.request.method).toBe('GET');
    req.flush(product);
  });

  it('create sends a POST request with the product payload', () => {
    const request: ProductRequest = { name: 'Mouse', description: null, price: 50, quantity: 3 };

    service.create(request).subscribe();

    const req = httpMock.expectOne(apiUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ id: 1, ...request, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' });
  });

  it('update sends a PUT request to /products/:id', () => {
    const request: ProductRequest = { name: 'Mouse Pro', description: null, price: 60, quantity: 5 };

    service.update(1, request).subscribe();

    const req = httpMock.expectOne(`${apiUrl}/1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush({ id: 1, ...request, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' });
  });

  it('delete sends a DELETE request to /products/:id', () => {
    service.delete(1).subscribe();

    const req = httpMock.expectOne(`${apiUrl}/1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
```

**`frontend/src/app/features/products/product-list/product-list.component.spec.ts`**
```ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ProductList } from './product-list.component';
import { ProductService } from '../../../core/services/product.service';
import { Page, Product } from '../../../core/models/product';

function buildProduct(overrides: Partial<Product> = {}): Product {
  return {
    id: 1,
    name: 'Mouse',
    description: 'Wireless',
    price: 50,
    quantity: 3,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('ProductList', () => {
  let component: ProductList;
  let fixture: ComponentFixture<ProductList>;
  let productService: { findAll: ReturnType<typeof vi.fn>; delete: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    productService = { findAll: vi.fn(), delete: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [ProductList],
      providers: [provideRouter([]), { provide: ProductService, useValue: productService }],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductList);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    productService.findAll.mockReturnValue(of({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 } as Page<Product>));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('loads products on init and exposes them via the signal', () => {
    const product = buildProduct();
    productService.findAll.mockReturnValue(
      of({ content: [product], totalElements: 1, totalPages: 1, number: 0, size: 20 } as Page<Product>),
    );

    fixture.detectChanges();

    expect(component.products()).toEqual([product]);
    expect(component.loading()).toBe(false);
  });

  it('sets an error message when loading fails', () => {
    productService.findAll.mockReturnValue(throwError(() => new Error('network error')));

    fixture.detectChanges();

    expect(component.errorMessage()).toContain('Could not load products');
  });

  it('removes the product from the list after a confirmed delete', () => {
    const product = buildProduct();
    productService.findAll.mockReturnValue(
      of({ content: [product], totalElements: 1, totalPages: 1, number: 0, size: 20 } as Page<Product>),
    );
    productService.delete.mockReturnValue(of(undefined));
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    fixture.detectChanges();
    component.deleteProduct(product);

    expect(productService.delete).toHaveBeenCalledWith(product.id);
    expect(component.products()).toEqual([]);
  });

  it('does not call delete when the confirmation is dismissed', () => {
    const product = buildProduct();
    productService.findAll.mockReturnValue(
      of({ content: [product], totalElements: 1, totalPages: 1, number: 0, size: 20 } as Page<Product>),
    );
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    fixture.detectChanges();
    component.deleteProduct(product);

    expect(productService.delete).not.toHaveBeenCalled();
  });
});
```

**`frontend/src/app/features/products/product-form/product-form.component.spec.ts`**
```ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ProductForm } from './product-form.component';
import { ProductService } from '../../../core/services/product.service';
import { Product } from '../../../core/models/product';

function buildProduct(overrides: Partial<Product> = {}): Product {
  return {
    id: 1,
    name: 'Mouse',
    description: 'Wireless',
    price: 50,
    quantity: 3,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('ProductForm', () => {
  let component: ProductForm;
  let fixture: ComponentFixture<ProductForm>;
  let productService: {
    findById: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  function setup(routeId: string | null) {
    productService = { findById: vi.fn(), create: vi.fn(), update: vi.fn() };

    TestBed.configureTestingModule({
      imports: [ProductForm],
      providers: [
        provideRouter([]),
        { provide: ProductService, useValue: productService },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap(routeId ? { id: routeId } : {}) } },
        },
      ],
    });

    fixture = TestBed.createComponent(ProductForm);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  }

  describe('create mode', () => {
    beforeEach(() => setup(null));

    it('should create with an empty form', () => {
      fixture.detectChanges();
      expect(component.isEditMode).toBe(false);
      expect(component.form.value).toEqual({ name: '', description: '', price: 0, quantity: 0 });
    });

    it('does not submit when the form is invalid', () => {
      fixture.detectChanges();
      component.submit();

      expect(productService.create).not.toHaveBeenCalled();
      expect(component.form.controls.name.touched).toBe(true);
    });

    it('calls create and navigates back to the list on success', () => {
      fixture.detectChanges();
      const created = buildProduct();
      productService.create.mockReturnValue(of(created));

      component.form.setValue({ name: 'Mouse', description: 'Wireless', price: 50, quantity: 3 });
      component.submit();

      expect(productService.create).toHaveBeenCalledWith({
        name: 'Mouse',
        description: 'Wireless',
        price: 50,
        quantity: 3,
      });
      expect(router.navigate).toHaveBeenCalledWith(['/products']);
    });

    it('surfaces an error message when create fails', () => {
      fixture.detectChanges();
      productService.create.mockReturnValue(throwError(() => new Error('boom')));

      component.form.setValue({ name: 'Mouse', description: '', price: 50, quantity: 3 });
      component.submit();

      expect(component.errorMessage()).toContain('Could not save');
    });
  });

  describe('edit mode', () => {
    beforeEach(() => setup('1'));

    it('loads the existing product and patches the form', () => {
      const existing = buildProduct();
      productService.findById.mockReturnValue(of(existing));

      fixture.detectChanges();

      expect(component.isEditMode).toBe(true);
      expect(productService.findById).toHaveBeenCalledWith(1);
      expect(component.form.value).toEqual({
        name: 'Mouse',
        description: 'Wireless',
        price: 50,
        quantity: 3,
      });
    });

    it('calls update with the product id on submit', () => {
      productService.findById.mockReturnValue(of(buildProduct()));
      fixture.detectChanges();
      productService.update.mockReturnValue(of(buildProduct({ name: 'Mouse Pro' })));

      component.form.patchValue({ name: 'Mouse Pro' });
      component.submit();

      expect(productService.update).toHaveBeenCalledWith(1, {
        name: 'Mouse Pro',
        description: 'Wireless',
        price: 50,
        quantity: 3,
      });
      expect(router.navigate).toHaveBeenCalledWith(['/products']);
    });
  });
});
```

Run them:
```bash
$ npx ng test
```
Expected: `Test Files 4 passed (4)`, `Tests 18 passed (18)`.

---

## Part 30 — Run everything together

Terminal 1:
```bash
$ cd backend
$ export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
$ export PATH="$JAVA_HOME/bin:$PATH"
$ ./mvnw spring-boot:run
```

Terminal 2:
```bash
$ cd frontend
$ npx ng serve
```

Open **http://localhost:4200/products** and exercise create / edit / delete.
Or hit the API directly:
```bash
$ curl http://localhost:8080/api/products
```

---

## Part 31 — Stopping everything

```bash
$ pkill -f "spring-boot:run"
$ pkill -f "ng serve"
```

---

## Troubleshooting

- **`FlywaySqlException: Postgres.app failed to verify "trust" authentication`**
  — Postgres.app's connection-permission dialog popped up and needs a click on
  your screen. Approve it once; it's remembered per client binary afterward.
- **`Cannot find namespace 'jasmine'` in a `.spec.ts`** — you're writing
  Jasmine-style test code (`jasmine.createSpyObj`, `spyOn`) in a project whose
  test runner is Vitest. Use `vi.fn()` / `vi.spyOn()` instead.
- **`JPA metamodel must not be empty` in a `@WebMvcTest`** — `@EnableJpaAuditing`
  is on the `@SpringBootApplication` class. Move it to its own `@Configuration`
  class (Part 13).
- **Port already in use** — something from a previous run is still listening.
  `lsof -i :8080` / `lsof -i :4200` to find the PID, then `kill <pid>`.

---

## Where to go from here

See `README.md` §9 in this repo for the prioritized list of next improvements
(Testcontainers, auth, OpenAPI docs, optimistic locking, pagination in the UI,
CI, Docker, and the eventual Spring Boot 4.x upgrade).
