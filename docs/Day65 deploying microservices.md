# Day 65 — Deploying Microservices Together on One Server
**Project:** eureka-server + BookManagement + api-gateway on vishva-server-1 | **Date:** September 02–03, 2026

*Written so someone with zero AWS/Linux background can follow it.*

---

## Part 1 — The Concept: Getting Ready to Deploy

### The Problem: `localhost` Doesn't Mean What It Looks Like
Up to now, `eureka-server`, `BookManagement`, and `api-gateway` all ran on one laptop, and their config files pointed at `localhost:8761` for Eureka. That worked because all three were genuinely on the same machine.

**Key idea:** `localhost` doesn't mean "some fixed address" — it always means **"this same machine, whoever is asking."** It's relative, not absolute. If `BookManagement` runs on Server A and Eureka runs on Server B, then `BookManagement` asking for `localhost:8761` means "look for Eureka on Server A" — which is wrong, because Eureka is actually on Server B.

### The Decision: One Server or Three?
Two real options for deploying three services:
- **Three separate EC2 instances** — one per service. Gives real physical isolation (if one crashes the machine, others are unaffected), but costs 3x and is more to manage.
- **One EC2 instance, three separate processes** — cheaper, simpler, and still gets the core microservices benefit as long as each service is its own process (own systemd service, own JAR). Redeploying one doesn't require touching or restarting the others.

**Chosen: one instance (`vishva-server-1`), three separate systemd services.** Right choice for a low-traffic learning project on AWS Free Tier — the physical-isolation benefit of separate servers mostly matters once there's real production traffic to justify the extra cost.

**Good news:** since all three services now live on the *same* machine, `localhost:8761` in `BookManagement`'s and `api-gateway`'s config actually still works correctly — no code changes needed. The rule is: `localhost` configs only need to change when services move to genuinely *different* machines, not just because you're moving from a laptop to a server.

---

## Part 2 — Manually Deploying eureka-server and api-gateway

BookManagement already has an automated GitHub Actions pipeline that builds and deploys it. `eureka-server` and `api-gateway` don't have that set up yet, so today's deploy was done manually — a good way to actually understand each step CI/CD normally automates.

### The Steps (done for both eureka-server and api-gateway)

**1. Build the JAR locally, on the laptop:**
```powershell
cd C:\Users\vishv\OneDrive\Desktop\eureka-server
./mvnw clean package -DskipTests
```
This produces a runnable `.jar` file inside a `target` folder.

**2. Create a folder for it on the server** (via SSH):
```bash
ssh -i "C:\Users\vishv\Downloads\vishva-key.pem" ubuntu@56.228.53.82
mkdir -p /home/ubuntu/eureka-server
exit
```

**3. Copy the JAR to the server** using `scp` (secure copy — like `ssh`, but for transferring files):
```powershell
scp -i "C:\Users\vishv\Downloads\vishva-key.pem" "C:\Users\vishv\OneDrive\Desktop\eureka-server\target\eureka-server-0.0.1-SNAPSHOT.jar" ubuntu@56.228.53.82:/home/ubuntu/eureka-server/
```

**4. Create a systemd service file** so Linux knows how to run and manage the app (start it, restart it if it crashes, start it automatically on boot):
```bash
sudo nano /etc/systemd/system/eureka-server.service
```
Content:
```ini
[Unit]
Description=Eureka Server for BookManagement Microservices
After=network.target

[Service]
User=ubuntu
ExecStart=/usr/bin/java -jar /home/ubuntu/eureka-server/eureka-server-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```
No database or JWT credentials needed here — Eureka never talks to a database, it's just a registry.

**5. Load, start, and enable it:**
```bash
sudo systemctl daemon-reload      # tells systemd "read the new service file"
sudo systemctl start eureka-server   # actually launches it
sudo systemctl enable eureka-server  # makes it auto-start if the server reboots
sudo systemctl status eureka-server  # confirms it's running
```

Repeated the same five steps for `api-gateway`, using port `8080` and its own JAR/folder.

**Verified:** both showed `Active: active (running)` in their status, and `eureka-server`'s dashboard (`curl http://localhost:8761`) correctly showed `api-gateway` registered.

---

## Part 3 — The Real Production Incident (What Actually Went Wrong)

