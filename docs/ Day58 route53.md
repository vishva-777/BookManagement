# Day 58 — Route 53 Basics (Beginner-Friendly Notes)

## Goal of the Day
Understand what DNS/Route 53 actually does and why it matters — practiced hands-on with hosted zones and DNS records, without spending money on a real domain purchase (kept light, closing out the Day 25 gap).

---

## 1. The Core Concept: DNS as a Phonebook

Nobody types `142.250.183.14` to reach Google — they type `google.com`, and *something* translates that name into the real server IP behind the scenes.

**That translator is DNS (Domain Name System), and Route 53 is AWS's DNS service** — it maps human-friendly names (`bookmanagement.com`) to real server IPs (like an EC2's `56.228.53.82`).

---

## 2. Why Bother With a Domain Name at All?

Two real reasons, both directly relevant to this project:

**1. Memorability** — same reason `vishva-server-1` is easier to work with than `i-0e34556f0873fcd99`. Domain names are that same idea, but for the whole internet.

**2. Indirection / flexibility** — this ties directly into Day 56's Elastic IP saga. If a domain name points at a server, and that server's IP ever changes, you only need to update **one DNS record** in Route 53. Every user/service using the domain name automatically reaches the right server — no need to notify anyone individually or update configs scattered everywhere.

---

## 3. Hosted Zones

A **Hosted Zone** is like a folder that holds all the DNS records for one domain.

```bash
aws route53 create-hosted-zone --name <domain> --caller-reference day58-$(date +%s)
```

**Why `--caller-reference` is required:** it prevents accidental duplicate creation. If a request is retried (e.g., due to a network hiccup), AWS can recognize "I've already processed this exact reference" and avoid creating a second, duplicate hosted zone. Analogy: an order confirmation number preventing a double-charge if a checkout page is resubmitted. Using `$(date +%s)` (current Unix timestamp) guarantees a fresh, unique value every run.

**Result of creating a hosted zone:**
```json
"ResourceRecordSetCount": 2
```
Even before adding any custom records, every hosted zone automatically gets:

1. **NS record (Name Server record)** — the list of AWS nameservers (e.g., `ns-1093.awsdns-08.org`) responsible for this zone. Tells the rest of the internet "ask THESE servers about this domain."
2. **SOA record (Start of Authority)** — administrative metadata about the zone (who's responsible, refresh timing) — mostly bookkeeping, rarely touched directly.

Every hosted zone needs to at minimum declare "I exist, here's who to ask" — without the NS record, nobody could even locate the zone.

---

## 4. A Records — The Actual Name → IP Mapping

**An A record is the literal phonebook entry:** *"Whenever anyone looks up `app.<domain>`, tell them the IP is `56.228.53.82`."*

Created via CLI:
```bash
aws route53 change-resource-record-sets \
  --hosted-zone-id <ZONE_ID> \
  --change-batch '{
    "Changes": [
      {
        "Action": "CREATE",
        "ResourceRecordSet": {
          "Name": "app.<domain>",
          "Type": "A",
          "TTL": 300,
          "ResourceRecords": [{"Value": "56.228.53.82"}]
        }
      }
    ]
  }'
```

**Same thing via AWS Console** (equally valid — CLI and console are just two doors to the same backend):
1. Route 53 Console → Hosted zones → click the zone
2. Create record → Record name: `app` (subdomain part only, AWS appends the rest) → Record type: `A` → Value: IP → TTL: 300 → Create records

---

## 5. TTL (Time To Live)

**TTL controls how long other computers/browsers are allowed to CACHE (remember) a DNS answer** before being required to ask again. `TTL: 300` = 5 minutes.

**Why this matters practically:** if a record is updated (e.g., after another Elastic IP change), anyone who already cached the old answer won't see the update until their TTL expires. This is why companies often **lower the TTL in advance** of a planned migration — so caches expire quickly right before the actual cutover, minimizing the "stale answer" window.

---

## 6. The Key Insight: A Record Existing ≠ Reachable on the Internet

Even with a correctly created A record pointing `app.vishva-bookmanagement-practice.com` → `56.228.53.82`, visiting that URL in a browser would **fail**.

**Why:** the domain `vishva-bookmanagement-practice.com` was never actually **registered** with a real domain registrar (GoDaddy, Namecheap, Route 53 Domains, etc.). Since it isn't legally owned, the **global DNS system has no reason to know** that AWS's 4 nameservers are the authority for it.

**Analogy:** writing a new number into your own personal contacts under "Pizza Hut" — but Pizza Hut never gave you that number, and it isn't listed anywhere official. Your own phone happily shows the contact, but a stranger asking "what's Pizza Hut's number" has no way to find what you privately wrote down, because it was never made official outside your own contacts app.

Only tools with direct AWS access (like `aws route53 list-resource-record-sets`) can see this record — the wider internet cannot, because domain registration (pointing the *official* registrar's nameservers to AWS) never happened. This is exactly why practicing this way was free — no need to spend ~$12+/year on a domain just to learn the mechanics.

---

## 7. Cleanup Gotcha (Same Pattern as Day 57's S3 Bucket)

```bash
aws route53 delete-hosted-zone --id <ZONE_ID>
```
Failed on first attempt:
```
HostedZoneNotEmpty: The specified hosted zone contains non-required resource record sets and so cannot be deleted.
```

**Same lesson as the versioned S3 bucket from Day 57** — a resource can't be deleted while custom content still exists inside it. The mandatory NS/SOA records are fine to leave (they're required by AWS), but any custom records (like our `app` A record) must be explicitly deleted first:

```bash
aws route53 change-resource-record-sets \
  --hosted-zone-id <ZONE_ID> \
  --change-batch '{
    "Changes": [
      {
        "Action": "DELETE",
        "ResourceRecordSet": {
          "Name": "app.<domain>",
          "Type": "A",
          "TTL": 300,
          "ResourceRecords": [{"Value": "56.228.53.82"}]
        }
      }
    ]
  }'
```
Only after this succeeded did `delete-hosted-zone` work cleanly.

---

## 8. Full Command Sequence Used Today

```bash
# Create hosted zone
aws route53 create-hosted-zone --name vishva-bookmanagement-practice.com --caller-reference day58-$(date +%s)

# Add an A record pointing to the EC2's Elastic IP
aws route53 change-resource-record-sets --hosted-zone-id <ID> --change-batch '{...CREATE...}'

# Verify records exist
aws route53 list-resource-record-sets --hosted-zone-id <ID> --output json --no-cli-pager

# Cleanup: delete the custom A record first
aws route53 change-resource-record-sets --hosted-zone-id <ID> --change-batch '{...DELETE...}'

# Then delete the hosted zone itself
aws route53 delete-hosted-zone --id <ID>
```

---

## Key Takeaway (One-Line Summary)

**Route 53 hosted zones and DNS records can be created and verified entirely for free without owning a real domain — the mechanics (NS/SOA records, A records, TTL, one-point-of-update flexibility) are exactly the same whether or not the domain is publicly registered; only actual domain registration (pointing a registrar's nameservers to AWS) makes a record reachable by the wider internet.**