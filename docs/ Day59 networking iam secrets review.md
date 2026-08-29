# Day 59 — Networking + IAM + Secrets Tie-Together Review
**Project:** BookManagement | **Date:** August 29, 2026

---

## 1. The Finding: RDS Publicly Exposed

**Instance:** `vishva-database-3`
**Discovery:** `PubliclyAccessible: true` flag was set.

### Key distinction learned
| Setting | What it actually controls |
|---|---|
| `PubliclyAccessible` | Whether AWS assigns a **public IP/DNS** to the instance |
| **Security Group** | The actual **gate** — decides who's allowed to connect, regardless of public/private IP |

`PubliclyAccessible: true` alone is *necessary but not sufficient* for exposure — the SG rule is what actually matters.

### What we found in `vishva-db-sg2` (RDS's security group)
Two inbound rules on port 3306:

| Port | Source | Meaning |
|---|---|---|
| 3306 | `106.192.168.112/32` | Home IP — fine, scoped to one machine |
| 3306 | `0.0.0.0/0` | **Entire internet** — genuine exposure |

**Conclusion:** Both the public flag AND the SG confirmed it — the database was genuinely reachable from anywhere on the internet.

---

## 2. Why This Matters: Attack Vectors Enabled by an Open Port

With port 3306 open to `0.0.0.0/0`, the only remaining protection is the DB password. Two attack types become possible *purely because the network door is open*:

1. **Brute-force attack** — bots automatically try thousands of username/password combos (`admin/admin`, `root/password123`, etc.) against the open port until one works.
2. **Exploiting a known vulnerability (CVE)** — MySQL's version is visible in the connection handshake before login. An old/unpatched version with a known CVE can sometimes be exploited to bypass login entirely — no password needed.

**If the SG only allowed a specific source** (like an app server's SG), neither attack could even begin — the connection attempt gets dropped at the network layer before any credential or version check happens.

---

## 3. IAM Piece: How `vishva-server-1` Authenticates to AWS

Checked EC2 → `vishva-server-1` → Security details:

- **IAM Role attached:** `bookmanagement-ec2-secrets-role`
- **Security Group attached:** `sg-00cb7efdbb2ce9d84` (`launch-wizard-1`)

### The identity chain
- The EC2 instance carries an **IAM Role** like a "badge" — it doesn't need hardcoded AWS access keys.
- The role's policy grants specific permissions (e.g., `GetSecretValue` on one specific secret).
- Secrets Manager checks that badge before releasing anything — no valid role, no secret.

This is how the `SecretsManagerInitializer` fetches the DB password at runtime instead of storing it in `.bashrc` or code.

---

## 4. Defense in Depth — The Core Lesson

No single layer is trusted to be perfect alone. Each layer assumes the others might fail:

| Layer | Job | What happens if it fails |
|---|---|---|
| **Network (SG)** | Controls *who can even reach* the port | If misconfigured, brute-force/CVE attacks become possible |
| **IAM** | Controls *who can authenticate* to AWS services | If too loose, blast radius of a leak grows |
| **Secrets Manager** | Keeps the DB password off disk, fetched at runtime | If bypassed, password could leak via hardcoding |

**This is exactly the gap found today** — IAM and Secrets Manager were working correctly, but the network layer had silently failed (`0.0.0.0/0`). Checking only one layer would have missed it.

---

## 5. The Fix (Applied Same Day)

### Fix 1 — Security Group (`vishva-db-sg2`)
- **Removed:** inbound rule with source `0.0.0.0/0` on port 3306
- **Added:** inbound rule on port 3306 with source = `sg-00cb7efdbb2ce9d84` (the app server's SG — not its IP, since IPs change on restart but SGs stay attached)
- **Kept:** `106.192.168.112/32` for direct home-IP admin access

### Fix 2 — RDS `PubliclyAccessible` flag
- Changed from **Yes** → **No**

**Why both fixes, not just one?**
Since `vishva-server-1` and `vishva-database-3` sit in the **same VPC**, the app never needed the public path at all — it connects via the private internal address. Leaving `PubliclyAccessible: true` was unnecessary attack surface even with a locked-down SG. Removing it means there's no public IP to attack in the first place, even if the SG is ever misconfigured again in the future.

---

## 6. Quick-Reference Summary

- ✅ Public flag ≠ automatically exposed — the SG is the real gate, but don't rely on that alone
- ✅ SG rules can reference another **SG ID** as source, not just IPs — stays valid even when instance IPs change
- ✅ IAM Roles let EC2 authenticate to AWS services without hardcoded keys
- ✅ Secrets Manager + IAM Role = password never touches disk
- ✅ Defense in depth = network + IAM + secrets each independently enforced, not relying on just one