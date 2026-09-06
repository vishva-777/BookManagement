# BookManagement — Day 14 to Day 18 Summary
**Project:** BookManagement (port 8090, AWS RDS `vishva-database-3`)
**Repo:** github.com/vishva-777/BookManagement

---

## Day 14 — Spring Boot + RDS + EC2

**What we learned:**
- `@Entity` maps a Java class to a database table (doesn't define fields — just marks the class as a table).
- `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)` → primary key, auto-incremented by MySQL itself (not by Java code).
- `JpaRepository<Entity, IdType>` — second generic parameter must always match the `@Id` field's type. It determines the type used in generated methods like `findById()`.
- Repository interfaces have no code inside — Spring builds a working **proxy class** at runtime, which internally uses Hibernate to generate SQL.
- `@RestController` + `@RequestMapping` — defines REST endpoints, returns JSON.
- `@RequestBody` — Jackson converts incoming JSON into a Java object using matching field names + setters.
- Getters/setters are required for Jackson to read (getter) and write (setter) object fields during JSON conversion.
- Flow: Controller → Repository (proxy) → Hibernate generates SQL → MySQL (RDS) executes it → generated ID written back into the Java object → returned to client.
- RDS chosen over self-hosted MySQL on EC2 for: automatic backups, independent scaling of app vs database, and AWS-managed patching — not just crash safety.

---

## Day 15 — Multiple Entities CRUD (Book + Author)

**What we learned:**
- Why a separate `Author` entity instead of author fields inside `Book`: avoids **data redundancy** — without it, author info repeats across every book row, risking inconsistency if updated in only some rows (normalization).
- `Author` entity built with the same pattern as `Book` (`@Entity`, `@Id`, `@GeneratedValue`, getters/setters).
- On Day 15, `Book` and `Author` tables are completely independent — no foreign key, no relationship yet.
- `Long` (not `String`) used for `@Id` because `GenerationType.IDENTITY` requires a numeric auto-increment column — MySQL can't auto-increment a `String`.
- Each Controller's `@RequestMapping` base path (`/books`, `/authors`) keeps routing fully separate.

---

## Day 16 — Relationships + Exception Handling

**Relationships:**
- Foreign key placement rule: goes on the **"many" side**, because a single column can only hold one value — the "one" side would need to store multiple values, which isn't possible in one column.
- `@ManyToOne` (in `Book.java`) = many books point to one author.
- `@JoinColumn(name = "author_id")` — names the actual foreign key column; doesn't create the relationship itself (that's `@ManyToOne`'s job).
- `@OneToMany(mappedBy = "author")` (in `Author.java`) = one author has many books.
- `mappedBy` points to the **field name** in the other entity (`Book.java`'s `author` field) — tells Hibernate not to create a duplicate column, since the real foreign key lives in `Book`.

**Exception Handling:**
- Custom exceptions (`ResourceNotFoundException extends RuntimeException`) give errors a distinct type, so different problems can be caught and handled differently (404 vs 400 vs 500).
- `@RestControllerAdvice` + `@ExceptionHandler` on `GlobalExceptionHandler` catch exceptions globally, across every controller in the project — written once, applies everywhere.
- Flow: Controller throws `ResourceNotFoundException` → `GlobalExceptionHandler` catches it → returns clean `404` with message, instead of a raw stack trace.

---

## Day 17 — Validation

**What we learned:**
- Without validation, invalid data (empty title, negative price) saves straight into MySQL with no resistance.
- `@NotBlank` rejects null, empty string, AND whitespace-only values.
- `@Min(value = 1)` rejects values below the threshold.
- `@Valid` (added in the Controller method signature) is the **trigger** — without it, validation annotations on the entity do nothing.
- When `@Valid` fails, Spring automatically throws `MethodArgumentNotValidException`.
- `GlobalExceptionHandler` catches this exception, loops through `getBindingResult().getFieldErrors()`, and returns **all** failing fields together in one response (not just the first error) with `400 Bad Request`.

---

## Day 18 — Spring Security (Basic Auth)

**What we learned:**
- Without security, any request from anywhere (no login) can access every endpoint.
- `SecurityConfig` class (`@Configuration`, `@EnableWebSecurity`) centrally configures security — Controllers themselves contain no authentication logic.
- CSRF disabled because it protects against browser-cookie-based session attacks, which don't apply to a stateless REST API called via Postman/mobile apps (no browser session to hijack).
- `.anyRequest().authenticated()` — locks every endpoint; unauthenticated requests get `401 Unauthorized`.
- `.httpBasic()` — enables Basic Auth (username + password sent with each request).
- `BCryptPasswordEncoder` — one-way hashing:
    - Passwords are never stored in plain text or reversibly encrypted.
    - At login, the typed password is re-hashed and compared against the stored hash (never decrypted back).
    - BCrypt adds a random "salt," so identical passwords produce different stored hashes.
- Flow: request hits Spring Security's filter **before** reaching any Controller → credentials checked → only valid requests proceed to `BookController`/`AuthorController`.

---

## Next: Day 19 — JWT Authentication