Once all three services were "deployed," things started failing in ways that looked like application bugs but turned out to be **infrastructure exhaustion** — the server itself running out of resources. Here's the full chain, in the order it was discovered.

### Incident 1: The Disk Was Completely Full

**Symptom:** Simple commands started hanging or freezing (even `du`, a basic "check folder size" command).

**Diagnosis:**
```bash
df -h
```
Showed `/dev/root` at **100% used**, only `3.1M` free out of `6.8G` total.

**Why this causes commands to hang:** Linux needs a small amount of free disk space to do almost anything, even just reading files — when a disk is completely full, the operating system struggles to do basic housekeeping, making everything sluggish or unresponsive.

**Finding the cause:**
```bash
sudo du -sh /var/log/* 2>/dev/null | sort -rh | head -10
```
Found `/var/log/journal` (systemd's internal log history) taking up **288M** — built up over months of the server running, plus a burst of extra logging from today's crashes.

**The fix — trim old logs, don't delete blindly:**
```bash
sudo journalctl --vacuum-time=2d    # keep only the last 2 days of logs
sudo apt clean                       # clear cached package files (safe, regenerable)
sudo apt autoremove -y               # remove unused packages
```
Result: free space went from `3.1M` up to `550M` — enough for the system to work normally again.

### Incident 2: The Server Was Running Out of RAM (Real Root Cause of the "Random" 403/404 Errors)

**Symptom:** Throughout the day, requests to the services kept randomly failing with 403s and 404s that seemed to appear and disappear unpredictably — services that were "running" moments ago would suddenly not respond.

**Diagnosis:**
```bash
free -h
```
Showed **803-886Mi used out of only 909Mi total RAM**, with almost nothing free, and **zero swap space** (swap is disk space Linux can use as overflow "fake RAM" when real RAM runs low).

**The real proof — checking for OOM (Out Of Memory) kills:**
```bash
sudo dmesg | grep -i "killed process"
```
This showed **repeated entries**, spaced minutes apart, all saying `Out of memory: Killed process ### (java)` — meaning Linux's built-in "OOM killer" had been forcibly killing the Java processes (Eureka, BookManagement, or the Gateway) over and over, every few minutes, all day.

**Why this happened:** `vishva-server-1` is a `t3.micro` — AWS Free Tier's smallest instance, with only about 1GB of RAM total. Running **three separate JVMs simultaneously** (each needing 150-200MB minimum, just for the Java runtime itself, before even doing any real work) pushed right up against — and over — that ceiling. When Linux runs completely out of memory, rather than letting the whole system crash, it picks a process and kills it to free up RAM immediately. That killed process then automatically restarted (because of `Restart=always` in the systemd config) — which is exactly why services kept "coming back" but then failing again shortly after: they were stuck in a repeating kill-and-restart loop.

**This was the actual explanation for every "mysterious" routing failure that day** — not bugs in the code, not misconfiguration, but the server physically running out of memory and killing whichever service was in the way.

**The fix — two parts, done together:**

**Part A: Add swap space** (a safety net — extra "overflow" memory using disk space):
```bash
sudo fallocate -l 512M /swapfile   # create a 512MB file to use as swap
sudo chmod 600 /swapfile            # restrict permissions (security)
sudo mkswap /swapfile               # format it as swap
sudo swapon /swapfile               # turn it on
```

**Part B: Limit how much RAM each Java program is allowed to use**, so no single service can hog unlimited memory. Added `-Xmx<size>` (max heap size) to each service's `ExecStart` line in its systemd file:
```
# bookmanagement.service
ExecStart=/usr/bin/java -Xmx256m -jar /home/ubuntu/target/BookManagement-0.0.1-SNAPSHOT.jar --server.port=8090

# eureka-server.service
ExecStart=/usr/bin/java -Xmx200m -jar /home/ubuntu/eureka-server/eureka-server-0.0.1-SNAPSHOT.jar

# api-gateway.service
ExecStart=/usr/bin/java -Xmx200m -jar /home/ubuntu/api-gateway/api-gateway-0.0.1-SNAPSHOT.jar
```

**Why both fixes together, not just one:** swap alone would stop the outright kills, but without memory limits, one service could still slowly consume everything and cause constant slow swapping (very sluggish performance). Memory limits alone would help, but without swap as a safety net, any brief spike (like a burst during startup) could still trigger a kill. Together: predictable, capped memory per service, plus a cushion for temporary spikes.

**After the fix**, restarted all three:
```bash
sudo systemctl daemon-reload
sudo systemctl restart eureka-server
sudo systemctl restart bookmanagement
sudo systemctl restart api-gateway
```
All three stayed up simultaneously and stable — no more repeated kills.

### Incident 3: The Database Was Separately Stopped

**Symptom:** Even after fixing disk and memory, `BookManagement` still couldn't finish starting up, and the gateway kept returning 404 for `/bookmanagement/health`.

**Diagnosis:** Checked the RDS console directly — `vishva-database-3`'s status showed **"Stopped."** This wasn't something done deliberately during this session — it was either stopped manually at some earlier point and forgotten, or possibly auto-stopped by an AWS setting (worth checking later).

**Why this blocked everything:** `BookManagement` can't finish starting up without a working database connection (established back on Day 62 — the app's controllers are tightly coupled to a live DB connection). Without a finished startup, it never reaches the point where it registers itself with Eureka — so the gateway had nothing to route to, hence the 404.

