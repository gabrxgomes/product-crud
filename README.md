# Product CRUD

A small full-stack reference project: **Spring Boot 3.5 (Java 21) + PostgreSQL** backend
and an **Angular 22** frontend, both with automated tests. It implements one resource
(`Product`) end-to-end so the same pattern can be replicated for any other entity.

```
product-crud/
├── backend/   Spring Boot REST API (Maven)
└── frontend/  Angular SPA (Angular CLI)
```

---

## 1. Architecture

```
Angular (localhost:4200)  --HTTP/JSON-->  Spring Boot (localhost:8080)  --JDBC-->  PostgreSQL
```

- **Backend**: layered architecture — `Controller -> Service -> Repository -> Entity`,
  with DTOs at the boundary, Bean Validation, a global exception handler, and Flyway-managed
  schema migrations.
- **Frontend**: standalone Angular components (no NgModules), a single `ProductService`
  wrapping `HttpClient`, and the Router switching between a list view and a create/edit form.
- **Database**: one `products` table, schema owned entirely by Flyway (Hibernate is set to
  `validate`, never `update`/`create`, so the SQL migration files are the single source of truth).

---

## 2. Prerequisites

| Tool | Version used here | Check |
|---|---|---|
| JDK | 21 | `java -version` |
| Maven | none needed — the **Maven Wrapper** (`./mvnw`) is committed in `backend/` | — |
| Node.js | 26.x | `node -v` |
| npm | 11.x | `npm -v` |
| Angular CLI | 22.x (invoked via `npx`, no global install needed) | `npx ng version` |
| PostgreSQL | 15+ (any recent version) | `psql --version` |

If you don't have a JDK 21 yet, on macOS the path of least resistance is Homebrew's
*keg-only* `openjdk@21` (it doesn't touch your system Java, so it's safe to install
alongside an existing older JDK):

```bash
brew install openjdk@21
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

Add the two `export` lines to your shell profile (`~/.zshrc`) so every new terminal
picks up JDK 21, or just run them once per session before building/running the backend.

---

## 3. One-time database setup

The backend expects a database and a dedicated, low-privilege role (not your superuser)
to own it — this mirrors how you'd configure a real environment and keeps the app's
credentials separate from your personal Postgres login.

```sql
-- via: psql -h localhost -U <your_superuser> -d postgres
CREATE ROLE product_app LOGIN PASSWORD 'product_app_pwd';
CREATE DATABASE productcrud      OWNER product_app;  -- used when you run the app
CREATE DATABASE productcrud_test OWNER product_app;  -- used by the integration tests
```

Two databases, not one: the test suite runs real SQL against `productcrud_test` (see
§6) so it never touches your development data in `productcrud`.

> **macOS + Postgres.app users**: the first time a new client binary (e.g. the `java`
> process running this app) connects, Postgres.app shows a one-time "Allow this app to
> connect?" permission dialog. You must click **Allow** on screen — it can't be
> scripted. After the first approval it's remembered for that binary.

The backend never hardcodes these values — see `backend/src/main/resources/application.properties`,
which reads `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` with the
defaults above baked in, so it works out of the box but is fully overridable via
environment variables for any other environment.

---

## 4. How this project was created (replicate it from scratch)

These are the exact commands used to scaffold both halves of the project — useful if
you want to redo this for a different entity/domain.

### 4.1 Backend — generated via Spring Initializr

```bash
curl -s "https://start.spring.io/starter.zip" \
  -d type=maven-project \
  -d language=java \
  -d bootVersion=3.5.15 \
  -d baseDir=backend \
  -d groupId=com.example \
  -d artifactId=product-crud \
  -d name=product-crud \
  -d packageName=com.example.productcrud \
  -d packaging=jar \
  -d javaVersion=21 \
  -d dependencies=web,data-jpa,postgresql,validation,lombok,devtools,flyway \
  -o backend.zip
