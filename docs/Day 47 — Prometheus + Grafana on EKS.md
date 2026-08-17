# Day 47 — Prometheus + Grafana on EKS

## Concept

CloudWatch Logs (Day 46) tells you *what happened* — text events, errors. It doesn't easily show *live numeric trends* — CPU%, memory%, request rate over time.

- **`metrics-server`** (already installed by default) = a thermometer. Tells you CPU/memory usage **right now**, no history. Powers `kubectl top pods`.
- **Prometheus** = a notebook. Continuously scrapes and **stores** metrics over time, so you can look back at trends/spikes even after the fact.
- **Grafana** = the chart on the wall. Reads Prometheus's stored data and renders it as visual dashboards.

If a Pod crashes at 2pm and you want to see CPU usage in the 10 minutes before — `metrics-server` can't help (no memory of the past), but Prometheus can.

---

## New tool: Helm

Helm = a package manager for Kubernetes (like `apt`/`npm`, but for installing complex multi-Pod applications in one command instead of hand-writing YAML).

**Install:**
```bash
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

**Add the Prometheus community chart repo:**
```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
```

---

## Install `kube-prometheus-stack` (Prometheus + Grafana bundled)

```bash
helm install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring --create-namespace \
  --set prometheus.prometheusSpec.resources.requests.memory=200Mi \
  --set prometheus.prometheusSpec.resources.requests.cpu=100m \
  --set grafana.resources.requests.memory=100Mi \
  --set grafana.resources.requests.cpu=50m \
  --set alertmanager.enabled=false \
  --set prometheus.prometheusSpec.retention=1d
```

- `--namespace monitoring --create-namespace` -> keeps it isolated, same pattern as `amazon-cloudwatch`
- Resource `requests` flags -> cap how much CPU/RAM each component *asks for* -- helps fit on small nodes
- `alertmanager.enabled=false` -> skip alerting component, not needed for basic visibility
- `retention=1d` -> only keep 1 day of metrics (saves storage for a learning cluster)

WARNING: **Resource request flags do NOT fix the pod-count ceiling.** `t3.micro` caps at 4 Pods/node regardless of how small you make CPU/memory requests. `kube-prometheus-stack` installs many components at once -- Prometheus Operator, Prometheus itself, Grafana (3 containers), `kube-state-metrics`, `node-exporter` (a DaemonSet, 1 per node), and an admission-webhook patch job. That's a lot of extra Pods on top of what BookManagement + Fluent Bit already use.

---

## The scaling pattern across Days 45-47 (the real lesson)

| Day | What's running | Nodes needed |
|---|---|---|
| 45 | BookManagement alone | 2 nodes was enough |
| 46 | + Fluent Bit (logging) | needed 3 nodes |
| 47 | + kube-prometheus-stack (monitoring) | still Pending even at 4 nodes |

Each new capability needs roughly its own extra node's worth of room on `t3.micro`. This isn't a mistake in setup -- it's a genuine, real-world lesson: **production monitoring stacks need real node capacity.** Companies don't run Prometheus/Grafana on the smallest possible instance type for exactly this reason.

---

## Access Grafana (no LoadBalancer needed for local testing)

```bash
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80
```
Open `http://localhost:3000` in a browser.

**Get the actual admin password** (varies by chart version/install -- don't assume a default):
```bash
kubectl get secret monitoring-grafana -n monitoring -o jsonpath="{.data.admin-password}" | base64 --decode
```
Username: `admin`, Password: (output above)

---

## What we actually verified today

- Grafana fully deployed (3/3 containers Running) and reachable -- real login, real dashboard loaded
- Most `node-exporter` copies running
- Prometheus Operator running; the main `prometheus-0` Pod itself stayed Pending (never got a free node slot) -- so no live metrics data flowing into dashboards yet
- This is a legitimate, honest partial result -- not a failure. The *reason* it's Pending (real capacity constraint) is itself the day's key lesson.

---

## Key lessons from today

1. Prometheus = time-series metrics storage; Grafana = visualization on top of it; `metrics-server` = instant-only, no history.
2. Helm is the standard way to install complex Kubernetes apps like monitoring stacks -- one command instead of dozens of YAML files.
3. **Resource requests (CPU/memory) and Pod-count limits are two separate constraints.** Reducing resource requests doesn't help once you've hit the 4-pods-per-node ceiling.
4. Real monitoring stacks (`kube-prometheus-stack`) are genuinely heavy -- several Deployments + DaemonSets at once -- and don't comfortably fit on `t3.micro` even at 4 nodes.
5. It's fine to stop at a partial, honest result once the pattern is understood -- chasing 100% completion on undersized infrastructure just burns Free Tier hours without adding new learning.