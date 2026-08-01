# Day 31 — Docker Basics

## 1. Why Docker?

**Problem:** "Works on my machine" — different Java versions, OS configs, or libraries between environments (local machine, EC2, teammate's laptop) cause code to break unpredictably.

**Analogy:** Just like shipping containers standardized cargo transport (any ship/truck can carry the same container shape regardless of contents), Docker standardizes software so it runs identically anywhere.

**Key concepts:**

| Term | Meaning | AWS Equivalent |
|---|---|---|
| Docker Image | Blueprint/template (read-only) | AMI |
| Docker Container | Running instance of an image | EC2 Instance |
| Docker Hub | Public registry for images | — |

- `docker ps` → shows only **running** containers
- `docker ps -a` → shows **all** containers (running + stopped/exited)

---

## 2. Installing Docker on WSL2 (Ubuntu)

```bash
# Update package list
sudo apt update

# Install prerequisites
sudo apt install -y ca-certificates curl gnupg

# Add Docker's official GPG key
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# Add Docker's repository
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker Engine
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Allow running docker without sudo
sudo usermod -aG docker $USER
# (close and reopen terminal after this)
```

**Verify:**
```bash
docker --version
docker ps
docker run hello-world
```

---

## 3. Issue Faced: Java Version Mismatch

**Error:** `release version 21 not supported`

**Cause:** `pom.xml` had `<java.version>21</java.version>`, but WSL2 only had Java 17 installed.

**Fix:** Changed `pom.xml` to match Java 17 (consistent with EC2 production and the Dockerfile base image):
```xml
<java.version>17</java.version>
```

**Rebuild:**
```bash
mvn clean package -DskipTests
```

---

## 4. Issue Faced: Windows Path vs WSL2 Native Path

Project initially lived at `/mnt/c/Users/.../Desktop/BookManagement` (Windows filesystem, accessed via WSL2). Docker builds are slower/less reliable across this boundary.

**Fix:** Copied project into WSL2's native filesystem:
```bash
mkdir -p ~/projects
cp -r "/mnt/c/Users/vishv/OneDrive/Desktop/BookManagement" ~/projects/
cd ~/projects/BookManagement
```

---

## 5. Dockerfile

```dockerfile
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

COPY target/BookManagement-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar"]
```

| Line | Purpose |
|---|---|
| `FROM eclipse-temurin:17-jdk-jammy` | Base image with Java 17 pre-installed |
| `WORKDIR /app` | Sets working directory inside the image |
| `COPY ...` | Copies the built JAR into the image |
| `EXPOSE 8090` | Documents which port the app uses |
| `ENTRYPOINT ...` | Command that runs when the container starts |

---

## 6. Build the Image

```bash
docker build -t bookmanagement:v1 .
```

Verify:
```bash
docker images
```

---

## 7. Run the Container

```bash
docker run -d -p 8090:8090 \
  -e DB_PASSWORD=<masked> \
  -e DB_ADMIN_PASSWORD=<masked> \
  -e JWT_SECRET=<masked> \
  --name bookmanagement-container \
  bookmanagement:v1
```

**Note:** RDS must be running (`available` state) before starting the container, otherwise the app fails to connect to the database.

---

## 8. Verification

```bash
curl http://localhost:8090/health
```

**Result:** `OK from <container-id>` — confirms the app is alive and the container ID is embedded in the health response (useful for identifying which container/instance served a request when scaled).

**Logs confirm full startup:**
```bash
docker logs bookmanagement-container
```
Key lines to check:
- `HikariPool-1 - Added connection ...` → RDS connection successful
- `Started BookManagementApplication in X seconds` → app fully booted, no errors

---

## 9. Container Lifecycle Commands

| Action | Command |
|---|---|
| Build image | `docker build -t name:tag .` |
| Run container | `docker run -d -p host:container -e VAR=value --name x image` |
| List running containers | `docker ps` |
| List all containers | `docker ps -a` |
| Stop container (graceful) | `docker stop <name>` |
| Restart existing container | `docker start <name>` |
| View logs (snapshot) | `docker logs <name>` |
| View logs (live/follow) | `docker logs -f <name>` |
| Remove container | `docker rm <name>` |
| List images | `docker images` |

**Note:** `docker stop` triggers Spring Boot's graceful shutdown (`GracefulShutdown` in logs) — closes DB connections and finishes active requests before stopping, unlike a forceful kill.

**Note:** Deleting a container (`docker rm`) does **not** delete the image — the image remains reusable to create new containers.

---

## 10. Key Takeaway

BookManagement now runs identically whether on EC2 (systemd service) or in a Docker container — same Java 17, same JAR, same RDS connection — proving the "works on my machine" problem is solved for this project.
