# Day 53 — AWS Secrets Manager (Beginner-Friendly Notes)

## Goal of the Day
Replace the old `.bashrc` / `kubectl create secret` habit of storing passwords with the real AWS-native way: **AWS Secrets Manager** + **IAM least-privilege roles**. By the end, the Spring Boot app fetches all its secrets (DB password, DB admin password, JWT secret) at runtime — nothing sensitive sits in plain text on the server.

---

## 1. The Core Analogy: Bank Locker vs Sticky Note

| Old way (`.bashrc`) | New way (Secrets Manager) |
|---|---|
| Password written on a sticky note stuck to your door | Password locked in a bank locker |
| Anyone with file access reads it instantly (`cat ~/.bashrc`) | Only authorized identities (IAM roles) can open it |
| No record of who accessed it | Every access can be logged/audited |
| Rotating the password = manual edit + app restart | Can be automated, app fetches the latest value each start |

---

## 2. Why `.bashrc` Was a Problem

1. **No security** — anyone with server/file access sees plain text passwords instantly.
2. **No audit trail** — no record of who read the secret or when.
3. **Manual rotation** — changing a password means SSH in, edit file, restart the app. Painful and error-prone across many services.

---

## 3. How Secrets Manager Solves It

- Your app **never stores the password**.
- Instead, at startup, the app **asks AWS**: *"Give me the current DB password"* — authenticated via **IAM identity**, not a hardcoded key.
- AWS checks: *does this EC2's attached IAM Role have permission to read this specific secret?* If yes, it returns the value.

**Key mechanism: IAM Role attached to EC2**
- You attach an IAM Role to the EC2 instance (not an IAM user, not access keys).
- The role has a policy allowing `secretsmanager:GetSecretValue` for one specific secret.
- No credentials are ever written in code — AWS handles authentication behind the scenes via the instance's role.

---

## 4. Least Privilege — Why Not Just Use `SecretsManagerReadWrite`?

The AWS-managed policy `SecretsManagerReadWrite` is dangerous here because:
- **"Write"** means the role could create/modify/delete secrets too — not just read.
- It has **no resource restriction** — it can access *every* secret in the account, not just the one you need.

