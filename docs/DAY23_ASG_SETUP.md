# Day 23 — Auto Scaling Group (ASG) Setup

## Overview
Configured AWS Auto Scaling Group to automatically scale EC2 instances
running BookManagement based on CPU utilization, integrated with the
existing Application Load Balancer from Day 22.

## Components Created

- **Launch Template**: `bookmanagement-lt`
    - AMI: `bookmanagement-ami-v2`
    - Instance type: `t3.micro`
    - Security group: `launch-wizard-1`

- **Auto Scaling Group**: `bookmanagement-asg`
    - Min: 1, Max: 3, Desired: 1 (default)
    - Attached to existing target group: `bookmanagement-tg`
    - ELB health checks enabled (uses `/health` endpoint)

- **Scaling Policy**: Target tracking
    - Metric: Average CPU Utilization
    - Target: 70%

## How It Works

1. ASG monitors CPU only on instances it launches itself
   (manually created EC2s are NOT monitored by ASG)
2. When average CPU > 70% sustained, ASG launches a new EC2
   from the Launch Template
3. New EC2 auto-registers into `bookmanagement-tg`
4. ALB automatically starts routing traffic to it once healthy
5. When CPU drops, ASG scales back down (respecting Min capacity)

## Verified With

Ran `stress --cpu 2 --timeout 300` on the ASG-launched instance
to simulate load. Confirmed:
- CPU graph in CloudWatch spiked to ~94%
- ASG desired capacity auto-updated from 1 → 2
- New instance auto-registered and passed health checks

## Cost Note

To avoid ongoing charges when not in use:
- Set ASG Min/Desired capacity to 0 (cleanly terminates its instances)
- Stop manual EC2s and RDS separately
- AMI, Launch Template, Target Group, ASG config itself are free to keep