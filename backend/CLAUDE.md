# Coding Standards — Spring Boot + Kotlin

## Key Specifications
The resulting PDF must always be compliant with this standard: https://www.ech.ch/sites/default/files/imce/eCH-Dossier/0181-0210/eCH-0196/2.0/Beilagen/eCH-0196%20E-Steuerauszug_Technische%20Wegleitung%202.0.pdf

## Kotlin Principles
- Prefer `val` over `var` — only use `var` when mutation is truly necessary
- Use data classes for DTOs, value objects, and simple models
- Leverage Kotlin's null safety — avoid `!!` (non-null assertion); use `?.`, `?:`, or `requireNotNull()` with a message
- Prefer expression bodies for simple functions:
  ```kotlin
  // Preferred
  fun fullName() = "$firstName $lastName"

  // Avoid for simple cases
  fun fullName(): String {
      return "$firstName $lastName"
  }
  ```
- Use `when` over long `if/else if` chains
- Use named arguments when calling functions with multiple parameters of the same type
- Prefer extension functions over utility classes

## Code Structure
- Keep functions small and single-purpose — aim for under 20 lines
- Avoid deep nesting — prefer early returns and guard clauses
- No magic numbers or strings — use named constants or enums
- One responsibility per class — if a class name contains "And", it's doing too much

## Spring Boot Conventions
- Use constructor injection — never field injection (`@Autowired` on fields)
  ```kotlin
  // Preferred
  @Service
  class OrderService(
      private val orderRepository: OrderRepository,
      private val paymentClient: PaymentClient
  )
  ```
- Annotate service classes with `@Service`, repositories with `@Repository`, controllers with `@RestController`
- Keep controllers thin — no business logic; delegate to services
- Keep services focused on business logic — no direct DB queries; delegate to repositories
- Use `@Transactional` at the service layer, not the controller layer

## API Design
- Use RESTful naming: nouns for resources (`/orders`), HTTP verbs for actions
- Return appropriate HTTP status codes — use `ResponseEntity` when status needs to vary
- Use DTOs for request/response bodies — never expose JPA entities directly in API responses
- Validate incoming requests with Bean Validation (`@Valid`, `@NotBlank`, `@Size`, etc.)
- Handle exceptions centrally using `@ControllerAdvice`

## Data & Persistence
- Prefer Spring Data JPA repository interfaces over custom implementations where possible
- Define relationships carefully — default to lazy loading for collections (`FetchType.LAZY`)
- Avoid N+1 queries — use `JOIN FETCH` or projections when loading related data
- Use Flyway or Liquibase for schema migrations — never rely on `spring.jpa.hibernate.ddl-auto=update` in non-dev environments

## Error Handling
- Never swallow exceptions silently
- Use custom exception classes that are meaningful to the domain (e.g., `OrderNotFoundException`)
- Map exceptions to HTTP responses in a central `@ControllerAdvice`, not scattered across controllers

## Testing
- Write unit tests for all service-layer logic
- Use `@SpringBootTest` sparingly — prefer slice tests (`@WebMvcTest`, `@DataJpaTest`) for speed
- Use MockK for mocking (not Mockito) — it's idiomatic for Kotlin
- Test names should describe behavior: `should return 404 when order not found`

## Naming
- Classes: `PascalCase` — descriptive nouns (`OrderService`, `PaymentController`)
- Functions: `camelCase` — verb phrases (`calculateTotal`, `findActiveOrders`)
- Constants: `UPPER_SNAKE_CASE`
- Packages: lowercase, domain-driven (`com.example.orders`, `com.example.payments`)

## Comments
- Comment *why*, not *what* — the code explains what; comments explain intent
- Avoid commented-out code — delete it, Git has history
- Use KDoc for public APIs