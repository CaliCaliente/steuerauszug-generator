# Coding Standards — Angular 21

## Core Principles
- Prefer readability over cleverness — future-you will thank present-you
- Keep components, services, and functions small and single-purpose
- Avoid magic strings and numbers — use constants, enums, or typed models
- No commented-out code — delete it, Git has history

## Components
- Use standalone components — avoid NgModules for new code
- Keep components focused on presentation — delegate logic to services or store
- Prefer `OnPush` change detection for all components:
  ```typescript
  @Component({
    changeDetection: ChangeDetectionStrategy.OnPush
  })
  ```
- Use signals for local component state (Angular 21 idiomatic):
  ```typescript
  protected count = signal(0);
  protected doubled = computed(() => this.count() * 2);
  ```
- Prefer `input()` and `output()` signal-based APIs over `@Input()`/`@Output()` decorators
- Avoid logic in templates — extract into computed signals or getters
- Destroy subscriptions properly — prefer `takeUntilDestroyed()` from `@angular/core/rxjs-interop`

## Templates
- Keep templates clean and free of business logic
- Use `@if`, `@for`, `@switch` (control flow syntax) — not `*ngIf`, `*ngFor`
- Always provide a `track` expression in `@for`:
  ```html
  @for (item of items(); track item.id) { ... }
  ```
- Avoid complex expressions in bindings — move them to computed signals or getters

## Services
- Use services for all data fetching, business logic, and shared state
- Prefer `providedIn: 'root'` for singleton services unless scoping is needed
- Use the `inject()` function over constructor injection for cleaner code:
  ```typescript
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  ```
- Return `Observable` or `Signal` from services — don't subscribe inside services unless necessary
- Handle errors in services, not components — use `catchError` and return meaningful fallbacks

## State Management
- Use signals for local/component-level state
- For shared application state, use a signals-based store (e.g., NgRx SignalStore or a simple service with signals)
- Avoid storing derived data — compute it with `computed()`
- Keep state updates immutable — replace objects rather than mutating them

## Routing
- Use lazy loading for all feature routes:
  ```typescript
  { path: 'orders', loadComponent: () => import('./orders/orders.component') }
  ```
- Use typed route parameters and query params — avoid raw `snapshot.params['id']` casts
- Use route guards (`CanActivate`, `CanDeactivate`) for access control, not component logic

## TypeScript
- Enable `strict` mode in `tsconfig.json` — no exceptions
- Always type function parameters and return values explicitly
- Avoid `any` — use `unknown` and narrow types, or define proper interfaces/types
- Use `interface` for object shapes; use `type` for unions, intersections, or aliases
- Prefer `readonly` for properties that shouldn't change after initialization
- Use enums or union types for fixed sets of values — not raw strings

## HTTP & Data
- Use a dedicated API service layer — never call `HttpClient` from a component
- Type all HTTP responses — avoid `any`:
  ```typescript
  this.http.get<Order[]>('/api/orders')
  ```
- Handle loading and error states explicitly in your UI
- Use interceptors for cross-cutting concerns (auth headers, error handling, logging)

## Naming Conventions
- Components: `PascalCase` + `Component` suffix (`OrderListComponent`)
- Services: `PascalCase` + `Service` suffix (`OrderService`)
- Files: `kebab-case` (`order-list.component.ts`)
- Signals: camelCase nouns (`orders`, `selectedOrderId`, `isLoading`)
- Methods: camelCase verb phrases (`loadOrders`, `handleSubmit`)
- Interfaces/Types: `PascalCase`, no `I` prefix

## Testing
- Write unit tests for all services and complex component logic
- Use `TestBed` sparingly for component tests — prefer testing logic in isolation
- Test names should describe behavior: `should display error message when request fails`
- Mock HTTP calls with `HttpTestingController` — never make real HTTP calls in tests

## File Organization
- Feature-based folder structure — group by domain, not by type:
  ```
  src/
    app/
      orders/
        order-list.component.ts
        order-detail.component.ts
        order.service.ts
        order.model.ts
      payments/
        ...
  ```
- Keep barrel (`index.ts`) exports minimal — only export what's used externally
