# Day 63 — API Gateway Pattern
**Project:** BookManagement + eureka-server + api-gateway | **Date:** September 01, 2026

---

## Part 1 — The Concept

### The problem
A frontend (React, or any outside client) needs data from multiple microservices — Orders, Payments, BookManagement, etc. If it calls each service **directly** by address, two problems appear:

1. **Addresses change.** Services restart, redeploy, get new IPs (same root problem as Day 62). The frontend would need constant updates to keep working.
2. **The frontend can't use Eureka.** Eureka is a backend-only tool — it lives inside the private network. A browser, running on a random user's laptop outside the infrastructure entirely, has no way to query it directly, even though a backend service like the gateway can.

### The solution — API Gateway
One single address (like a front desk in an apartment complex) that the frontend always talks to. The gateway:
- Receives every request from the frontend
- Looks at the request path to figure out which service it's for
- Asks **Eureka** for that service's current address (the gateway *can* do this — it's a backend service itself, with full network access)
- Forwards the request there, gets the response, sends it back

The frontend never needs to know Eureka exists, never needs individual service addresses, and doesn't even need to know there are multiple services behind the scenes.

### Security bonus
Before a gateway: every microservice needs its own public IP, its own SG, its own attack surface (same concepts as Day 59, multiplied by the number of services).

After a gateway: only the gateway needs to be public. Every other service (Orders, Payments, BookManagement) can be set to `PubliclyAccessible: No` — private, VPC-only. Fewer exposed doors = a much smaller attack surface to secure and monitor.

---

## Part 2 — Building It

### New project: `api-gateway`
Same pattern as `eureka-server` — its own independent Spring Boot project, sibling folder to `BookManagement` and `eureka-server`, so it can be deployed and scaled separately from any one backend service.

- Path: `C:\Users\vishv\OneDrive\Desktop\api-gateway`
- Maven, Java 17, Group `com.vishva`, Artifact `api-gateway`

### `application.properties`
```properties
spring.application.name=api-gateway
server.port=8080

eureka.client.service-url.defaultZone=http://localhost:8761/eureka/

spring.cloud.gateway.server.webflux.discovery.locator.enabled=true
spring.cloud.gateway.server.webflux.discovery.locator.lower-case-service-id=true
```
- `server.port=8080` — a distinct port from Eureka (`8761`) and BookManagement (`8090`); two processes can never share one port on the same machine
- `discovery.locator.enabled=true` — tells the gateway: automatically discover every service registered in Eureka and create a route to it, with no manual wiring needed
- `lower-case-service-id=true` — Eureka stores names uppercase (`BOOKMANAGEMENT`), but routes use lowercase (`/bookmanagement/**`)

### `ApiGatewayApplication.java`
No extra annotation needed — the Eureka client dependency auto-registers itself the moment it's on the classpath with `eureka.client.service-url.defaultZone` set (same as BookManagement).

---

## Part 3 — The Problem We Actually Hit (and How We Solved It)

### What happened
Visiting `http://localhost:8080/bookmanagement/health` returned a **Whitelabel Error Page — 404**, even though:
- Eureka showed both `BOOKMANAGEMENT` and `API-GATEWAY` registered and `UP`
- The gateway started with no errors
- The config looked correct on paper

A 404 handled by Spring's own default error page (not a gateway-specific error) meant the gateway never even tried to route the request anywhere — it just didn't recognize `/bookmanagement/health` as a route at all.

### Root cause #1 — wrong gateway dependency
The very first `pom.xml` used **`spring-cloud-starter-gateway-server-webmvc`** — a newer, MVC-based variant of Spring Cloud Gateway. Checked directly against Spring Cloud's own GitHub issue tracker: **this variant does not support automatic route discovery from Eureka at all** — it's an open, unimplemented feature request. The `discovery.locator.enabled=true` property simply has no effect on this module. Nothing was misconfigured — the feature genuinely doesn't exist yet for this variant.

**Fix:** switched to the classic reactive Gateway instead, which does support auto-discovery:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
</dependency>
```
(This also meant the gateway now runs on **Netty**, not Tomcat — required for the reactive/WebFlux style. Confirmed by the startup log: `Netty started on port 8080`.)

### Root cause #2 — property prefix had also changed
Even after switching to the correct dependency, the same 404 persisted. Spring Cloud's 2025.x release renamed the module and, alongside it, **changed the property prefix** to match:

- Old: `spring.cloud.gateway.discovery.locator.enabled`
- New: `spring.cloud.gateway.server.webflux.discovery.locator.enabled`

The property we'd set simply didn't exist anymore under its old name — Spring silently ignored it rather than erroring, which is why the failure looked identical to a routing problem rather than a typo.

**Fix:** updated `application.properties` to the new prefix (shown in full above).

### Result
Restarted the gateway, retried the URL, and got:
```
OK from Vishvapari
```
— the real response from `HealthController` on BookManagement (port 8090), returned through the gateway (port 8080) without ever calling BookManagement's address directly. Full request flow confirmed working end to end: frontend → gateway → Eureka lookup → BookManagement → response back through the gateway.

---

## Part 4 — Why This Debugging Session Mattered
Both root causes were the **same underlying lesson**: Spring Cloud's Gateway module went through a naming and API split in recent versions (WebFlux vs WebMVC variants, with different capabilities and different property prefixes). Tutorials and older documentation online still reference the old single `spring-cloud-starter-gateway` name and the old `spring.cloud.gateway.discovery...` prefix — neither works anymore on Spring Cloud `2025.1.3`. When following current instructions produces silent, no-error failures, it's worth checking whether the library itself has been restructured since the tutorial was written, not just checking your own config for typos.

---

## Quick-Reference Summary
- **Problem an API Gateway solves:** frontend can't/shouldn't call every microservice directly — no Eureka access from a browser, unmanageable addresses at scale
- **Solution:** one public address (the gateway) that looks up real service addresses via Eureka and forwards requests internally
- **Security bonus:** only the gateway needs to be public; everything else stays private (`PubliclyAccessible: No`)
- **Gotcha hit today:** `spring-cloud-starter-gateway-server-webmvc` does not support Eureka auto-discovery (open feature request, not a bug in our config) — use `spring-cloud-starter-gateway-server-webflux` instead
- **Gotcha hit today:** the discovery-locator property prefix changed to `spring.cloud.gateway.server.webflux.discovery.locator.*` in this Spring Cloud version