# Day 52 — VPC Deep Dive (Beginner-Friendly Notes)

## Goal of the Day
Understand how AWS networking (VPC) works — and *prove it hands-on* by building a mini network, launching two servers, and testing which one can/can't reach the internet.

No prior AWS networking knowledge needed to follow this. Everything is explained with a simple analogy first.

---

## 1. The Core Analogy: Apartment Building

Imagine a big apartment building.

| Real World | AWS Term |
|---|---|
| The building itself | **VPC** (Virtual Private Cloud) — your own isolated network in AWS |
| Individual flats inside the building | **Subnets** — smaller sections inside the VPC |
| Ground floor flats (easy street access) | **Public Subnet** |
| Upper floor flats (need to go through security) | **Private Subnet** |
| Main gate connecting building to the city road | **Internet Gateway (IGW)** |
| A one-way service exit (residents can go out, strangers can't come in) | **NAT Gateway** |
| A signboard in each flat saying "to reach the city, go this way" | **Route Table** |
| A lock on each flat's own door | **Security Group** (protects one server) |
| A security guard at the building's main gate | **NACL** (protects a whole subnet) |

---

## 2. What is a VPC?

A **VPC** is your own **private, isolated network** inside AWS. Nobody else's resources can see inside it unless you allow it.

- When you create a VPC, you give it an IP address range (called a **CIDR block**), e.g. `10.0.0.0/16`
- This is like saying "our building can have up to 65,536 addresses (flats) inside it"

---

## 3. What is a Subnet?

A VPC is too big to use directly — you divide it into smaller chunks called **subnets**. Each subnet gets its own smaller IP range, e.g. `10.0.1.0/24` (256 addresses).

**Two types:**
- **Public Subnet** — has a path to the internet (used for things users need to access directly, like a web server)
- **Private Subnet** — has NO direct path to the internet (used for sensitive things like a database)

**Important rule:** A subnet lives inside only **one Availability Zone (AZ)** — think of an AZ as a separate physical building/data center. If that AZ goes down, that subnet goes down with it. That's why real production setups spread subnets across multiple AZs.

---

## 4. Internet Gateway (IGW)

The IGW is like the **main road connecting your building to the city**. It attaches to the whole VPC, not to one subnet.

But here's the catch: **just because the gateway exists doesn't mean every subnet can use it.** Each subnet needs its own instructions (route table) pointing to it — like a signboard saying "go through the main gate to reach the city."

---

## 5. Route Table

A Route Table is the "signboard" that tells traffic where to go.

- **Public Subnet's Route Table:** `0.0.0.0/0 → Internet Gateway` (all traffic → straight to internet)
- **Private Subnet:** stays on the default route table, which has **no internet route** → so it's cut off from the internet completely (unless you add a NAT Gateway)

---

## 6. NAT Gateway (Optional / Skipped Today to Save Cost)

If a private server (like a database) needs to download updates but should never be reachable from the internet, you use a **NAT Gateway**.

- It's a **one-way door**: private subnet → internet (allowed), internet → private subnet (blocked)
- **Note:** NAT Gateway has an hourly + data cost (not free-tier). We intentionally skipped it today and instead just proved the private subnet has **zero internet access at all** — which teaches the same core concept for free.

---

## 7. Security Group vs NACL

Both are "firewalls" but at different levels:

| | Security Group | NACL |
|---|---|---|
| Analogy | Lock on your own flat door | Guard at building's main gate |
| Applies to | One EC2/RDS instance | Entire subnet |
| Rule type | Allow only | Allow + Deny |
| Behavior | **Stateful** — if you let someone in, their way out is automatically allowed, no extra rule needed | **Stateless** — every direction (in AND out) needs its own explicit rule |

---

## 8. Bastion Host Pattern (Real-World Technique)

Since a private server has no public IP, you can't SSH into it directly from your laptop. Instead:

```
Your Laptop → SSH → Public EC2 (has public IP) → SSH → Private EC2 (no public IP)
```

The public EC2 acts as a **middleman ("bastion" or "jump box")**. This is exactly how real companies protect their databases — nobody connects to the database directly from the internet, ever.

**Security tip:** Never copy your private key onto the public server. Use **SSH Agent Forwarding** instead — your key stays on your laptop, and authentication requests get forwarded through securely.

---

## 9. Hands-On Steps We Executed

### Step 1: Create VPC
- VPC Console → Create VPC
- Name: `vishva-vpc-day52`, CIDR: `10.0.0.0/16`

### Step 2: Create Subnets
- Public: `vishva-public-subnet`, CIDR `10.0.1.0/24`
- Private: `vishva-private-subnet`, CIDR `10.0.2.0/24`

### Step 3: Create & Attach Internet Gateway
- Name: `vishva-igw-day52`
- Attach it to `vishva-vpc-day52`

### Step 4: Route Table (Public Only)
- Create `vishva-public-rt`
- Add route: `0.0.0.0/0 → vishva-igw-day52`
- Associate with `vishva-public-subnet`
- (Private subnet left on default route table — no internet route)

### Step 5: Launch 2 EC2 Instances (Ubuntu, t3.micro)

**Public EC2:**
- Subnet: `vishva-public-subnet`
- Auto-assign public IP: **Enable**
- SG (`vishva-public-sg`): allow SSH (22) from **My IP** only

**Private EC2:**
- Subnet: `vishva-private-subnet`
- Auto-assign public IP: **Disable**
- SG (`vishva-private-sg`): allow SSH (22) from **10.0.0.0/16** (inside VPC only)

### Step 6: Test Everything

**A) SSH into public EC2 from laptop:**
```bash
ssh -i ~/.ssh/VPC-KEY.pem ubuntu@<public-ec2-ip>
```
✅ Worked.

