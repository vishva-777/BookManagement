# Day 34 — Push to Docker Hub

## 1. Why Push to Docker Hub?

**Problem:** The `bookmanagement:v2` image only existed locally on WSL2. It couldn't be used on EC2, shared with teammates, or pulled by any other machine without manually rebuilding it there.

**Analogy:** Docker Hub is like GitHub, but for Docker images instead of code. Just as `git push` makes code available to pull from anywhere, `docker push` makes an image available to `docker pull` from anywhere — no rebuilding required on the destination machine.

**Real-world relevance:** Production deployment pipelines build an image once, push it to a registry, and every server simply pulls that exact same image — guaranteeing consistency between what was tested and what's deployed.

---

## 2. Docker Hub Account Setup

- Created a **Personal** Docker Hub account (not "Work" — that's for team/org billing, not individual learning projects).
- Username: `vishvapari` (Docker image names must be lowercase, even if the account was created with different casing).

---

## 3. Logging In from the Terminal

```bash
docker login
```

Used Docker's web-based login flow: terminal displayed a one-time device code, confirmed via browser at `https://login.docker.com/activate`, then returned `Login Succeeded` in the terminal.

---

## 4. Tagging the Image for Docker Hub

**Concept:** Docker Hub requires image names in the format: