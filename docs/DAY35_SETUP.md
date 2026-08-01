# Day 35 — Docker on EC2

## 1. Goal

Bring the Docker workflow full circle: install Docker on the actual EC2 production server, pull the `vishvapari/bookmanagement:v2` image built and pushed to Docker Hub on Day 34, and run it on EC2 — proving the image is genuinely portable from local machine to production without any rebuilding.

**Analogy:** Baked a cake at home (WSL2), uploaded the recipe to a shared recipe book (Docker Hub). A completely different kitchen (EC2) can now open that same recipe book and bake the exact same cake, without needing the original baker present.

**Real-world relevance:** This is how production deployment actually works — build/test once (locally or in CI), push to a registry, then servers just pull and run. No more manually copying JAR files via SCP or rebuilding on the server itself.

---

## 2. Installing Docker on EC2

Same installation steps as WSL2 (Day 31), since EC2 also runs Ubuntu:

```bash
sudo apt update
sudo apt install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
```

**Then reconnect** (group membership doesn't apply to an already-open session):
```bash
exit
ssh -i ~/.ssh/vishva-key.pem ubuntu@<ec2-public-ip>
```

**Verify:**
```bash
docker --version
docker ps
```

---

## 3. Issue Faced: Package Manager Lock

**Error:** `Waiting for cache lock: Could not get lock /var/lib/dpkg/lock-frontend. It is held by process 2138 (unattended-upgr)`

**Cause:** Ubuntu's automatic background security-update process (`unattended-upgrades`) was mid-task and holding the package manager lock — not an actual error, just the install command patiently waiting its turn.

**Decision point:** Considered whether to force-kill the process to speed things up, versus waiting it out.

**Correct choice: waited it out.** Force-killing a package manager process mid-operation risks corrupting the package database, potentially breaking `apt` entirely — a far worse outcome than a few extra minutes of waiting. The lock cleared naturally and installation completed successfully.

**Side note:** Install also flagged a pending kernel upgrade (reboot required to apply) — noted for a future planned maintenance window, not acted on immediately.

---

## 4. Pulling the Image from Docker Hub

```bash
docker pull vishvapari/bookmanagement:v2
```

**Result:** All 8 layers pulled successfully, confirmed by matching digest (`sha256:5bbeae2a9bf3...`) — the exact same image built and tested on WSL2, now present on EC2 without any rebuild.

---

## 5. Port Conflict Consideration

**Problem:** EC2 already runs BookManagement via **systemd** (`bookmanagement.service`) bound to port 8090. Only one process can bind to a given port on a machine at a time — running a second process (the new Docker container) also mapped to 8090 would fail.

**Options considered:**
1. Stop systemd first, then run Docker on 8090 (full replacement)
2. Run Docker on a different host port (e.g., 8091), so both run side-by-side

**Decision:** Chose option 2 — safer for a learning/verification exercise, since it doesn't touch the currently-working production systemd deployment. Full migration can happen later, deliberately, once Docker is proven reliable.

---

## 6. Running the Container on EC2

```bash
docker run -d -p 8091:8090 \
  -e DB_URL=jdbc:mysql://vishva-database-3.clkm44eaeacb.eu-north-1.rds.amazonaws.com:3306/Bookdb \
  -e DB_PASSWORD=vishva007 \
  -e DB_ADMIN_PASSWORD=admin123 \
  -e JWT_SECRET=xEq5iTkBpwhG/BAR2HYDWhHQUzAxacQKRItiQdc5x4k= \
  --name bookmanagement-docker \
  vishvapari/bookmanagement:v2
```

**Port mapping explained (`-p 8091:8090`):**
- **Container-side (8090)** — fixed, matches `server.port=8090` baked into the JAR via `application.properties`. Docker cannot change what port the Java app listens on internally.
- **Host-side (8091)** — our choice, deliberately different from 8090 to avoid conflicting with the existing systemd service already using that port on the EC2 host.

**Security Group consideration:** Testing was done **internally** (via `curl http://localhost:8091/health` from inside EC2 itself) rather than opening port 8091 in the Security Group — avoided an unnecessary security change for what was a temporary verification test.

---

## 7. Verification

```bash
docker ps
curl http://localhost:8091/health
```

**Result:**

Confirmed: BookManagement running via Docker on EC2, connected to the same live RDS database, alongside the existing systemd deployment on a different port — both fully functional simultaneously.

---OK from fc75b91b9feb

## 8. Current EC2 State (Two Deployments Side-by-Side)

| Deployment | Port | Method |
|---|---|---|
| Original | 8090 | systemd + `java -jar` directly |
| New | 8091 | Docker container, pulled from Docker Hub |

---

## 9. Deployment Workflow Comparison

**Systemd approach (updating code — Days 21-25 pattern):**
1. Rebuild JAR locally
2. SCP the new JAR to EC2
3. Restart the systemd service

**Docker approach (updating code):**
1. `docker pull vishvapari/bookmanagement:v3` (or whatever new tag)
2. Stop and remove the old container
3. `docker run` a new container from the new image

**Key difference:** The Docker approach never touches the EC2 filesystem directly and never rebuilds anything on the server — the image already contains everything (Java, JAR, dependencies), built and tested once. This is the foundation of real CI/CD pipelines: build once, deploy the identical artifact everywhere.

---

## 10. Key Takeaway

BookManagement now runs on EC2 via two completely different mechanisms simultaneously — proving that the Docker image built and tested locally on WSL2 is genuinely production-ready without modification. Future updates only require pulling a new image tag and swapping the running container, eliminating the manual file-copying and server-side rebuilding that the systemd workflow required.