**B) Test internet from public EC2:**
```bash
ping -c 3 google.com
```
✅ Success — 0% packet loss (proves IGW + route table works)

**C) Set up SSH Agent Forwarding (so key never leaves laptop):**
```bash
eval "$(ssh-agent -s)"
ssh-add ~/.ssh/vpc-key2.pem
ssh -A -i ~/.ssh/VPC-KEY.pem ubuntu@<public-ec2-ip>
```

**D) From inside public EC2, hop to private EC2:**
```bash
ssh ubuntu@<private-ec2-private-ip>
```
✅ Worked (after fixing a Security Group typo — `0.0.0.0/16` should have been `10.0.0.0/16`)

**E) Test internet from private EC2:**
```bash
ping -c 3 google.com
```
❌ 100% packet loss — **exactly as expected.** Proves private subnet has zero internet access.

**F) Test direct SSH from laptop to private EC2 (bypassing bastion):**
```bash
ssh -i ~/.ssh/vpc-key2.pem ubuntu@<private-ec2-private-ip>
```
❌ Hangs/times out — proves the private EC2 can ONLY be reached through the bastion (public EC2), never directly.

---

## 10. Final Proof Table

| Test | Result | Why |
|---|---|---|
| Public EC2 → Internet | ✅ Success | Public subnet → Route Table → IGW |
| Laptop → Public EC2 (SSH) | ✅ Success | Public IP + SG allows your IP |
| Public EC2 → Private EC2 (SSH) | ✅ Success | Private SG allows traffic from within VPC |
| Private EC2 → Internet | ❌ Failed | No IGW/NAT route in private subnet |
| Laptop → Private EC2 (direct SSH) | ❌ Failed | Private EC2 has no public IP, unreachable from internet |

---

## 11. Common Mistake We Hit (Real Debugging Practice)

We set the private EC2's Security Group source as `0.0.0.0/16` instead of `10.0.0.0/16` — a single-character typo. Result: SSH connection **timed out** (not "permission denied" — a timeout usually means network/firewall blocking, not an auth issue).

**Lesson:** When SSH times out, always check:
1. Security Group source/port rules
2. Route table entries
3. Whether the target even has the right network path

---

## 12. Cleanup (To Avoid Any Lingering Cost/Clutter)

Delete in this order:
1. Terminate both EC2 instances
2. Delete Security Groups (`vishva-public-sg`, `vishva-private-sg`)
3. Detach & delete Internet Gateway (`vishva-igw-day52`)
4. Delete Route Table (`vishva-public-rt`)
5. Delete both subnets
6. Delete the VPC (`vishva-vpc-day52`)

---

## Key Takeaway (One-Line Summary)

**A VPC is your private network; subnets split it into public (internet-facing) and private (isolated) zones; route tables + gateways control who can reach the internet; and the bastion host pattern lets you safely manage private servers without ever exposing them directly — this is exactly how real companies protect their databases in production.**