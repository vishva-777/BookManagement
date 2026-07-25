# Day 28 — API Gateway Setup

## Overview
Added an API Gateway trigger to the test Lambda function to expose it as a public HTTPS endpoint. Without API Gateway, Lambda can only be invoked by AWS-internal events (e.g. S3 uploads, Day 27) or manual console tests — never by an external browser, Postman, or frontend request.

## Components Created
- **API Gateway type**: HTTP API (simpler/cheaper than REST API for this use case)
- **Attached to**: `bookmanagement-test-lambda`
- **Security**: Open (no auth — test/proof-of-concept only)
- **Live endpoint**:
  `https://gq6485w3fe.execute-api.eu-north-1.amazonaws.com/default/bookmanagement-test-lambda`

## Verification
- Opened the endpoint directly in a browser, no AWS login required
- Response: `"Hello from Lambda!"` — confirms the Lambda function is now publicly reachable via a normal HTTPS URL

## Trigger Comparison
| Trigger | Fires when |
|---|---|
| S3 (Day 27) | A file is uploaded — automatic, AWS-internal event |
| API Gateway (Day 28) | An external HTTP request hits the endpoint — manual, external call |

## Security Note
`Security: Open` means anyone on the internet can call this endpoint an unlimited number of times with no authentication. Since Lambda bills per invocation, an open endpoint is a real cost-attack surface — same risk pattern as a public S3 bucket (Day 26). The correct production fix is a JWT/Lambda Authorizer on API Gateway, conceptually equivalent to the JWT + role-based checks already protecting `/books/**` in `SecurityConfig.java`. Not implemented in this session — scoped to console-level understanding for this stage of the project.

## Status
Console-based proof of concept only. No changes to BookManagement's Spring Boot source code in this session.