unzip backend.zip && rm backend.zip
```

This is identical to using [start.spring.io](https://start.spring.io) in a browser with
the same options, and it's what generated `backend/pom.xml`, `backend/mvnw`, and the
initial `src/` skeleton. *Note: at the time of writing, start.spring.io defaulted to the
brand-new Spring Boot 4.x line; this project deliberately pins `bootVersion=3.5.15`
(latest Spring Boot 3.x) because it's far better documented and matches the conventions
used in almost every existing Spring tutorial — see §9 for upgrading later.*

On top of the generated skeleton, the actual CRUD code was added by hand:
`Product` (entity), `ProductRepository`, `ProductService`, `ProductController`,
the `ProductRequest`/`ProductResponse` DTOs, `ProductMapper`, `ProductNotFoundException`,
the `web/` package (`ApiError`, `GlobalExceptionHandler`, `WebConfig` for CORS), the
`config/JpaAuditingConfig` class, and the Flyway migration in
`src/main/resources/db/migration/V1__create_products_table.sql`.

### 4.2 Frontend — generated via Angular CLI

```bash
npx @angular/cli@latest new frontend \
  --routing \
  --style=css \
  --ssr=false \
  --skip-git \
  --package-manager=npm \
  --test-runner=vitest \
  --file-name-style-guide=2016
```

Notes on these flags:
- `--test-runner=vitest`: Angular 22's new default test runner (replaces Karma/Jasmine).
- `--file-name-style-guide=2016`: keeps the classic `*.component.ts` / `*.service.ts`
  file naming instead of Angular's new terse `2025` style (e.g. `product-list.ts`).
  This was a deliberate choice — the 2016 convention is what virtually every existing
  Angular tutorial, course, and Stack Overflow answer uses, which matters for a project
  meant to be read and replicated. Note that even with `2016` file naming, **class
  names themselves are still terse by default** in this Angular version (`ProductList`,
  not `ProductListComponent`) — only the generated service class was renamed by hand
  (from `Product` to `ProductService`) because it collided with the `Product` model
  interface.

Then, on top of the generated skeleton:
- `npx ng generate environments` — added `src/environments/environment*.ts` for the API base URL.
- `npx ng generate interface core/models/product` — the `Product`/`ProductRequest`/`Page<T>` types.
- `npx ng generate service core/services/product` — `ProductService` (HTTP calls).
- `npx ng generate component features/products/product-list --standalone`
- `npx ng generate component features/products/product-form --standalone`
- `provideHttpClient()` was added to `app.config.ts`, and the routes were wired in `app.routes.ts`.

---

## 5. Project structure

### Backend (`backend/src/main/java/com/example/productcrud/`)

```
ProductCrudApplication.java        Entry point (@SpringBootApplication)
config/JpaAuditingConfig.java      @EnableJpaAuditing, kept OUT of the main class on purpose
                                    (see comment in the file — it breaks @WebMvcTest slices otherwise)
product/
  Product.java                     JPA entity (maps to the `products` table)
  ProductRepository.java           Spring Data JPA repository
  ProductService.java              Business logic, transaction boundaries
  ProductController.java           REST endpoints (/api/products)
  ProductMapper.java                Entity <-> DTO conversion (plain static methods, no MapStruct)
  ProductNotFoundException.java
  dto/ProductRequest.java          Inbound payload + Bean Validation annotations
  dto/ProductResponse.java         Outbound payload
web/
  ApiError.java                    Uniform error response shape
  GlobalExceptionHandler.java      @RestControllerAdvice: 404 / 400 / 500 mapping
  WebConfig.java                   CORS configuration (allows the Angular dev origin)
```

Migrations: `backend/src/main/resources/db/migration/V1__create_products_table.sql`.

### Frontend (`frontend/src/app/`)

```
app.component.ts / .html / .css    Root shell: navbar + <router-outlet>
app.routes.ts                      /products, /products/new, /products/:id/edit
app.config.ts                      provideRouter, provideHttpClient
core/
  models/product.ts                Product, ProductRequest, Page<T> TypeScript types
  services/product.service.ts      HttpClient calls to the backend
features/products/
  product-list/                    Table view + delete
  product-form/                    Reactive form, shared between create and edit
