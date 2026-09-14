# BookManagement

A full-stack book management system built as a hands-on microservices + React
project — covers service discovery, an API gateway, async messaging, JWT
auth, and a deployed full-stack app on AWS.

**Live app:** http://vishva-bookmanagement-frontend.s3-website.eu-north-1.amazonaws.com

## Architecture

```
┌─────────────────────┐
│  React frontend      │  S3 Static Website Hosting
│  (bookmanagement-    │
│   fronted)            │
└──────────┬───────────┘
           │ HTTPS/HTTP (JWT Bearer token)
           ▼
┌─────────────────────┐      ┌──────────────────┐
│   api-gateway         │◄────►│  eureka-server     │
│   (Spring Cloud       │      │  (service registry)│
│    Gateway, webflux)  │      └──────────────────┘
└──────────┬───────────┘
           │ routed via Eureka discovery
           ▼
┌─────────────────────┐      ┌──────────────────┐
│   BookManagement       │─────►│  MySQL (RDS)       │
│   (Spring Boot,        │      └──────────────────┘
│    JWT auth, CRUD)     │
└──────────┬───────────┘
           │ async (fire-and-forget)
           ▼
┌─────────────────────┐
│   SQS queue            │  book-thumbnail-queue
└─────────────────────┘
```

All three backend services (`eureka-server`, `BookManagement`, `api-gateway`)
run as independent systemd services on a single EC2 instance
(`vishva-server-1`), each in its own process — independently deployable, cost
kept down by sharing one Free Tier box.

## Tech stack

- **Backend:** Java 21, Spring Boot 3.5, Spring Security (JWT), Spring Data
  JPA/Hibernate, Spring Cloud (Eureka, Gateway)
- **Database:** MySQL on AWS RDS
- **Messaging:** AWS SQS (async notifications on book creation)
- **Frontend:** React 18 (Vite), plain `fetch`, `useState`/`useEffect`,
  `localStorage` for session persistence
- **Infra:** AWS EC2, RDS, S3 (static hosting), Secrets Manager, IAM
- **CI/CD:** GitHub Actions (BookManagement backend; auto build + deploy on
  push to `master`)

## Features

- JWT-based authentication (real login form, not hardcoded credentials)
- Session persistence across page refresh (`localStorage`)
- Full CRUD on books: create, read, update, delete — all reachable from the
  UI, not just via API
- Role-based authorization (`ROLE_ADMIN` / `ROLE_USER`)
- Service discovery via Eureka — no hardcoded service addresses
- Single public entry point via API Gateway
- Async notification on book creation via SQS
- CORS configured for both local dev (`localhost:5173`) and the deployed S3
  origin

## Repositories

| Repo | Purpose | CI/CD |
|---|---|---|
| [BookManagement](https://github.com/vishva-777/BookManagement) | Main Spring Boot API (books, auth) | GitHub Actions, auto-deploy to EC2 |
| [eureka-server](https://github.com/vishva-777/eureka-server) | Service registry | Manual deploy |
| [api-gateway](https://github.com/vishva-777/api-gateway) | Single entry point, routes via Eureka | Manual deploy |
| [bookmanagement-fronted](https://github.com/vishva-777/bookmanagement-fronted) | React frontend | Manual build (`npm run build`) + S3 upload |

## Running locally

### Backend
Each service needs its own AWS credentials (Secrets Manager access) to run
against the real RDS instance — see each repo's own setup. Locally, comment
out `SecretsManagerInitializer`'s registration in
`BookManagementApplication.java` and supply DB credentials via environment
variables instead (**remember to uncomment before pushing**).

```
./mvnw spring-boot:run
```

### Frontend
```
cd bookmanagement-fronted
npm install
npm run dev
```
Opens on `http://localhost:5173`.

## API overview

All endpoints except `/login`, `/error`, `/health` require a
`Authorization: Bearer <token>` header.

| Method | Path | Description |
|---|---|---|
| POST | `/login` | Authenticate, returns `{ "token": "..." }` |
| GET | `/books` | List all books (requires USER or ADMIN) |
| GET | `/books/{id}` | Get one book |
| POST | `/books` | Create a book (`title`, `description`, `price`, `author: { id }`) |
| PUT | `/books/{id}` | Update a book |
| DELETE | `/books/{id}` | Delete a book (ADMIN only) |
| GET | `/author` | List all authors |

## Known limitations

- The frontend's Edit form can't pre-fill the author ID, because
  `Book.author` is annotated `@JsonBackReference` (to avoid an infinite
  JSON loop with `Author.books`) and is never included in `GET /books`
  responses. The author ID must be typed manually when editing a book.
- `eureka-server` and `api-gateway` have no CI/CD yet — deploys are manual
  (build JAR → scp → systemd restart).
- The S3-hosted frontend is served over plain HTTP, no custom domain or
  HTTPS (would need CloudFront + an ACM certificate).
- `t3.micro` is memory-constrained running all three JVMs at once; a swap
  file (permanently registered in `/etc/fstab`) keeps this stable, but
  there's little headroom for further load.

## Project history

Built incrementally as part of a 90-day DevOps/AWS learning plan:

- **Days 61–65** — microservices fundamentals: service discovery (Eureka),
  API gateway pattern, sync vs. async communication (SQS), deployed all
  three services together on one EC2 instance
- **Days 66–68** — React frontend built from scratch, connected to the
  Spring Boot backend, deployed publicly on S3
- **Day 69** — real authentication (login form) and delete functionality
- **Day 70** — full CRUD in the UI (add/edit), session persistence, logout,
  and this documentation
