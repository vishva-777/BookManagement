# Day 48 — Alerts & Notifications (Alertmanager + Slack)

## Concept

Day 47 gave us Prometheus (stores metrics) + Grafana (displays them) -- but nobody's watching the dashboard 24/7. If CPU spikes at 3am, nobody knows unless they happen to look.

Same problem you already solved once, on EC2 (Day 26-30) with CloudWatch Alarms + SNS:

| EC2 world (Day 26-30) | Kubernetes world (Day 48) |
|---|---|
| CloudWatch Alarm -- detects the breach | Prometheus -- detects the breach |
| SNS -- sends the actual notification | Alertmanager -- sends the actual notification |

Same two-step separation: one thing *watches*, another thing *notifies*. Prometheus evaluates rules ("is CPU > 80% for 5 min?"); when true, it hands off to **Alertmanager**, which decides *how* to notify (email, Slack, PagerDuty) and handles grouping/deduplication so you're not spammed.

---

## Step 1 — Slack Incoming Webhook

A webhook is a special URL Slack gives you -- anything POSTed to it becomes a Slack message. This is the reverse of normal Slack usage: your cluster pushes messages *to* Slack, Slack isn't watching anything.

**Setup (via api.slack.com/apps):**
1. Create New App -> From scratch (or "Blank app" on newer UI)
2. Name it, pick your workspace
3. Features -> Incoming Webhooks -> toggle On
4. "Add New Webhook to Workspace" -> pick a destination
5. Copy the Webhook URL: `https://hooks.slack.com/services/T.../B.../...`

WARNING: **The destination you pick during setup is permanent for that webhook.** If you pick yourself in the channel picker, it posts to your personal "Notes to Self" space (Direct messages -> "YourName (you)"), not a shared channel -- easy to miss if you're expecting it in a channel. Check the "Channel" column on the Incoming Webhooks settings page to confirm exactly where it's pointed.

**Test it directly, before wiring into anything:**
```bash
curl -X POST -H 'Content-type: application/json' \
  --data '{"text":"Test alert from BookManagement setup"}' \
  https://hooks.slack.com/services/YOUR/WEBHOOK/URL
```
A response of `ok` means Slack accepted it -- go check the actual destination (not just trust the `ok`).

---

## Step 2 — Wire the webhook into Alertmanager (via Helm values)

Alertmanager is part of the same `kube-prometheus-stack` Helm chart from Day 47 -- we just disabled it then (`alertmanager.enabled=false`) to save Pod slots. Today we re-enable it and configure it.

```bash
nano alertmanager-values.yaml
```

```yaml
alertmanager:
  enabled: true
  config:
    global:
      slack_api_url: 'https://hooks.slack.com/services/YOUR/WEBHOOK/URL'
    route:
      receiver: 'slack-notifications'
    receivers:
      - name: 'slack-notifications'
        slack_configs:
          - send_resolved: true
            title: '{{ .CommonAnnotations.summary }}'
            text: '{{ .CommonAnnotations.description }}'

prometheus:
  prometheusSpec:
    resources:
      requests:
        memory: 200Mi
        cpu: 100m
    retention: 1d

grafana:
  resources:
    requests:
      memory: 100Mi
      cpu: 50m
```

WARNING: **`slack_configs` is a YAML list, not a map** -- it needs a `-` before the first key (`- send_resolved: true`), with the following keys (`title`, `text`) indented to align with `send_resolved`, not the dash itself. Missing the dash silently breaks the block -- Helm won't error, it just won't create the Alertmanager resource properly.

If you omit the `channel:` field, Slack uses whatever destination the webhook was originally configured for.

**Install/upgrade:**
```bash
helm install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring --create-namespace -f alertmanager-values.yaml
```

**Verify config actually applied:**
```bash
helm get values monitoring -n monitoring
```

**Check if the Alertmanager custom resource exists (separate from a plain Pod):**
```bash
kubectl get alertmanager -n monitoring
```
This is a Prometheus-Operator-managed resource type. It can show `enabled: true` and still have `READY: 0` / `RECONCILED: False` if the Operator hasn't been able to actually create its Pods yet (usually a capacity issue, same as Day 46-47).

---

## The scaling pattern continues (now confirmed a 3rd time)

| Day | What's running | Nodes needed |
|---|---|---|
| 45 | BookManagement alone | 2 nodes |
| 46 | + Fluent Bit (logging) | 3 nodes |
| 47 | + kube-prometheus-stack (monitoring) | still Pending at 4 nodes |
| 48 | + Alertmanager on top of that | still Pending at 5 nodes (23 Pods trying to fit into 20 slots) |

At this point the lesson is fully confirmed, not new: `t3.micro` clusters cannot realistically host a full logging + monitoring + alerting stack alongside an application. Real environments size nodes for the whole observability stack, not just the app.

---

## What we actually verified today (without needing the live cluster component)

Since Alertmanager's actual Pod never got scheduled (capacity), we proved the *integration logic* directly instead -- sending a payload shaped exactly like what Alertmanager sends to Slack:

```bash
curl -X POST -H 'Content-type: application/json' --data '{
  "text": "*BookManagementApplication Alert*\n*Status:* firing\n*Summary:* High CPU usage detected on BookManagement Pod\n*Description:* CPU usage has exceeded 80% for more than 5 minutes."
}' https://hooks.slack.com/services/YOUR/WEBHOOK/URL
```

This confirmed the message renders correctly in Slack (bold headers, line breaks, structured fields) -- proving the webhook + payload format is solid and will work the moment Alertmanager itself has room to run.

---

## Key lessons from today

1. Prometheus detects, Alertmanager notifies -- exact same split as CloudWatch Alarms + SNS on EC2, different names.
2. Slack webhooks are one-way, destination-locked at creation time -- always verify the actual "Channel" shown in the webhook settings, don't assume.
3. YAML lists need their dash (`-`) even when there's only one item -- a missing dash silently drops the whole block without any Helm error.
4. `kubectl get <custom-resource>` (like `kubectl get alertmanager`) can show a resource "exists" while its actual Pods never got created -- check the underlying Pod/StatefulSet separately to know the real status.
5. When live infrastructure is blocked by a known, already-understood constraint (Pod capacity), testing the *integration logic* directly (e.g. replicating the exact webhook payload) is a legitimate way to prove a concept without re-solving the same capacity problem a fourth time.