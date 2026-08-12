# Day 46 — CloudWatch Logs on EKS

## Concept

EC2 logs stay on disk forever (one machine, one log file). Pods are **disposable** — Kubernetes kills/replaces them constantly, so `kubectl logs` only works while the Pod still exists. Once it's gone, the logs are gone too.

**Fix:** ship logs out of the Pod, in real-time, to a permanent store — **CloudWatch Logs**.

Lambda does this automatically. EKS does **not** — you have to install a log agent yourself.

---

## Key concept: DaemonSet

- **Deployment** = run N replicas, spread across nodes.
- **DaemonSet** = run exactly 1 copy **on every node**, automatically (3 nodes → 3 copies).

Log agents must be a DaemonSet — each node generates its own logs locally, so each node needs its own shipper.

---

## Step 1 — Cluster + Pod room

Create cluster with **3 nodes directly** (2 wasn't enough once logging agents are added):

```bash
eksctl create cluster \
  --name bookmanagement-cluster \
  --region eu-north-1 \
  --zones eu-north-1a,eu-north-1b \
  --nodegroup-name standard-workers \
  --node-type t3.micro \
  --nodes 3 \
  --nodes-min 1 \
  --nodes-max 3 \
  --managed
```

Trim `metrics-server` to free a slot (same as Day 45):
```bash
kubectl scale deployment metrics-server -n kube-system --replicas=1
```

⚠️ **Lesson:** `t3.micro` = 4 pods/node hard cap. The full **AWS CloudWatch Observability addon** installs *two* DaemonSets — `fluent-bit` AND `cloudwatch-agent` — that's 2 slots per node just for logging, on top of `aws-node`, `kube-proxy`. On `t3.micro` this leaves **zero room** for your app + `coredns` + `metrics-server`. Don't use the full addon on tiny clusters — use Fluent Bit standalone instead (below).

---

## Step 2 — Redeploy app (Secret + Deployment, same as Day 45)

```bash
kubectl create secret generic bookmanagement-secret \
  --from-literal=DB_URL="jdbc:mysql://vishva-database-3.clkm44eaeacb.eu-north-1.rds.amazonaws.com:3306/Bookdb" \
  --from-literal=DB_PASSWORD="vishva007" \
  --from-literal=JWT_SECRET="xEq5iTkBpwhG/BAR2HYDWhHQUzAxacQKRItiQdc5x4k=" \
  --from-literal=DB_ADMIN_PASSWORD="admin123"

kubectl apply -f deployment.yaml
```

⚠️ Secrets don't survive a cluster delete — recreate every time. ⚠️ RDS must be `available` first.

---

## Step 3 — Install Fluent Bit standalone (lightweight, log-shipping only)

**a) IAM permission** — nodes need permission to write to CloudWatch:
```bash
eksctl get nodegroup --cluster bookmanagement-cluster --region eu-north-1 --name standard-workers -o json | grep NodeInstanceRoleARN
# then:
aws iam attach-role-policy \
  --role-name <role-name-from-above> \
  --policy-arn arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy
```

**b) Namespace:**
```bash
kubectl apply -f https://raw.githubusercontent.com/aws-samples/amazon-cloudwatch-container-insights/latest/k8s-deployment-manifest-templates/deployment-mode/daemonset/container-insights-monitoring/cloudwatch-namespace.yaml
```
⚠️ Correct folder is `container-insights-monitoring` — not `container-insights` (that URL 404s silently).

**c) ConfigMap** — must be named exactly `fluent-bit-cluster-info`, with **all** these keys (missing any of them → `CreateContainerConfigError`):
```bash
kubectl create configmap fluent-bit-cluster-info \
  --from-literal=cluster.name=bookmanagement-cluster \
  --from-literal=logs.region=eu-north-1 \
  --from-literal=http.server=Off \
  --from-literal=http.port=2020 \
  --from-literal=read.head=Off \
  --from-literal=read.tail=On \
  -n amazon-cloudwatch
```

**d) The DaemonSet itself:**
```bash
kubectl apply -f https://raw.githubusercontent.com/aws-samples/amazon-cloudwatch-container-insights/latest/k8s-deployment-manifest-templates/deployment-mode/daemonset/container-insights-monitoring/fluent-bit/fluent-bit.yaml
```

**Confirm:**
```bash
kubectl get pods -n amazon-cloudwatch
```
Should show `fluent-bit-xxxxx` × (1 per node), all `1/1 Running`.

⚠️ If Pods show `CreateContainerConfigError`, they don't auto-retry — force it:
```bash
kubectl delete pods -l k8s-app=fluent-bit -n amazon-cloudwatch
```

⚠️ You may briefly see `[error] CreateLogGroup ... OperationAbortedException` in the logs — harmless race condition (all node copies trying to create the same Log Group at once). Self-resolves once one succeeds.

---

## Step 4 — Verify in CloudWatch Console

Go to **CloudWatch → Log groups**, find:
```
/aws/containerinsights/<cluster-name>/application
```
Click the log stream → should show real app log lines (Spring Boot startup, shutdown, etc.), timestamped and permanent — even after the Pod that generated them is gone.

To force fresh logs for testing: delete the Pod (Deployment auto-recreates it):
```bash
kubectl delete pod <pod-name>
```

---

## Key lessons from today

1. **Pods are disposable → logs need to be shipped out**, not just read live via `kubectl logs`.
2. **DaemonSet** = 1 copy per node (not a `replicas` count like Deployment).
3. The full CloudWatch Observability addon is too heavy for `t3.micro` — installs 2 DaemonSets, eats too many pod slots. Use standalone Fluent Bit instead.
4. ConfigMap name and keys must match **exactly** what the DaemonSet manifest expects — one wrong name (`cluster-info` vs `fluent-bit-cluster-info`) or missing key breaks the whole thing silently.
5. `CreateContainerConfigError` Pods don't self-retry after a ConfigMap fix — must manually delete the Pods.
6. A brief `OperationAbortedException` on Log Group creation is a normal race condition with multiple DaemonSet copies, not a real failure.