environments/                      apiBaseUrl per build configuration (dev vs prod)
```

---

## 6. Running the tests

### Backend — 4 layers, all passing against a real Postgres

```bash
cd backend
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw test
```

| Test class | What it covers | Needs a DB? |
|---|---|---|
| `ProductServiceTest` | Business logic, pure Mockito | No |
| `ProductControllerWebMvcTest` | HTTP layer, validation, status codes (`@WebMvcTest`, mocked service) | No |
| `ProductRepositoryIntegrationTest` | Real SQL via `@DataJpaTest` against `productcrud_test` | Yes |
| `ProductControllerIntegrationTest` | Full HTTP round trip (`@SpringBootTest`, random port) through the whole stack | Yes |

`src/test/resources/application.properties` overrides the datasource to point at
`productcrud_test` — that's the only thing that makes the integration tests safe to run
repeatedly without touching your dev data.

> Why not Testcontainers? It's the more modern choice for integration tests (a disposable,
> versioned Postgres container per test run), but it requires Docker, which wasn't
> installed on the machine this was built on. Pointing tests at a real local
> `productcrud_test` database was the pragmatic alternative. **If you have Docker, this
> is the first thing worth upgrading** — see §9.

### Frontend — Vitest

```bash
cd frontend
npx ng test
```

| Spec file | What it covers |
|---|---|
| `app.component.spec.ts` | Root shell renders |
| `product.service.spec.ts` | Every HTTP verb, asserted via `HttpTestingController` (no real network) |
| `product-list.component.spec.ts` | Loading state, rendering, delete confirm/cancel, error handling |
| `product-form.component.spec.ts` | Create mode, edit mode (loads + patches existing data), validation blocks submit, error handling |

---

## 7. Building

```bash
# Backend -> executable JAR at backend/target/product-crud-0.0.1-SNAPSHOT.jar
cd backend && ./mvnw clean package

# Frontend -> static files at frontend/dist/frontend/
cd frontend && npx ng build
```

---

## 8. Running the app locally

Open two terminals.

**Terminal 1 — backend** (serves the API on `:8080`):
```bash
cd backend
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw spring-boot:run
```

**Terminal 2 — frontend** (serves the SPA on `:4200`, proxies API calls to `:8080`
via `environment.development.ts`):
```bash
cd frontend
npx ng serve
```

Then open **http://localhost:4200/products**.

### API reference (if you want to skip the UI and use curl)

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/api/products?page=0&size=20` | — | Paged list |
| GET | `/api/products/{id}` | — | One product, or 404 |
| POST | `/api/products` | `{name, description, price, quantity}` | 201 + `Location` header |
| PUT | `/api/products/{id}` | same as POST | 200, or 404 |
| DELETE | `/api/products/{id}` | — | 204, or 404 |

Validation errors return `400` with a body like:
```json
{
  "status": 400, "error": "Bad Request", "message": "Validation failed",
  "fieldErrors": [{ "field": "name", "message": "name is required" }]
}
```

---

## 9. Suggested improvements (where to take this next)

Roughly in order of impact:

1. **Testcontainers for backend integration tests** — if Docker is available, swap
   the real local `productcrud_test` database for a Testcontainers-managed Postgres
   (`@ServiceConnection` + `@Testcontainers`). Removes the "you must have a local
   Postgres with this exact role" setup requirement for anyone else running the tests.
2. **Authentication/authorization** — there's currently none; every endpoint is public.
   Spring Security + JWT (or OAuth2/OIDC) would be the natural next layer.
3. **API documentation** — add `springdoc-openapi` for a generated Swagger UI; very
   low effort, high payoff for anyone integrating with the API.
4. **Optimistic locking** — add `@Version` to `Product` to guard against lost updates
   when two clients edit the same row concurrently.
5. **Pagination/sorting/search in the UI** — the backend already supports `Pageable`;
   the Angular list view currently just requests the first page. Wiring up page
   controls, column sorting, and a name filter is mostly frontend work.
6. **CI pipeline** — a GitHub Actions workflow running `./mvnw test` (with a Postgres
   service container) and `npx ng test --watch=false` on every PR.
7. **Containerize** — a `Dockerfile` per app plus a `docker-compose.yml` that also runs
   Postgres, so `docker compose up` is the only setup step for a new contributor.
8. **Upgrade path to Spring Boot 4.x** — this project pinned 3.5.15 deliberately (see
   §4.1); once the ecosystem (IDEs, libraries, docs) catches up, moving to 4.x mainly
   means revisiting the renamed starters (`spring-boot-starter-webmvc`) and the new
   per-starter test dependencies.
9. **MapStruct** instead of the hand-written `ProductMapper` — only worth it once you
   have more than one or two entities to map.
