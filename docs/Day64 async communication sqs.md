# Day 64 — Inter-Service Communication (Sync vs Async) + SQS Hands-On
**Project:** BookManagement | **Date:** September 01–02, 2026

*Written so someone with zero AWS background can follow it.*

---

## Part 1 — The Concept: Two Ways Services Can Talk to Each Other

### Analogy: A Restaurant Kitchen
Imagine the Orders station needs to tell the Payments station "charge this customer ₹500." There are two ways to send that message:

- **Option 1 (Synchronous):** The Orders cook walks over, hands Payments the ticket, and **waits right there** until Payments finishes and hands back a confirmation. Only then does Orders move on.
- **Option 2 (Asynchronous):** The Orders cook drops the ticket into a **tray** at the Payments station and immediately goes back to the next customer — without waiting to see when Payments picks it up.

### What We'd Already Built Was Synchronous
Everything from Day 63 (the Gateway calling BookManagement over HTTP and waiting for `"OK from Vishvapari"`) is **synchronous communication**. The caller sends a request and sits there, blocked, until it gets a response back.

### The Problem With Always Being Synchronous
If BookManagement (or any service) is slow, crashed, or restarting — the caller just sits there waiting. Worse: if 50 other users are also making requests through the same path at the same time, **all of them get stuck too**, because the one call that's hung up is blocking everything behind it. One slow/dead service can cascade and freeze the whole system.

