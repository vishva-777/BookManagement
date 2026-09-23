# BookManagement

A full-stack book cataloging system built as a **microservices architecture** on AWS, demonstrating production-style patterns: service discovery (Eureka), a central API Gateway, JWT authentication, async messaging (SQS), and secrets management — not just CRUD.

Three independently deployable Spring Boot services (BookManagement, Eureka Server, API Gateway) run behind a single gateway, with a React frontend deployed on S3 and a MySQL database on RDS. Includes CI/CD via GitHub Actions and real production debugging (OOM handling, systemd, CORS, IAM least-privilege).

## Architecture

```
React Frontend (S3)
        │
        ▼
   API Gateway (Spring Cloud Gateway, port 8080)
        │  looks up real service address via Eureka
        ▼
Eureka Server (Service Registry, port 8761)
        │
        ▼
BookManagement Service (port 8090)
   ├── MySQL (RDS) — book/author data
   ├── AWS Secrets Manager — DB credentials, JWT secret
   └── AWS SQS (book-thumbnail-queue) — async thumbnail notifications
```

**Request flow:** The React app never talks to BookManagement directly — every request goes to the API Gateway, which asks Eureka for BookManagement's current address and forwards the call. This means only the Gateway needs public exposure; BookManagement, Eureka, and the database stay VPC-private.

**Deployment:** All three backend services run as independent systemd services on a single EC2 instance (t3.micro, Free Tier), each with capped JVM heap and swap enabled to survive running three JVMs on ~1GB RAM. GitHub Actions handles CI/CD for BookManagement on every push to `master`.

**Auth:** JWT-based — clients POST `/login` for a token, then attach `Authorization: Bearer <token>` on every protected request. No server-side session state.

## Tech Stack

**Backend**
- Java, Spring Boot, Spring Security (JWT)
- Spring Cloud Netflix Eureka (service discovery)
- Spring Cloud Gateway (WebFlux/Netty)
- Hibernate/JPA, MySQL

**Frontend**
- React (Vite), fetch API, localStorage for token persistence

**AWS**
- EC2, RDS (MySQL), S3 (static hosting), SQS, Secrets Manager, IAM (least-privilege inline policies)

**DevOps**
- GitHub Actions (CI/CD), systemd, Docker

## Setup & Run Instructions

### Prerequisites
- Java 17+, Maven
- Node.js + npm
- MySQL (or an AWS RDS instance)
- AWS account (for Secrets Manager, SQS, S3 — optional for local-only testing)

### 1. Eureka Server
```bash
cd eureka-server
./mvnw spring-boot:run
# runs on http://localhost:8761
```

### 2. BookManagement (backend)
```bash
cd BookManagement
# set environment variables: DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET
./mvnw spring-boot:run
# runs on http://localhost:8090
```

### 3. API Gateway
```bash
cd api-gateway
./mvnw spring-boot:run
# runs on http://localhost:8080
```

### 4. Frontend
```bash
cd bookmanagement-fronted
npm install
npm run dev
# runs on http://localhost:5173
```

### Usage
- Register/login via the frontend (or `POST /login`) to get a JWT
- All `/books` and `/author` endpoints require `Authorization: Bearer <token>`

## Known Limitations / Future Work

- **Author selection on Add/Edit** is by manual Author ID entry — no dropdown, since `/books` doesn't expose the full author list inline. A future improvement would add a `GET /author` dropdown to the form.
- **CI/CD** is currently set up only for `BookManagement`; `eureka-server` and `api-gateway` are deployed manually and would benefit from their own GitHub Actions pipelines.
- **bookmanagement.service** secrets are stored as plaintext environment variables in the systemd unit file rather than pulled fresh via Secrets Manager at runtime — a future cleanup item.
- **Single EC2 instance** runs all three backend services together (cost-optimized for AWS Free Tier); a production setup would likely separate these or use ECS/EKS with proper autoscaling.
- **No automated tests** yet — all verification so far has been manual/curl-based.