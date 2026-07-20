# Day 24 — CloudWatch Monitoring & Alarms Setup

## Overview
Configured CloudWatch Alarm + SNS to send an email notification when the
BookManagement app becomes unreachable (health check failing), separate
from the CPU-based auto-scaling alarms configured on Day 23.

## Components Created

- **SNS Topic**: `bookmanagement-alerts`
    - Email subscription confirmed

- **CloudWatch Alarm**: `bookmanagement-unhealthy-target-alert-v2`
    - Metric: `UnHealthyHostCount` (AWS/ApplicationELB)
    - Statistic: Average, Period: 1 minute
    - Condition: >= 1
    - Action: Notify `bookmanagement-alerts` SNS topic when "In alarm"

## How It Works

1. CloudWatch continuously monitors the ALB target group's health check results
2. If any target fails its `/health` check (app down but EC2 running),
   `UnHealthyHostCount` becomes >= 1
3. Alarm state changes: OK -> In alarm
4. SNS sends an email notification automatically
5. When the app recovers, alarm returns to OK and sends a "resolved" email

## Key Distinction Learned

A CPU-based alarm does NOT catch application crashes, because a crashed
process uses LOW CPU, not high. This alarm specifically watches target
health (`UnHealthyHostCount`), which is the correct signal for detecting
an app that has crashed or become unresponsive.

## Verified With

Manually stopped the app process only (`sudo systemctl stop bookmanagement`)
on a running EC2 instance -- not the instance itself -- to simulate a
realistic crash. Confirmed:
- Target group showed the instance as "Unhealthy"
- CloudWatch alarm transitioned to "In alarm"
- Email notification received
- After restarting the service, alarm returned to "OK" with a resolved
  notification

## Cost Note

CloudWatch Alarms and SNS Topics are free to keep configured (no idle
charge). Only the underlying EC2/RDS/ALB resources being monitored cost
money while running.

Important: if the ALB is deleted and recreated (e.g. during a cost-saving
pause), any alarm watching its metrics will silently stop working, since
each ALB gets a new unique ARN even if the name stays the same. Always
verify alarms after recreating the ALB.