### The Alternative: Asynchronous Communication (Message Queues)
Instead of Orders calling Payments directly and waiting, Orders drops a **message** into a **queue** — a line of things waiting their turn, like people queued at a bank counter. Orders is free immediately. Payments (whenever it's ready — even if it was down a moment ago) picks messages off the queue and processes them one at a time, in order. Nothing is lost, because the queue holds the message safely until someone is ready to handle it.

### The Real Skill: Knowing Which One to Use
Not everything should be synchronous, and not everything should be asynchronous. The test is:

> **Does the user genuinely need to wait for this specific step before I can honestly tell them "success"?**

**Example — placing an order and sending a confirmation email:**
- Saving the order to the database → **synchronous.** The order isn't real until it's saved — this must complete before telling the user "success."
- Sending the confirmation email → **asynchronous.** The user doesn't need the email to have already landed in their inbox to know their order worked. This can happen quietly in the background, queued up and processed separately. If the email server is slow, the user's checkout experience isn't affected at all.

**Example — uploading a new book (BookManagement):**
- Saving the book's details + cover image → **synchronous.** The book isn't "added" until these exist.
- Generating a thumbnail from the cover image → **asynchronous.** The book is already fully usable without a thumbnail yet — it can be generated a few seconds later, invisibly.

This second example is exactly what we built today.

---

## Part 2 — What Is AWS SQS?

**SQS (Simple Queue Service)** is Amazon's fully-managed message queue — the "tray" from the restaurant analogy, but running as a cloud service instead of something you have to install and run yourself.

- **Standard Queue** (what we used): messages might rarely arrive slightly out of order or be delivered twice, but it's simple and fast. Fine for our use case — thumbnail order doesn't matter, and processing the same message twice by accident is harmless.
- **FIFO Queue**: strictly ordered, exactly-once delivery, but more setup overhead. Not needed here.

---

## Part 3 — What We Actually Built

### Goal
When a new book is added to BookManagement, send a message to an SQS queue — as a stand-in for "later, some other process will pick this up and generate a thumbnail."

### Step 1 — Created the SQS Queue
AWS Console → SQS → Create queue → **Standard type**, named `book-thumbnail-queue`.

This gave us a **Queue URL** — a unique web address for this specific queue:
```
https://sqs.eu-north-1.amazonaws.com/902685117068/book-thumbnail-queue
```

### Step 2 — Added the AWS SDK Dependency
In `BookManagement`'s `pom.xml`, added the SQS library:
```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>sqs</artifactId>
    <version>2.25.11</version>
</dependency>
```
**Why version `2.25.11` specifically?** It matches the version already used for `secretsmanager` elsewhere in the project. AWS SDK modules share internal libraries with each other — mixing versions can cause confusing runtime errors (missing methods, unexpected crashes) because the modules disagree on what those shared internal pieces look like. Keeping versions consistent avoids this whole category of bug.

### Step 3 — Wrote a Separate Class to Send Messages
Created `SqsPublisher.java` in the same `config` package as `SecretsManagerInitializer` (existing AWS-related code):

```java
package com.vishva007.BookManagement.config;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Service
public class SqsPublisher {

    private final SqsClient sqsClient = SqsClient.builder().build();
    private final String queueUrl = "https://sqs.eu-north-1.amazonaws.com/902685117068/book-thumbnail-queue";

    public void sendBookAddedMessage(String bookTitle) {
        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody("New book added: " + bookTitle)
                .build();

        sqsClient.sendMessage(request);
        System.out.println("SQS message sent for book: " + bookTitle);
    }
}
```

**Why a separate class instead of putting this code directly in `BookController`?** Same reasoning as `SecretsManagerInitializer` being separate from the main app class: `BookController`'s job is handling HTTP requests and talking to the database — not knowing the details of *how* to talk to AWS SQS. Keeping AWS-specific logic in its own small, focused class makes it easier to find, test, and change later without cluttering the controller.

**What does `@Service` do?** It tells Spring: "create one instance of this class automatically, and make it available anywhere I ask for it." This is the same mechanism that lets `BookRepository` be used inside `BookController` without ever writing `new BookRepository()` manually.

### Step 4 — Wired It Into `BookController`

Added the import:
```java
import com.vishva007.BookManagement.config.SqsPublisher;
```

Injected it the same way `BookRepository` is injected:
```java
@Autowired
private SqsPublisher sqsPublisher;
```

Updated the `create` method:
```java
@PostMapping
public Book create(@Valid @RequestBody Book book) {
    Book savedBook = bookRepository.save(book);
    sqsPublisher.sendBookAddedMessage(savedBook.getTitle());
    return savedBook;
}
```

**Why does the database save happen *before* the SQS message, and not the other way around?** If the SQS message were sent first and the database save failed afterward, the system would have sent a "new book added" notification for a book that doesn't actually exist. Saving first guarantees the notification only ever fires for books that were genuinely, successfully saved — the database is the source of truth, and everything else follows only once that's confirmed.

---

## Part 4 — Giving the Server Permission to Use SQS (IAM)

Just like `SecretsManagerInitializer` needs an IAM Role to talk to AWS Secrets Manager, `SqsPublisher` needs permission to talk to SQS. The existing role attached to `vishva-server-1` (`bookmanagement-ec2-secrets-role`) didn't have SQS permissions yet — we had to add them.

### What We Added
An **inline policy** (a small, specific permission rule) attached directly to the existing role:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": "sqs:SendMessage",
            "Resource": "arn:aws:sqs:eu-north-1:902685117068:book-thumbnail-queue"
        }
    ]
}
```
Named: `AllowSendToBookThumbnailQueue`

### Why Only `sqs:SendMessage`, and Only on This One Queue?
This is the **principle of least privilege** — a core cloud-security idea: grant only the exact permission needed to do the job, nothing more. Same reasoning as scoping the RDS security group to a specific source instead of `0.0.0.0/0` (Day 59). If this role's credentials were ever leaked or misused, the damage is contained: an attacker could only *send messages to this one queue* — nothing else. They couldn't delete queues, read other queues, or touch any other AWS service. If we'd granted broad access "just in case," a leak would be far more damaging.

---

## Part 5 — The Debugging Journey (What Went Wrong, and Why)

### Problem 1: Package/Import Error
`BookController.java` couldn't find `SqsPublisher` — `Cannot resolve symbol 'SqsPublisher'`.

**Cause:** `SqsPublisher` lives in a different Java package (`com.vishva007.BookManagement.config`) than `BookController` (`com.vishva007.BookManagement`). Java doesn't automatically see classes in other packages — it needs an explicit `import` line.

**Fix:** Added `import com.vishva007.BookManagement.config.SqsPublisher;` to the top of `BookController.java`.

### Problem 2: Accidentally Deployed a Broken Local Change
Earlier in the day (Day 62/63 debugging), `SecretsManagerInitializer`'s registration was temporarily commented out in `BookManagementApplication.java` to allow local testing without an AWS IAM role. That commented-out line was still in place when today's SQS code was committed and **pushed to GitHub**, which meant it would have gone live to production and broken it (Secrets Manager loading disabled = the app couldn't start correctly).

**Lesson:** local-only debugging changes need to be tracked carefully and reverted before committing. Caught this by re-checking the file before pushing, and pushed an immediate follow-up fix to re-enable it.

### Problem 3: RDS Confusion
Instinct was to flip RDS's `PubliclyAccessible` back to `Yes` to test in production — but this wasn't needed. `vishva-server-1` and the database are in the **same VPC**, so production traffic between them never depended on `PubliclyAccessible` at all (that setting only ever mattered for a laptop testing from outside the VPC, back on Day 62). Correctly left RDS set to `No` — no unnecessary exposure.

### Problem 4: The Server Wasn't Actually Running
Every request to `vishva-server-1` (even a simple health check) failed with "Could not connect to server." SSH'd into the server directly and ran:
```bash
sudo systemctl status bookmanagement
```
Result: `Active: inactive (dead)`. The app simply wasn't running — and it was also set to `disabled`, meaning it wouldn't even start automatically if the server rebooted.

**Fix:**
```bash
sudo systemctl start bookmanagement
```
This started it immediately, and the health check succeeded right after.

**Follow-up task for later:** run `sudo systemctl enable bookmanagement` so it survives a server reboot automatically in the future — not done yet, worth fixing in a future session.

### Problem 5: 403 Forbidden on POST /books
GET requests (like `/health`) worked fine, but POST requests to `/books` returned `403 Forbidden`, even after trying HTTP Basic Auth (`-u admin:admin123`).

**Cause:** Checked `SecurityConfig.java` and found the app uses **JWT token-based authentication** (`SessionCreationPolicy.STATELESS` + a custom `JwtFilter`), not HTTP Basic Auth. Sending `-u admin:admin123` sends a completely different kind of credential that this security setup doesn't check at all — it expects a `Bearer <token>` in the request instead.

**Fix:** Logged in first to get a real token:
```
POST /login  { "username": "admin", "password": "admin123" }
```
Then included that token on the actual request:
```
Authorization: Bearer <token>
```

### Problem 6: PowerShell JSON Quoting
Sending JSON directly in a PowerShell `curl.exe -d '{...}'` command kept breaking — PowerShell's own quote-escaping rules mangled the JSON, causing curl to misread spaces as separate arguments.

**Fix:** Wrote the JSON to a file first, then referenced the file instead of typing raw JSON inline:
```powershell
'{"title":"Test Book for SQS","description":"Testing async notification","price":199}' | Out-File -FilePath book.json -Encoding utf8
curl.exe -X POST http://56.228.53.82:8090/books -H "Content-Type: application/json" -H "Authorization: Bearer <token>" --data-binary "@book.json"
```

---

## Part 6 — Final Result: Confirmed Working

```
POST /books → {"id":5,"title":"Test Book for SQS","description":"Testing async notification","price":199,"coverImageUrl":null}
```

And in the AWS SQS Console, polling `book-thumbnail-queue`:
```
New book added: Test Book for SQS
```

Full flow proven, live, in production:
1. Book saved to the database (**synchronous** — completed first, guaranteed)
2. Confirmation returned to the caller immediately, without waiting for anything else
3. A message quietly landed in SQS afterward (**asynchronous** — happened in the background, never blocked the response)

---

## Quick-Reference Summary
- **Synchronous** = caller waits for a response (HTTP calls, like Gateway → BookManagement). Risk: one slow/dead service can block everything upstream.
- **Asynchronous** = caller drops a message and moves on immediately (message queues, like SQS). The message waits safely even if the receiver is temporarily down.
- **The real skill**: ask "does the user need to wait for this specific step?" — not everything needs to be one or the other.
- **IAM least privilege**: grant only the exact permission needed (`sqs:SendMessage` on one queue), never broad access "just in case."
- **JWT auth ≠ Basic Auth**: this app expects a Bearer token from `/login`, not a username/password on every request.
- **Always check the service is actually running** (`systemctl status`) before assuming a connection failure is a network problem.