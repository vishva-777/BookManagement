# Day 62 Hands-On — Building & Registering with Eureka Server
**Project:** BookManagement + new eureka-server | **Date:** August 30, 2026

---

## What we built

### 1. A standalone `eureka-server` project (the "phonebook" itself)

**Why separate from BookManagement:** Day 61 established that microservices should be independently deployable — bundling the registry inside one app would mean it goes down whenever that app restarts, and every other future service would depend on one specific app being alive.

**How it was created:**
- Via [start.spring.io](https://start.spring.io) (manual, not CLI)
- Project: Maven, Language: Java, Java 17
- Group: `com.vishva`, Artifact: `eureka-server`
- Dependency: **"Eureka Server"** (not "Eureka Discovery Client" — that's for apps that register *into* a registry, not for the registry itself)
- Extracted to `C:\Users\vishv\OneDrive\Desktop\eureka-server` — a sibling folder to `BookManagement`, not nested inside it

**Code changes:**
- Added `@EnableEurekaServer` above `@SpringBootApplication` in `EurekaServerApplication.java` — this is the annotation that turns a plain Spring Boot app into an actual registry server
- `application.properties`:
  ```properties
  spring.application.name=eureka-server
  server.port=8761
  eureka.client.register-with-eureka=false
  eureka.client.fetch-registry=false
  ```
  The last two lines stop Eureka Server from also trying to act as a client of itself (its default behavior) — it should only serve as the registry.

**Ran with:** `./mvnw spring-boot:run` — confirmed alive at `http://localhost:8761`, showing the Eureka dashboard with "No instances available" (expected — nothing had registered yet).

---

### 2. Made `BookManagement` register itself as a Eureka client

**`pom.xml` changes** (permanent):
- Added a `spring-cloud.version` property (`2025.0.0`)
- Added a `<dependencyManagement>` block importing `spring-cloud-dependencies` as a BOM — this doesn't add anything to the build itself, it just tells Maven which version to use *if* a Spring Cloud dependency is added
- Added the actual dependency in `<dependencies>`:
  ```xml
  <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
  </dependency>
  ```

**`application.properties` changes** (permanent):
```properties
spring.application.name=BookManagement
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
```
- `spring.application.name` is the name the service shows up as in the registry (`BOOKMANAGEMENT`)
- `defaultZone` tells the app where to find the registry — currently hardcoded to `localhost`, which only works for local testing (see Open Follow-up below)

---

## Debugging chain (local test only — none of these were real bugs)

Getting `BookManagement` to actually boot locally surfaced five separate issues, one after another:

1. **Stale duplicate `HealthController`** — Spring found two bean definitions with the same name. Root cause: an old `.class` file left behind in `target/classes` from before `HealthController` was moved between packages. Fixed with `./mvnw clean` before rebuilding. Not related to Eureka at all — a pre-existing artifact of Maven's incremental build.

2. **`SecretsManagerInitializer` crash** — it authenticates to AWS via the EC2 IAM role (`bookmanagement-ec2-secrets-role`), which only exists on `vishva-server-1`. A laptop has no such role, so the AWS credential chain fails immediately. **Fix (temporary, local-only):** commented out the line in `BookManagementApplication.java` that registers this initializer, just for the test. Uncommented again afterward — this must stay active for real deployments.

3. **Missing `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` / `DB_ADMIN_PASSWORD` / `JWT_SECRET`** — with the initializer disabled, nothing set these system properties, and Spring refuses to start with unresolved `${...}` placeholders (confirmed: Spring does NOT leave them blank, it fails hard). **Fix:** set them manually via `$env:` in the PowerShell session, pointing at the real `vishva-database-3` RDS instance (db name `Bookdb`, found via `SHOW DATABASES;`).

4. **RDS connection timeout** — traced to a **stale security group rule**. The home-IP rule in `vishva-db-sg2` still referenced the old IP (`106.192.168.112/32`) from Day 59, but the laptop's ISP-assigned IP had since changed to `223.237.191.152`. This is the exact "IP changes over time" problem from Day 62's core lesson, just happening on a home network instead of a server. **Fix:** updated the SG rule to the current IP.

5. **RDS connection *still* timing out after the IP fix** — because `PubliclyAccessible` had been set to `No` at the end of Day 59. With no public IP assigned to the RDS instance at all, the laptop (outside the VPC) had no path to reach it, regardless of what the SG allowed. **Fix (temporary):** flipped `PubliclyAccessible` back to `Yes` just long enough to complete the test, then reverted to `No` immediately after confirming success.

**Key distinction drawn from this:** none of these failures happen on `vishva-server-1` in production — it has the IAM role (fixes #2) and sits inside the same VPC as RDS (fixes #4/#5 are irrelevant there). The local laptop was just correctly being denied access it was never meant to have.

---

## Result — confirmed success

Server logs showed:
```
Registering application BOOKMANAGEMENT with eureka with status UP
DiscoveryClient_BOOKMANAGEMENT/Vishvapari.mshome.net:BookManagement:8090 - registration status: 204
```

Eureka dashboard (`localhost:8761`) confirmed visually: **BOOKMANAGEMENT**, status **UP**, listed at `Vishvapari.mshome.net:BookManagement:8090`.

This is the phonebook pattern from Day 62's concept, now proven working end to end — not just theoretical.

---

## Open follow-up (not yet done)

`eureka.client.service-url.defaultZone=http://localhost:8761/eureka/` is hardcoded to `localhost` — this only works when both apps run on the same laptop. Deploying this to production requires:
- Deciding where `eureka-server` itself gets deployed (its own EC2 instance? Same instance as BookManagement, different port?)
- Updating `defaultZone` to point at that real address instead of `localhost`