**Instead, we wrote a custom least-privilege policy:**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "secretsmanager:GetSecretValue",
      "Resource": "arn:aws:secretsmanager:eu-north-1:ACCOUNT_ID:secret:bookmanagement/prod/db-password-*"
    }
  ]
}
```
This role can do exactly one thing: read exactly one secret. Nothing else.

*(Note: the `-*` suffix is required because AWS appends a random 6-character suffix to every secret's ARN.)*

---

## 5. Naming Convention

Secrets should be named descriptively, like folders:
```
bookmanagement/prod/db-password
```
This tells you at a glance: which app, which environment, what kind of secret. Much better than `mysecret123` once you have dozens of secrets across environments.

---

## 6. Hands-On Steps We Executed

### Step 1: Create the Secret
- Secrets Manager → Store a new secret → **Credentials for Amazon RDS database** (structured type, links directly to the RDS instance)
- Name: `bookmanagement/prod/db-password`
- Fields ended up including: `username`, `password`, `engine`, `host`, `port`, `dbname`, `dbInstanceIdentifier`, and later added `DB_ADMIN_PASSWORD` and `JWT_SECRET`
- Skipped automatic rotation (adds Lambda complexity — a good topic for a future day)

### Step 2: Create Least-Privilege IAM Policy
- IAM → Policies → Create policy → JSON tab → pasted the policy above
- Name: `bookmanagement-secrets-read-policy`

### Step 3: Create IAM Role for EC2
- IAM → Roles → Create role → Trusted entity: AWS service → Use case: EC2
- Attached `bookmanagement-secrets-read-policy`
- Name: `bookmanagement-ec2-secrets-role`

**Important note:** An EC2 instance can only have **one IAM role** attached at a time. Attaching a new role **replaces** the old one — it doesn't add to it. In production, you'd instead attach multiple policies to a single role rather than creating multiple roles.

### Step 4: Attach Role to EC2
- EC2 → select `vishva-server-1` → Actions → Security → Modify IAM role → selected `bookmanagement-ec2-secrets-role`

### Step 5: Update Spring Boot Code

**Why plain `${DB_PASSWORD}` in `application.properties` isn't enough:**
Spring resolves `application.properties` placeholders very early — before any custom Java logic runs. But fetching from Secrets Manager requires an actual API call. So we need code that runs **before** Spring reads its properties.

**Solution: `ApplicationContextInitializer`** — a special Spring hook that runs before the app context (and its property resolution) is built.

**`SecretsManagerInitializer.java`** (created inside a `config` package):
```java
package com.vishva007.BookManagement.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public class SecretsManagerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        try {
            SecretsManagerClient client = SecretsManagerClient.builder()
                    .region(Region.EU_NORTH_1)
                    .build();

            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId("bookmanagement/prod/db-password")
                    .build();

            GetSecretValueResponse response = client.getSecretValue(request);
            String secretJson = response.secretString();

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> secretMap = mapper.readValue(secretJson, Map.class);

            System.setProperty("DB_PASSWORD", String.valueOf(secretMap.get("password")));
            System.setProperty("DB_USERNAME", String.valueOf(secretMap.get("username")));
            System.setProperty("DB_ADMIN_PASSWORD", String.valueOf(secretMap.get("DB_ADMIN_PASSWORD")));
            System.setProperty("JWT_SECRET", String.valueOf(secretMap.get("JWT_SECRET")));

            String host = String.valueOf(secretMap.get("host"));
            String port = String.valueOf(secretMap.get("port"));
            String dbname = String.valueOf(secretMap.get("dbname"));
            System.setProperty("DB_URL", "jdbc:mysql://" + host + ":" + port + "/" + dbname);

            System.out.println("Secrets Manager: DB credentials loaded successfully.");
        } catch (Exception e) {
            System.err.println("Failed to load secrets from Secrets Manager: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
```

**Register it in the main class:**
```java
public static void main(String[] args) {
    SpringApplication app = new SpringApplication(BookManagementApplication.class);
    app.addInitializers(new com.vishva007.BookManagement.config.SecretsManagerInitializer());
    app.run(args);
}
```

**`application.properties` stays clean** — still just references placeholders:
```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.security.user.password=${DB_ADMIN_PASSWORD}
JWT_SECRET=${JWT_SECRET}
```

### Step 6: Fixed a Hardcoded Password in `SecurityConfig.java`
Found that `SecurityConfig.java` was hardcoding the admin password directly in Java instead of using `DB_ADMIN_PASSWORD` — inconsistent with the migration. Fixed with `@Value` injection:
```java
@Value("${DB_ADMIN_PASSWORD}")
private String adminPassword;
...
UserDetails admin = User.withUsername("admin")
        .password(passwordEncoder.encode(adminPassword))
        .roles("ADMIN")
        .build();
```

---

## 7. Real Debugging Journey (What Actually Broke, One by One)

| Version | Error | Root Cause | Fix |
|---|---|---|---|
| v3 | `DB_ADMIN_PASSWORD` placeholder unresolved | Value was in `.bashrc`, Docker container can't see host's `.bashrc` | Added to Secrets Manager |
| v4 | `JWT_SECRET` circular placeholder | Same — was in `.bashrc` only | Added to Secrets Manager |
| v5 | Disk full (`no space left on device`) | Old Docker image layers piling up on small t3.micro disk | `docker system prune -a -f` |
| v6 | `DB_URL` unresolved | Also only in `.bashrc` | Built dynamically from `host`/`port`/`dbname` fields already in the secret |
| v7 | `ClassCastException: Integer cannot be cast to String` | JSON `"port": 3306` parsed as Integer, but Map was typed `<String, String>` | Changed Map to `<String, Object>`, used `String.valueOf()` everywhere |
| v7 (again) | `Access denied for user 'vishva-database-3'` | Secret's `username` field was wrongly set to the RDS *instance identifier*, not the actual master username | Corrected to real master username (`admin`) from RDS console |
| v8 | Login returned "Bad credentials" with `xxx` | `SecurityConfig.java` had a **hardcoded** password (`xxx`), completely ignoring `DB_ADMIN_PASSWORD` | Injected `@Value("${DB_ADMIN_PASSWORD}")` properly |

**Lesson:** Migrations like this often reveal *multiple* places where a value was hardcoded or only lived in one location (`.bashrc`). Expect several rounds of "missing variable" errors — each one is progress, not failure.

---

## 8. Other Real-World Lessons Along the Way

- **systemd services can silently hold a port.** A leftover `bookmanagement.service` (from earlier CI/CD days) was auto-restarting the app natively and blocking Docker from binding port 8090. Had to `systemctl stop` + `systemctl disable` it.
- **Disk space matters on small instances.** t3.micro's small disk filled up fast with old Docker layers — `docker system prune -a -f` is a routine maintenance command.
- **A 403 isn't always a bug.** Getting `403 Forbidden` from a protected endpoint without a token is *correct* behavior — it proves Spring Security is working, not broken.

---

## 9. Final Verification

```bash
curl -X POST http://localhost:8090/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"xxx"}'
```

✅ Returned a valid JWT token — proving the full chain works:
**Secrets Manager → IAM Role → App fetches secrets → RDS connects → Spring Security validates → JWT generated using Secrets-Manager-sourced `JWT_SECRET`**

---

## 10. Cost Note

- IAM roles and policies: **always free**, no cost ever.
- Secrets Manager: **$0.40 per secret per month** (prorated daily) + **$0.05 per 10,000 API calls**. For one secret used for learning/testing, this is a fraction of a cent per day — negligible, but worth remembering to delete the secret later if this project is purely for learning and won't be kept long-term.

---

## Key Takeaway (One-Line Summary)

**Instead of storing passwords in plain text on a server, the app now authenticates as itself (via an IAM role) and asks AWS for the current secret at runtime — no credentials in code, no credentials in `.bashrc`, full audit trail, and a foundation for automated rotation later. This is the real-world, production-grade way companies manage secrets.**