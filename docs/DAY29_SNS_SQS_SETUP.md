# Day 29 — SNS + SQS Setup

## Overview
Reviewed SNS (already used in Day 24 for CloudWatch alarm emails) in more depth, and created a new SQS queue to understand how it differs from SNS. SNS broadcasts one event to multiple subscribers at once. SQS holds messages in a queue until a worker picks them up and explicitly confirms processing is done.

## Components Created
- **SQS Queue**: `bookmanagement-upload-queue`
- **Type**: Standard (not FIFO — strict ordering not needed for this use case)
- **Region**: eu-north-1

## Verification
- Sent a test message: `Book cover uploaded: bht.jpg`
- Polled for messages — confirmed the message stayed in the queue with `Receive count: 1`, proving SQS does **not** delete a message just because it was read; it waits for an explicit delete confirmation from the worker

## SNS vs SQS
| | SNS | SQS |
|---|---|---|
| Pattern | Broadcast to many subscribers at once | Queue — waits for one worker to process |
| Use case | Notify email + SMS + Lambda simultaneously | Smooth traffic bursts, prevent message loss during downtime |

## Key Concept: Receive vs Delete
SQS separates "received" from "deleted" as a safety mechanism — if a worker crashes mid-processing, the message becomes available again for retry instead of being lost forever.

## Status
Console-based proof of concept only. No changes to BookManagement's Spring Boot source code in this session.