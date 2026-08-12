# Day 45 — Deploy BookManagement on EKS

## Concept

We're deploying BookManagement onto EKS. Kubernetes runs multiple copies (replicas) of the app, and if one dies, it automatically starts another to replace it — no manual `systemctl restart` needed like on EC2.

Analogy: Zomato doesn't run order-service on one lonely server. They have a **cluster** of machines and a manager (Kubernetes) that says "keep N copies alive, restart on failure."

You already built the cluster (Day 44). Today you deploy the app onto it and give it a stable address.

---

## Step 1 — Create the cluster

```bash
eksctl create cluster \
  --name bookmanagement-cluster \
  --region eu-north-1 \
  --zones eu-north-1a,eu-north-1b \
  --nodegroup-name standard-workers \
  --node-type t3.micro \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 2 \
  --managed
```

⚠️ **Note:** Use `--nodes 2` directly (not `--nodes 1` then scaling later) — this creates both nodes upfront in one shot and skips a manual scaling step. Takes 15–20 min.

⚠️ **Free Tier note:** `t3.medium` fails silently with `InvalidParameterCombination`. Always use `t3.micro`.

**Confirm:**
```bash
kubectl get nodes
```
`STATUS: Ready` means the node is healthy and can run Pods.

---

## Step 2 — Understand and fix the Pod limit (important lesson from this session)

`t3.micro` allows a **hard max of 4 Pods per node** (AWS network interface limit — not a CPU/RAM issue).

System Pods already eat most of that: `aws-node`, `kube-proxy`, `coredns` (x2), `metrics-server` (x2 by default).

With only 1 node, there's zero room left for your app → Pods get stuck `Pending` with error `Too many pods`.