**The fix:** AWS Console → RDS → `vishva-database-3` → **Actions → Start**. Took a few minutes to become "Available" again. Once it was, restarted `bookmanagement`:
```bash
sudo systemctl restart bookmanagement
```

### Incident 4 (Bonus Find): Jenkins Was Fighting for Port 8080

**Symptom:** At one point, hitting port 8080 returned a **Jenkins** loading page instead of the gateway's response.

**Diagnosis:** `api-gateway` is configured for port 8080 — and it turns out **Jenkins** (installed back on Days 37-44, no longer actually used since GitHub Actions now handles CI/CD) is also configured for port 8080 by default. Checked:
```bash
sudo systemctl status jenkins
```
Found Jenkins was **crash-looping** — trying to start, failing (likely a casualty of the same memory crisis), giving up, and repeating — which is why it sometimes briefly grabbed port 8080 before crashing again, letting `api-gateway` take over afterward.

**The fix — since Jenkins isn't needed anymore, disable it permanently:**
```bash
sudo systemctl stop jenkins
sudo systemctl disable jenkins
```
This stops it from ever trying to start again, freeing up whatever resources it was wasting on a memory-constrained server, and permanently resolves any future port conflict with `api-gateway`.

---

## Part 4 — Final Confirmed Result

After working through all four incidents in sequence:

```bash
curl http://localhost:8080/bookmanagement/health
→ OK from ip-172-31-40-111
```

All three services — `eureka-server`, `BookManagement`, `api-gateway` — running **simultaneously and stably** on one EC2 instance, with the gateway correctly discovering and routing to BookManagement through Eureka, all in real production, not a local test.

---

## Part 5 — Why This Day Actually Mattered

None of today's problems were "textbook tutorial" issues — they were a realistic, compounding production incident: a slowly-filling disk (from months of unmanaged logs), a memory-starved server pushed over the edge by adding new services, and a separately-stopped database, all surfacing around the same time and initially looking like unrelated application bugs. Diagnosing this required checking each layer in turn — disk, memory, database, port ownership — rather than assuming the first plausible explanation (app code bugs) was correct. This is genuinely valuable, realistic experience: a lot of real-world "the app is broken" incidents turn out to be infrastructure exhaustion, not code.

---

## Quick-Reference Summary
- **`localhost` = "myself," always relative** — only works if caller and callee share a machine; re-check every `localhost` config when moving services around
- **One EC2 instance can host multiple independent microservices** as separate systemd services — you keep the independent-deploy benefit as long as each is its own process, without paying for separate servers
- **`df -h`** = check disk space; **`free -h`** = check RAM/swap; **`sudo dmesg | grep -i "killed process"`** = check whether the OOM killer has been terminating processes
- **`/var/log/journal` can silently grow huge over time** — `sudo journalctl --vacuum-time=<N>d` trims it safely
- **`t3.micro` (Free Tier) has very limited RAM (~909Mi)** — running multiple JVMs needs swap space + `-Xmx` memory caps on each one to stay stable
- **"Random" errors are often resource exhaustion, not application bugs** — always check disk/memory/whether the dependent service is even running before assuming the code is wrong
- **Unused services (like an old Jenkins install) should be disabled**, not just left dead — they still compete for resources and can cause confusing port conflicts even while failing