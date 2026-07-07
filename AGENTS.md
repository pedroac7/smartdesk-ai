# AGENTS.md — SmartDesk AI

## Project overview

SmartDesk AI is a Java/Spring distributed web application for intelligent support ticket triage.

The system receives a support ticket description, routes it through Spring Cloud Gateway, coordinates the business flow in a ticket orchestrator service, calls an AI service through GraphQL, uses Spring AI with Chat Memory, RAG and MCP, calls a Spring Cloud Function service to calculate SLA, and exposes observability metrics for Prometheus/Grafana.

The project is a monorepo with multiple Spring Boot services.

## Main goal

Build a simple, reliable, demonstrable distributed system for an academic Programming Distributed Systems project.

Prioritize:

1. correctness;
2. compatibility between Spring versions;
3. clear microservice boundaries;
4. resilience;
5. observability;
6. simple business logic;
7. presentation reliability.

Do not over-engineer the business domain.

## Required stack

Use these versions unless explicitly instructed otherwise:

- Java: 25
- Maven: 3.9.x or newer
- Spring Boot: 4.0.7
- Spring Framework: 7.x, managed by Spring Boot
- Spring Cloud: 2025.1.2
- Spring AI: 2.0.x
- Resilience4j: Spring Boot 4 compatible version
- Prometheus: 3.x
- Grafana: 12.x
- JMeter: 5.6.x
- Docker Compose: 2.x

Do not downgrade to Spring Boot 3.x, Spring Framework 6.x or Java 21 unless explicitly requested.

## Repository structure

Expected project structure:

```text
smartdesk-ai/
├── config-server/
├── eureka-server/
├── gateway-service/
├── ticket-orchestrator-service/
├── ai-support-service/
├── sla-function-service/
├── support-rules-mcp-server/
├── config-repo/
├── observability/
├── jmeter/
├── rag-docs/
├── docker/
├── README.md
└── AGENTS.md
```

## Services and responsibilities

### config-server

Spring Cloud Config Server.

Responsibilities:

- centralize configuration;
- serve configuration from `config-repo`;
- support externalized configuration following Twelve-Factor principles;
- expose Actuator health endpoints.

Expected port:

```text
8888
```

### eureka-server

Spring Cloud Netflix Eureka Server.

Responsibilities:

- service discovery;
- run in cluster mode;
- allow services to register dynamically;
- allow Gateway to route with `lb://service-name`.

Expected local ports:

```text
8761
8762
```

### gateway-service

Spring Cloud Gateway.

Responsibilities:

- single external entry point;
- route requests to internal services;
- use routes from Config Server;
- use Eureka service names;
- apply retry;
- apply rate limiting;
- expose Actuator metrics.

Important rule:

Do not hardcode Gateway routes in Java code. Routes must be configured through `application.yml` served by the Config Server.

### ticket-orchestrator-service

Main business coordinator.

Responsibilities:

- expose REST endpoint for ticket analysis;
- receive requests from Gateway;
- call `ai-support-service` through GraphQL;
- call `sla-function-service` through HTTP;
- apply Resilience4j patterns;
- expose circuit breaker metrics.

Expected main endpoint:

```text
POST /tickets/analyze
```

Resilience must be implemented here for calls to the AI service:

- Circuit Breaker;
- Retry;
- TimeLimiter;
- Bulkhead;
- fallback response.

### ai-support-service

AI-powered microservice.

Responsibilities:

- expose GraphQL API;
- classify support tickets;
- use Spring AI;
- use Chat Memory by `conversationId`;
- use RAG with documents from `rag-docs`;
- use MCP tools from `support-rules-mcp-server`.

Expected GraphQL operation:

```graphql
analyzeTicket(input: AnalyzeTicketInput!): TicketAnalysis!
```

### sla-function-service

Spring Cloud Function service.

Responsibilities:

- implement a simple stateless function;
- calculate SLA based on category and priority;
- return `slaHours` and `supportTeam`.

Expected function:

```text
calculateSla
```

Keep this service simple and stateless.

### support-rules-mcp-server

MCP server for support rules.

Responsibilities:

- expose tools that the AI service can use;
- keep support procedures outside the AI service;
- demonstrate MCP integration.

Planned tools:

```text
getSupportProcedure(category)
getEscalationRule(priority)
getAllowedActions(category)
```

## Business domain

Keep the domain simple.

Main flow:

```text
Support ticket description
  -> AI classification
  -> category
  -> priority
  -> suggested answer
  -> SLA calculation
  -> final response
```

Example input:

```json
{
  "conversationId": "demo-1",
  "description": "Meu notebook não conecta no Wi-Fi da universidade."
}
```

Example output:

```json
{
  "category": "REDE",
  "priority": "MEDIA",
  "summary": "Problema de conectividade Wi-Fi.",
  "suggestedAnswer": "Verifique se o adaptador Wi-Fi está ativo e tente reconectar à rede.",
  "slaHours": 8,
  "supportTeam": "Suporte de Redes",
  "mode": "NORMAL"
}
```

Fallback output example:

```json
{
  "category": "SUPORTE_GERAL",
  "priority": "MEDIA",
  "summary": "Análise automática indisponível no momento.",
  "suggestedAnswer": "Seu chamado foi registrado e será analisado por um atendente.",
  "slaHours": 24,
  "supportTeam": "Triagem Manual",
  "mode": "FALLBACK"
}
```

