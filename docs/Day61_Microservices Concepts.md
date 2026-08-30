Project: BookManagement | Date: August 30, 2026

Day 61 — Microservices Concepts
Starting point: what BookManagement is today
One Spring Boot app → compiled into one JAR → deployed as one unit on vishva-server-1.
All controllers, services, everything — packaged and deployed together, every time.
The monolith problem

If a bug is fixed in one small module (e.g. Payments, in a Swiggy-style example):

You can't deploy just that fix — building the project packages everything (every controller, every service) into one JAR.
Deploying means redeploying the entire application, even though only one part changed.
At company scale (many engineers, many modules), this becomes slow, risky, and frustrating — one small change forces a full redeploy of unrelated code.
The microservices fix

Break the application into separate, independently deployable services — e.g. a Payments service, an Orders service, a Restaurant-listing service. Each:

Has its own codebase
Has its own deployment
Can be fixed and redeployed without touching or redeploying the others

Confirmed advantage: fix a bug in one service → redeploy only that service → everything else keeps running untouched.

The new cost: communication changes

In the monolith, BookController calling UserService is a plain in-memory Java method call — instant, reliable, same JVM, same process.

Once Payments and Orders are separate applications (possibly on separate servers), there's no shared memory anymore. They can only talk over the network — typically HTTP/REST calls (same mechanism your React frontend already uses to talk to your Spring Boot backend, just now between two backend services instead of frontend↔backend).

Note on public vs. private network: services in the same VPC (like vishva-server-1 and vishva-database-3 today) talk over the private internal network, not the public internet — same pattern applies between microservices in the same VPC.

The new risk this introduces

Network calls are slower and less reliable than method calls:

The other service might be down, restarting, or slow
The network itself can hiccup or fail
None of this could happen with a plain Java method call inside one JVM
Day 61 summary
Monolith	Microservices
Deployment	One JAR, one deploy, everything coupled	Independent services, independent deploys
Communication	In-memory Java method calls (instant, reliable)	Network calls (HTTP) — slower, can fail
Fixing a bug	Requires full redeploy	Redeploy only the affected service
Day 62 — Service Discovery
The problem this solves

Even over a private network (not public — same VPC), IP addresses still change whenever a service:

Restarts
Redeploys
Scales up/down or moves to a different server

This is the exact same root problem behind the Elastic IP fix applied earlier to vishva-server-1 (its public IP kept changing on every stop/start, breaking GitHub Actions CI/CD) — except now it applies to every microservice, not just one EC2 instance, and it happens far more often at scale.

Concrete failure scenario
Payments is running at private IP 10.0.1.5. Orders hardcodes that IP to call it.
Payments restarts (crash, redeploy, scaling event) → gets a new private IP, e.g. 10.0.1.9.
Orders is still calling the old IP 10.0.1.5 → the request fails, even though Payments is actually alive and running fine — just at a different address now.
The solution: Service Discovery

A central registry (like a phone book) that solves this:

When a service starts up, it registers itself: "I'm Payments, I'm currently at 10.0.1.9."
When another service (Orders) needs to call Payments, it looks up Payments' current address from the registry first — instead of relying on a hardcoded IP.
If Payments restarts and changes IP, it re-registers, and Orders' next lookup automatically gets the correct, current address.
Real tools that implement this
Eureka (Spring Cloud Netflix) — common in Spring Boot microservices setups
AWS-native options: Cloud Map, or ECS Service Discovery
Day 62 summary
Without Service Discovery	With Service Discovery
Finding another service	Hardcoded IP — breaks when the service restarts	Look up current address from a registry before calling
On service restart	Caller keeps hitting the old, dead IP → request fails	Service re-registers new IP → callers get the correct address automatically
How Day 61 and 62 connect

Microservices (Day 61) gain deployment flexibility but pay for it with unreliable network communication between services. Service discovery (Day 62) is the first concrete tool that manages part of that unreliability — specifically, knowing where to even send the request as services move around. It doesn't yet solve what to do if the request fails after being sent correctly (timeouts, retries, circuit breakers) — that's further down the microservices roadmap.