**Fix — trim `metrics-server` to 1 replica** (it's just monitoring, not essential):
```bash
kubectl scale deployment metrics-server -n kube-system --replicas=1
```

With 2 nodes (from Step 1) + this trim, you'll have enough free slots for your app.

---

## Step 3 — Check your app's port

Check `application.properties` for `server.port`. (BookManagement uses **8090**.)

---

## Step 4 — Create the Secret

Concept: On EC2 you put `DB_PASSWORD`, `JWT_SECRET` etc. into `.bashrc`. In Kubernetes, you use a **Secret** instead — a locked drawer inside the cluster that the Pod reads from at startup.

⚠️ **Missed in original notes:** your app needs **4** values, not 3 — `DB_ADMIN_PASSWORD` is also required (found via a `CrashLoopBackOff` debugging session). Fetch all 4 from EC2's `.bashrc` first:

```bash
cat ~/.bashrc | grep -E 'DB_URL|DB_PASSWORD|JWT_SECRET|DB_ADMIN_PASSWORD'
```

Then create the Secret with all 4:

```bash
kubectl create secret generic bookmanagement-secret \
  --from-literal=DB_URL="jdbc:mysql://vishva-database-3.clkm44eaeacb.eu-north-1.rds.amazonaws.com:3306/Bookdb" \
  --from-literal=DB_PASSWORD="vishva007" \
  --from-literal=JWT_SECRET="xEq5iTkBpwhG/BAR2HYDWhHQUzAxacQKRItiQdc5x4k=" \
  --from-literal=DB_ADMIN_PASSWORD="admin123"
```

**Confirm:**
```bash
kubectl get secrets
```
Should show `bookmanagement-secret` with `DATA: 4`.

---

## Step 5 — Write `deployment.yaml`

⚠️ **Corrected — original notes were missing the `DB_ADMIN_PASSWORD` block.** All 4 secret keys must be wired in, or the app crashes with `Could not resolve placeholder 'DB_ADMIN_PASSWORD'`.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: bookmanagement-deployment
spec:
  replicas: 1
  selector:
    matchLabels:
      app: bookmanagement
  template:
    metadata:
      labels:
        app: bookmanagement
    spec:
      containers:
        - name: bookmanagement
          image: vishvapari/bookmanagement:v2
          ports:
            - containerPort: 8090
          env:
            - name: DB_URL
              valueFrom:
                secretKeyRef:
                  name: bookmanagement-secret
                  key: DB_URL
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: bookmanagement-secret
                  key: DB_PASSWORD
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: bookmanagement-secret
                  key: JWT_SECRET
            - name: DB_ADMIN_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: bookmanagement-secret
                  key: DB_ADMIN_PASSWORD
```

⚠️ **YAML indentation matters.** Every `- name:` under `env:` must have identical indentation (12 spaces). One extra/missing space breaks parsing (`did not find expected '-' indicator`).

⚠️ `replicas: 1` — on a small `t3.micro` setup, 2 replicas may not fit even after trimming `metrics-server`. Start with 1.

**Before applying — make sure RDS is running:**
```bash
aws rds describe-db-instances --db-instance-identifier vishva-database-3 --region eu-north-1 --query 'DBInstances[0].DBInstanceStatus'
```
If it says `stopped`:
```bash
aws rds start-db-instance --db-instance-identifier vishva-database-3 --region eu-north-1
```
(Takes 3–5 min. If RDS is down, the Pod will start but crash with `CrashLoopBackOff` — Hibernate can't connect.)

**Apply:**
```bash
kubectl apply -f deployment.yaml
```

**Confirm:**
```bash
kubectl get pods
```
Look for `1/1 Running` with low/zero restarts.

**Check logs for a clean startup:**
```bash
kubectl logs <pod-name> --tail=15
```
Look for: `Started BookManagementApplication in X seconds`

---

## Step 6 — Write `service.yaml` (expose the app)

Concept: the Pod only has an internal cluster IP. A Service gives it a stable, reachable address. `type: LoadBalancer` provisions a real AWS load balancer so it's reachable from your browser.

⚠️ **Cost note:** LoadBalancer has its own small hourly charge, separate from EC2/EKS Free Tier.

```yaml
apiVersion: v1
kind: Service
metadata:
  name: bookmanagement-service
spec:
  type: LoadBalancer
  selector:
    app: bookmanagement
  ports:
    - protocol: TCP
      port: 80
      targetPort: 8090
```

**Line-by-line, plain English:**

- `kind: Service` → this object's job is routing traffic, not running containers (that's the Deployment's job).
- `metadata.name: bookmanagement-service` → the name this Service is registered under inside the cluster.
- `type: LoadBalancer` → tells AWS "provision a real, internet-facing Elastic Load Balancer for this Service." (Other types exist — `ClusterIP` for internal-only, `NodePort` for exposing via a node's port — but `LoadBalancer` is what makes it reachable from a browser.)
- `selector: app: bookmanagement` → this is how the Service finds which Pods to send traffic to. It looks for any Pod labeled `app: bookmanagement` — which matches the label you set in `deployment.yaml`'s `template.metadata.labels`. If these two labels don't match, the Service exists but has nowhere to route traffic.
- `ports.port: 80` → the port people hit from *outside* — standard HTTP, so no `:8090` needed in the URL.
- `ports.targetPort: 8090` → the port your app actually listens on *inside* the container. The Service receives traffic on 80 and forwards it to 8090.
- `protocol: TCP` → the transport protocol used (HTTP itself runs on top of TCP, so this is standard for web traffic).

**Apply:**
```bash
kubectl apply -f service.yaml
```

**Get the public address:**
```bash
kubectl get service bookmanagement-service
```
Wait for `EXTERNAL-IP` to populate (a real AWS DNS hostname, not `<pending>`).

---

## Step 7 — Verify it actually works end-to-end (missing from original notes)

Hitting the root `/` in a browser will show a `403 Whitelabel Error Page` — that's actually a **good sign**, it means the Load Balancer → Pod → Spring Security chain is working. Spring Security is just correctly blocking an unauthenticated request to a protected root path.

To really confirm the app + DB + auth all work, test the real login endpoint (check `AuthController.java` for the exact `@PostMapping` path — don't assume, verify):

```bash
curl -X POST http://<your-loadbalancer-dns>/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"user123"}'
```

A valid JWT token in the response = full success: internet → Load Balancer → Pod → Spring Security → RDS, all working.

---

## Step 8 — Clean up (always do this to avoid charges)

```bash
eksctl delete cluster --name bookmanagement-cluster --region eu-north-1
```

Optional, if you want to stop DB billing too:
```bash
aws rds stop-db-instance --db-instance-identifier vishva-database-3 --region eu-north-1
```

---

## Key lessons from this session

1. `t3.micro` caps Pods at 4/node — plan node count and `metrics-server` replicas around this before deploying, not after hitting `Pending`.
2. Missing a single env var (`DB_ADMIN_PASSWORD`) causes a full crash — always cross-check *every* placeholder in `application.properties` against your Secret, not just the obvious ones.
3. YAML indentation errors are silent killers — one extra space broke the whole file.
4. **RDS must be running** before the Pod can connect — it doesn't auto-start with the cluster.
5. EC2 does **not** need to be running for the EKS deployment to work — the app now runs as a container inside EKS, independent of the old EC2/systemd setup.
6. A `403` on `/` is not a failure — it confirms the whole pipeline is reachable; the real test is hitting an actual endpoint.