## Twelve-Factor requirements

The project must follow Twelve-Factor principles.

Pay special attention to:

- config outside code;
- dependency declaration in Maven;
- port binding;
- stateless services;
- logs to stdout;
- backing services treated as attached resources;
- quick startup/shutdown;
- dev/prod parity with Docker Compose.

Never commit API keys, tokens, credentials or `.env` files.

## Configuration rules

Configuration must live in `config-repo` whenever possible.

Do not hardcode these values in Java code:

- service ports;
- Gateway routes;
- Resilience4j thresholds;
- rate limit settings;
- fallback messages;
- AI model configuration;
- external service URLs.

Use local `application.yml` only for bootstrap/minimal configuration needed to reach the Config Server when necessary.

## Build commands

Use Maven Wrapper inside each service.

From a service directory:

```powershell
.\mvnw.cmd clean package -DskipTests
```

From Linux/macOS:

```bash
./mvnw clean package -DskipTests
```

There is not yet a root Maven aggregator unless explicitly created.

## Running commands

Prefer one service at a time during development.

Before making changes, understand which service is being changed and why.

Do not introduce large cross-service changes in a single task unless explicitly requested.

## Testing strategy

At minimum, every implementation task should end with:

1. compile the changed service;
2. run relevant unit tests if present;
3. verify application startup if the change affects configuration or runtime behavior.

For infrastructure changes, verify:

- Config Server endpoint;
- Eureka dashboard;
- Gateway route;
- Actuator health endpoint.

## Observability rules

All services should expose Actuator health.

Services involved in resilience and traffic should expose Prometheus metrics.

Important metrics:

- service status;
- request count;
- request latency;
- error rate;
- circuit breaker state;
- circuit breaker successful calls;
- circuit breaker failed calls;
- circuit breaker rejected calls.

Grafana must be able to show circuit breaker state during the presentation.

## Resilience rules

Use Resilience4j for remote calls, mainly in `ticket-orchestrator-service`.

Required patterns:

- Circuit Breaker;
- Retry;
- TimeLimiter;
- Bulkhead.

Rate limiting should be applied primarily in `gateway-service`.

The AI service call must have fallback behavior.

## GraphQL rules

The Orchestrator must call the AI service through GraphQL.

Do not replace this with plain REST unless explicitly instructed.

The AI service may still expose Actuator endpoints separately.

## Serverless Function rules

The SLA service must use Spring Cloud Function.

Keep function logic small and stateless.

Do not add unnecessary database persistence to this service.

## RAG rules

RAG documents live in:

```text
rag-docs/
```

Initial documents:

```text
faq-rede.md
faq-hardware.md
faq-sistemas.md
politica-sla.md
```

Keep documents small and easy to demonstrate.

## MCP rules

The MCP server should be minimal.

Do not make MCP complex.

Its purpose is to demonstrate that the AI service can call external tools/rules.

## Docker rules

Docker will be added after the services are configured and working locally.

Planned Docker Compose services:

- config-server;
- eureka-server-a;
- eureka-server-b;
- gateway-service;
- ticket-orchestrator-service;
- ai-support-service;
- sla-function-service;
- support-rules-mcp-server;
- redis;
- prometheus;
- grafana.

## JMeter rules

JMeter plans live in:

```text
jmeter/
```

The main load test should target Gateway, not internal services directly.

Main endpoint:

```text
POST /api/tickets/analyze
```

The presentation must show:

- more than 5 simultaneous users;
- load below Knee Capacity;
- zero errors in normal operation;
- increased errors/fallback after killing instances;
- recovery after restarting instances.

## Coding style

Use clear Java code.

Prefer:

- constructor injection;
- records for simple DTOs;
- meaningful package names;
- small classes;
- explicit names;
- no unnecessary abstractions.

Avoid:

- complex domain modeling;
- premature persistence;
- hidden static state;
- hardcoded service URLs;
- over-engineering.

## Git rules

Use small commits.

Suggested commit prefixes:

```text
chore:
feat:
fix:
docs:
test:
refactor:
```

Do not rewrite Git history unless explicitly requested.

Do not run destructive Git commands such as:

```text
git reset --hard
git clean -fd
git push --force
```

unless explicitly instructed.

## Security rules

Never commit:

- API keys;
- OpenAI/Gemini/Claude keys;
- passwords;
- tokens;
- `.env`;
- private credentials.

Use environment variables or local ignored files for secrets.

## Workflow for Codex

For each task:

1. read this `AGENTS.md`;
2. inspect the relevant service only;
3. make the smallest useful change;
4. explain changed files;
5. provide exact commands to build/test;
6. do not silently change versions or architecture;
7. do not introduce unrelated features.

When unsure, ask for clarification before changing the project direction.

## Current implementation phase

Current phase:

```text
Infrastructure setup
```

Recommended order:

```text
1. Config Server
2. Eureka cluster
3. Config/Eureka clients
4. Gateway routes
5. Minimal fake business flow
6. Resilience4j
7. Observability
8. Spring AI
9. MCP
10. RAG
11. Docker Compose
12. JMeter
```
