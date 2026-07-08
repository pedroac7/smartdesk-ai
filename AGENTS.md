# AGENTS.md — SmartDesk AI

## Project overview

SmartDesk AI is a Java/Spring distributed web application for intelligent support ticket triage.

The system receives a support ticket description, routes it through Spring Cloud Gateway, coordinates the business flow in a ticket orchestrator service, calls an AI service through GraphQL, uses Spring AI with Chat Memory, RAG and MCP, calls a Spring Cloud Function service to calculate SLA, and exposes observability metrics for Prometheus/Grafana.

The project is a monorepo with multiple Spring Boot services.

## Main academic goal

Build a simple, reliable, demonstrable distributed system for an academic Distributed Programming project.

The project must clearly demonstrate:

1. Java/Spring distributed systems;
2. microservices architecture;
3. Spring Cloud Config Server;
4. Netflix Eureka service discovery;
5. Spring Cloud Gateway;
6. GraphQL communication between services;
7. Spring Cloud Function as a serverless-style service;
8. Resilience4j patterns;
9. observability with Actuator, Prometheus and Grafana;
10. Spring AI;
11. Chat Memory;
12. RAG;
13. MCP-style external tools/rules;
14. Docker Compose;
15. JMeter load testing.

Prioritize:

1. correctness;
2. presentation reliability;
3. compatibility between Spring versions;
4. clear microservice boundaries;
5. resilience;
6. observability;
7. simple business logic;
8. fast local development.

Do not over-engineer the business domain.

## Required stack

Use these versions unless explicitly instructed otherwise:

- Java: 25
- Maven: 3.9.x or newer
- Spring Boot: 4.0.7
- Spring Framework: 7.x, managed by Spring Boot
- Spring Cloud: 2025.1.2
- Spring AI: 2.0.x
- Resilience4j: Spring Boot 4 compatible version, currently 2.4.x if already configured
- Prometheus: 3.x
- Grafana: 12.x
- JMeter: 5.6.x
- Docker Compose: 2.x

Do not downgrade to Spring Boot 3.x, Spring Framework 6.x or Java 21 unless explicitly requested.

Do not silently change Java, Spring Boot, Spring Cloud, Spring AI or Resilience4j versions.

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
├── scripts/
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
- expose Actuator health and Prometheus endpoints.

Expected local port:

```text
8888
```

Main validation URL:

```text
http://localhost:8888/application/default
```

### eureka-server

Spring Cloud Netflix Eureka Server.

Responsibilities:

- service discovery;
- run in local cluster mode;
- allow services to register dynamically;
- allow Gateway and internal clients to route with service names.

Expected local ports:

```text
8761
8762
```

Main validation URLs:

```text
http://localhost:8761
http://localhost:8762
```

For local stability, regular clients may use `http://localhost:8761/eureka/` as the main Eureka endpoint, while the Eureka servers themselves may still peer with each other.

### gateway-service

Spring Cloud Gateway.

Responsibilities:

- single external entry point;
- route requests to internal services;
- use routes from Config Server;
- use Eureka service names where possible;
- apply retry;
- later apply rate limiting;
- expose Actuator and Prometheus metrics.

Expected local port:

```text
8080
```

Important rule:

Do not hardcode Gateway routes in Java code. Routes must be configured through YAML served by the Config Server, usually in:

```text
config-repo/gateway-service.yml
```

Main external endpoints:

```text
GET  /api/tickets/status
POST /api/tickets/analyze
```

The Gateway should route:

```text
/api/tickets/** -> lb://ticket-orchestrator-service
```

and remove the `/api` prefix before forwarding.

### ticket-orchestrator-service

Main business coordinator.

Responsibilities:

- expose REST endpoints for ticket analysis;
- receive requests from Gateway;
- call `ai-support-service` through GraphQL;
- call `sla-function-service` through HTTP/Spring Cloud Function endpoint;
- apply Resilience4j patterns around the AI service call;
- return consolidated response;
- expose Actuator, Prometheus and circuit breaker metrics.

Expected local port:

```text
8081
```

Main internal endpoints:

```text
GET  /tickets/status
POST /tickets/analyze
```

The Orchestrator must call the AI service through GraphQL.

Do not replace the GraphQL call with plain REST unless explicitly instructed.

Resilience must be implemented here for calls to the AI service:

- Circuit Breaker;
- Retry;
- TimeLimiter;
- Bulkhead;
- fallback response.

The fallback response must keep the system usable when `ai-support-service` is down.

### ai-support-service

AI-powered microservice.

Responsibilities:

- expose GraphQL API;
- classify support tickets;
- support an implemented fake/local mode when no real API key is available;
- in fake mode, demonstrate classification, RAG lookup, Chat Memory and the project's own MCP rules service;
- use Spring AI/OpenAI only when explicitly configured, for example with `smartdesk.ai.mode=openai`;
- use Chat Memory by `conversationId`;
- use RAG with documents from `rag-docs`;
- call tools/rules from `support-rules-mcp-server`;
- expose Actuator and Prometheus metrics.

Expected local port:

```text
8082
```

Expected GraphQL operation:

```graphql
analyzeTicket(input: AnalyzeTicketInput!): TicketAnalysis!
```

Expected GraphQL fields:

```graphql
category
priority
summary
suggestedAnswer
ragSource
mcpRuleUsed
```

AI behavior must be controlled by configuration:

```yaml
smartdesk:
  ai:
    mode: fake
```

Expected modes:

```text
fake
openai
```

Default mode must be `fake`.

The service must start and work without `OPENAI_API_KEY` in fake mode.

Never hardcode API keys.

### sla-function-service

Spring Cloud Function service.

Responsibilities:

- implement a simple stateless function;
- calculate SLA based on category and priority;
- return `slaHours` and `supportTeam`;
- expose Actuator and Prometheus metrics.

Expected local port:

```text
8083
```

Expected function:

```text
calculateSla
```

Keep this service simple and stateless.

Do not add database persistence to this service.

### support-rules-mcp-server

Minimal MCP-style support rules service.

Responsibilities:

- expose external support rules/tools that the AI service can use;
- keep support procedures outside the AI service;
- demonstrate MCP/tool integration;
- expose Actuator and Prometheus metrics.

Expected local port:

```text
8084
```

Current expected endpoints:

```text
GET  /mcp/status
GET  /mcp/tools
POST /mcp/tools/support-rule
```

Expected support-rule input:

```json
{
  "category": "REDE",
  "priority": "MEDIA"
}
```

Expected support-rule output:

```json
{
  "tool": "support-rule",
  "ruleName": "NETWORK_STANDARD_TRIAGE",
  "recommendation": "Verificar conectividade, autenticação e alcance do roteador."
}
```

Rules may be simple:

```text
REDE -> NETWORK_STANDARD_TRIAGE
HARDWARE -> HARDWARE_STANDARD_TRIAGE
SUPORTE_GERAL -> GENERAL_SUPPORT_TRIAGE
```

Priority `ALTA` may add critical/urgent escalation wording.

## Business domain

Keep the domain simple.

Main flow:

```text
Support ticket description
  -> Gateway
  -> Ticket Orchestrator
  -> AI classification through GraphQL
  -> RAG document lookup
  -> MCP support rule lookup
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

Example normal/fake output:

```json
{
  "category": "REDE",
  "priority": "MEDIA",
  "summary": "Problema de conectividade Wi-Fi.",
  "suggestedAnswer": "Verifique se o adaptador Wi-Fi está ativo e tente reconectar à rede.",
  "slaHours": 8,
  "supportTeam": "Suporte de Redes",
  "mode": "FAKE_AI"
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

The final response from the Orchestrator must preserve the public contract expected by:

```text
POST /api/tickets/analyze
```

## Current implementation status

The project is being developed incrementally.

Known implemented or expected stages:

```text
1. Config Server - implemented
2. Eureka cluster - implemented
3. Config/Eureka clients - implemented
4. Gateway routes - implemented
5. Minimal fake business flow - implemented
6. Resilience4j - implemented
7. Local startup/build scripts - implemented
8. Observability with Prometheus/Grafana - implemented, needs dashboard polish for presentation
9. Spring AI/RAG/Chat Memory/project MCP - implemented locally in fake mode
10. External/third-party MCP - pending
11. Gateway rate limit - pending
12. Runtime-changeable configuration demo - pending
13. Real LLM mode with Spring AI/OpenAI - pending or optional but recommended
14. Docker Compose for full stack - pending
15. JMeter load testing - pending
16. Knee Capacity and Usable Capacity investigation - pending
17. Final README and presentation script - pending
```

When asked to continue development, prefer the next unfinished item in this order unless explicitly instructed otherwise.

## Twelve-Factor requirements

The project must follow Twelve-Factor principles.

Pay special attention to:

- config outside code;
- dependency declaration in Maven;
- port binding;
- stateless services where possible;
- logs to stdout;
- backing services treated as attached resources;
- quick startup/shutdown;
- dev/prod parity with Docker Compose;
- environment variables for secrets and runtime differences.

Never commit API keys, tokens, credentials or `.env` files.

## Configuration rules

Configuration must live in `config-repo` whenever possible.

Do not hardcode these values in Java code:

- service ports;
- Gateway routes;
- Resilience4j thresholds;
- rate limit settings;
- fallback messages when they are configurable;
- AI model configuration;
- API keys;
- external service URLs;
- RAG document path;
- MCP service base URL.

Use local service `application.yml` only for bootstrap/minimal configuration needed to reach the Config Server when necessary.

Prefer property names under:

```yaml
smartdesk:
  services:
  ai:
  rag:
```

Important examples:

```yaml
smartdesk:
  ai:
    mode: fake
  rag:
    docs-path: ../rag-docs
  services:
    ai-support:
      base-url: http://ai-support-service
    sla-function:
      base-url: http://sla-function-service
    support-rules-mcp:
      base-url: http://support-rules-mcp-server
```

For local non-load-balanced troubleshooting, it is acceptable to temporarily configure URLs like:

```text
http://localhost:8082
http://localhost:8083
http://localhost:8084
```

but keep them in `config-repo`, not hardcoded in Java.

### Runtime-changeable configuration demo

This is still pending.

The project should demonstrate a simple runtime configuration change through Config Server. Keep the demo small and reliable.

Good candidate properties:

```text
smartdesk.ai.mode
smartdesk.demo.message
a simple demo threshold or message used by one service
```

Prefer `/actuator/refresh` if it is compatible with the current Spring Boot/Spring Cloud stack and already available dependencies. Do not create a complex custom configuration system for this requirement.

## Build commands

Use Maven Wrapper inside each service.

From a service directory on Windows:

```powershell
.\mvnw.cmd clean package -DskipTests
```

From Linux/macOS:

```bash
./mvnw clean package -DskipTests
```

There is no root Maven aggregator unless explicitly created.

If available, prefer the project script:

```powershell
.\scripts\build-all.ps1
```

The build script should stop if any service fails.

## Running commands and scripts

During early debugging, running one service at a time is acceptable.

For faster development, prefer the scripts in `scripts/` when available.

Expected scripts:

```text
scripts/start-all.ps1
scripts/stop-all.ps1
scripts/build-all.ps1
scripts/smoke-test.ps1
scripts/test-ai-features.ps1
scripts/start-observability.ps1
scripts/stop-observability.ps1
scripts/check-observability.ps1
```

`start-all.ps1` should start services in this order:

```text
1. config-server
2. eureka-server on 8761
3. eureka-server on 8762
4. ai-support-service
5. sla-function-service
6. support-rules-mcp-server
7. ticket-orchestrator-service
8. gateway-service
```

`gateway-service` should start last.

If services behave inconsistently, stop all Java services and start again:

```powershell
.\scripts\stop-all.ps1
.\scripts\start-all.ps1
```

Prometheus/Grafana may continue running while Java services are restarted.

## Testing strategy

At minimum, every implementation task should end with:

1. compile the changed service;
2. run relevant unit tests if present;
3. verify application startup if the change affects configuration or runtime behavior;
4. run a small smoke test if the change affects the request flow.

For infrastructure changes, verify:

- Config Server endpoint;
- Eureka dashboard;
- Gateway route;
- Actuator health endpoint;
- Prometheus metrics endpoint.

Useful local checks:

```powershell
.\scripts\smoke-test.ps1
.\scripts\test-ai-features.ps1
.\scripts\check-observability.ps1
```

Main manual validation endpoints:

```text
GET  http://localhost:8080/api/tickets/status
POST http://localhost:8080/api/tickets/analyze
GET  http://localhost:8081/actuator/circuitbreakers
GET  http://localhost:8081/actuator/circuitbreakerevents
GET  http://localhost:8084/mcp/status
GET  http://localhost:8084/mcp/tools
```

## Observability rules

Prometheus and Grafana are already present in `observability/`.

All services should expose Actuator health.

All services should expose Prometheus metrics:

```text
/actuator/prometheus
```

Important metrics:

- service status;
- request count;
- request latency;
- error rate;
- JVM memory;
- circuit breaker state;
- circuit breaker successful calls;
- circuit breaker failed calls;
- circuit breaker rejected calls.

Prometheus should scrape local services from Docker using:

```text
host.docker.internal
```

Expected local Prometheus targets:

```text
host.docker.internal:8888
host.docker.internal:8761
host.docker.internal:8762
host.docker.internal:8080
host.docker.internal:8081
host.docker.internal:8082
host.docker.internal:8083
host.docker.internal:8084
```

Prometheus URL:

```text
http://localhost:9090
```

Grafana URL:

```text
http://localhost:3000
```

The Grafana dashboard still needs presentation polish. It must clearly show:

- UP/DOWN status of services;
- HTTP traffic by service;
- HTTP latency;
- JVM memory;
- `aiSupportService` circuit breaker state;
- circuit breaker events/calls.

Important Prometheus queries:

```promql
up
```

```promql
http_server_requests_seconds_count
```

```promql
sum by (job) (rate(http_server_requests_seconds_count[1m]))
```

```promql
sum by (job) (rate(http_server_requests_seconds_sum[1m]))
/
sum by (job) (rate(http_server_requests_seconds_count[1m]))
```

```promql
jvm_memory_used_bytes
```

```promql
resilience4j_circuitbreaker_state
```

```promql
resilience4j_circuitbreaker_calls_seconds_count
```

If metric names change, search in Prometheus using prefixes:

```text
resilience4j
http_server
jvm_memory
```

## Resilience rules

Use Resilience4j for remote calls, mainly in `ticket-orchestrator-service`.

Required patterns:

- Circuit Breaker;
- Retry;
- TimeLimiter;
- Bulkhead.

The AI service call must have fallback behavior.

The fallback must keep the system responding even when `ai-support-service` is unavailable.

Expected fallback mode:

```text
FALLBACK
```

Normal local AI mode from the Orchestrator may remain:

```text
FAKE_AI
```

Resilience4j configuration should live in:

```text
config-repo/ticket-orchestrator-service.yml
```

Avoid hardcoding thresholds in Java.

Recommended circuit breaker name:

```text
aiSupportService
```

## Gateway and rate limiting rules

Gateway should be the external entry point for load tests and demos.

Main endpoint for load testing:

```text
POST /api/tickets/analyze
```

Gateway routes must be configured in `config-repo`, not Java code.

Gateway retry is already configured for the main ticket route.

Rate limiting is still pending and should be implemented in `gateway-service`.

If rate limiting requires Redis, keep Redis as an external backing service and configure it externally.

Do not break the existing `/api/tickets/**` routes while implementing rate limiting.

## GraphQL rules

The Orchestrator must call the AI service through GraphQL.

The AI service must expose:

```graphql
type Query {
  analyzeTicket(input: AnalyzeTicketInput!): TicketAnalysis!
}
```

The exact GraphQL schema may be extended, but existing fields must remain compatible.

Required fields:

```graphql
category
priority
summary
suggestedAnswer
ragSource
mcpRuleUsed
```

Scripts that call GraphQL from PowerShell should use:

```powershell
ConvertTo-Json -Depth 10
```

Scripts must print GraphQL errors when they occur instead of hiding them.

## Serverless Function rules

The SLA service must use Spring Cloud Function.

Keep function logic small and stateless.

Expected function:

```text
calculateSla
```

Do not add unnecessary database persistence to this service.

Expected behavior:

```text
priority ALTA -> slaHours = 4
category REDE and priority MEDIA -> slaHours = 8, supportTeam = Suporte de Redes
category HARDWARE and priority MEDIA -> slaHours = 12, supportTeam = Suporte de Hardware
other cases -> slaHours = 24, supportTeam = Triagem Manual
```

## Spring AI rules

The AI service should demonstrate Spring AI usage when possible.

However, the project must remain functional without a real API key.

Fake mode is implemented and is useful for local demos. It should continue to demonstrate ticket classification, RAG, Chat Memory and the project's own MCP rules service without external credentials.

Expected configuration:

```yaml
smartdesk:
  ai:
    mode: fake
```

Allowed modes:

```text
fake
openai
```

Rules:

- default mode must be `fake`;
- fake mode must remain demonstrable and must not be removed;
- do not hardcode API keys;
- use environment variables such as `OPENAI_API_KEY`;
- do not commit `.env` files;
- fake mode must not call external LLM APIs;
- OpenAI mode should only be used when explicitly configured;
- real LLM mode with Spring AI/OpenAI should be implemented and tested if time allows;
- if no real key is available, the project must remain fully demonstrable in fake mode.

If Spring AI APIs are unstable due to version compatibility, prefer a simple, build-stable implementation that still clearly demonstrates the architecture and configuration.

## Chat Memory rules

Chat Memory should be based on `conversationId`.

A simple in-memory implementation is acceptable for the academic demo.

Expected behavior:

- store recent user descriptions per `conversationId`;
- store or derive previous responses;
- use previous conversation context to enrich `summary` or `suggestedAnswer`;
- keep implementation simple and easy to explain.

Do not add a database only for Chat Memory unless explicitly requested.

## RAG rules

RAG documents live in:

```text
rag-docs/
```

Accepted document names may include:

```text
rede.md
hardware.md
suporte-geral.md
faq-rede.md
faq-hardware.md
faq-sistemas.md
politica-sla.md
```

Keep documents small and easy to demonstrate.

A simple keyword-based RAG retrieval is acceptable.

Suggested mapping:

```text
wifi, wi-fi, internet, rede -> rede.md or faq-rede.md
notebook, computador, teclado, mouse, monitor -> hardware.md or faq-hardware.md
sistema, login, acesso, erro -> faq-sistemas.md if present
other cases -> suporte-geral.md
```

The selected document should influence:

```text
summary
suggestedAnswer
ragSource
```

The `ragSource` field should identify the document used.

## MCP rules

MCP support is split into the project's own MCP-style rules service and a future external/third-party MCP demonstration.

Do not make MCP complex.

Its purpose is to demonstrate that the AI service can call external tools/rules.

### Project MCP

The current project-owned MCP-style service is:

```text
support-rules-mcp-server
```

Expected local port:

```text
8084
```

Current endpoints:

```text
GET  /mcp/status
GET  /mcp/tools
POST /mcp/tools/support-rule
```

The AI service should call the MCP server through configurable base URL:

```yaml
smartdesk:
  services:
    support-rules-mcp:
      base-url: http://support-rules-mcp-server
```

If load-balanced service names do not work locally, use a configurable local URL in `config-repo`:

```text
http://localhost:8084
```

Do not hardcode this URL in Java.

If the MCP server fails, the AI service must not fail the GraphQL request.

Expected fallback field:

```text
mcpRuleUsed = "mcp-unavailable"
```

### External/third-party MCP

External or third-party MCP integration is still pending.

It should be implemented in a simple and demonstrable way. Acceptable approaches include:

- a local service simulating a third-party MCP server, for example `third-party-mcp-server` on port `8085`;
- a configurable integration with an external/local tool endpoint.

Rules:

- configuration must live in `config-repo`;
- the main ticket flow must not fail if the external MCP is unavailable;
- failure should produce a compatible fallback value, such as `externalMcpRuleUsed = "external-mcp-unavailable"` or an equivalent backward-compatible field;
- do not replace the project MCP with external MCP.

## Docker rules

Docker should be added after the services are working locally.

Do not prematurely dockerize before local service flow is stable.

Planned Docker Compose services:

```text
config-server
eureka-server-a
eureka-server-b
gateway-service
ticket-orchestrator-service
ai-support-service
sla-function-service
support-rules-mcp-server
redis
prometheus
grafana
```

When moving to Docker:

- replace `localhost` service URLs with Docker service names;
- verify Config Server paths;
- verify Eureka registration hostnames;
- verify Gateway routing;
- verify Prometheus targets;
- keep secrets out of images and Git.

## JMeter rules

JMeter plans live in:

```text
jmeter/
```

JMeter load testing is still pending.

The main load test should target Gateway, not internal services directly.

Main endpoint:

```text
POST /api/tickets/analyze
```

The presentation must show:

- more than 5 simultaneous users;
- a way to identify Knee Capacity;
- a way to identify Usable Capacity;
- load below Knee Capacity for normal operation;
- zero errors in normal operation if possible;
- increased errors or fallback behavior after killing instances;
- decreased errors or fallback behavior after restarting instances;
- Grafana/Prometheus showing the effect of load, failures and recovery.

JMeter artifacts should be simple and reproducible.

Prefer a `.jmx` test plan and a small README explaining how to run it.

## Presentation demo rules

The project must be reliable during presentation.

Preferred demo sequence:

```text
1. Start all services.
2. Show Eureka with registered services.
3. Show Gateway status endpoint.
4. Run smoke test with normal mode.
5. Show final response with mode = FAKE_AI.
6. Show direct AI feature script demonstrating RAG, Chat Memory and project MCP.
7. If implemented, show external/third-party MCP behavior.
8. Show Prometheus targets UP.
9. Show Grafana dashboard with HTTP/JVM/circuit breaker metrics.
10. Stop ai-support-service.
11. Run smoke test again.
12. Show mode = FALLBACK.
13. Show circuit breaker state/events/calls in Grafana.
14. Restart ai-support-service.
15. Show recovery.
16. Run JMeter test and discuss Knee Capacity and Usable Capacity.
```

Avoid risky live coding during presentation.

Prefer scripts and prevalidated commands.

## Coding style

Use clear Java code.

Prefer:

- constructor injection;
- records for simple DTOs;
- meaningful package names;
- small classes;
- explicit names;
- clear configuration properties;
- simple error handling;
- logs that help debugging.

Avoid:

- complex domain modeling;
- premature persistence;
- hidden static state;
- hardcoded service URLs;
- over-engineering;
- unrelated refactors;
- large cross-service rewrites unless explicitly requested.

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

Before committing, run at least:

```powershell
git status
```

When possible, commit after each stable milestone.

## Security rules

Never commit:

- API keys;
- OpenAI/Gemini/Claude keys;
- passwords;
- tokens;
- `.env`;
- private credentials;
- local secrets files.

Use environment variables or ignored local files for secrets.

If a secret is accidentally added, stop and ask before trying to fix Git history.

## Workflow for Codex

For each task:

1. read this `AGENTS.md`;
2. inspect only the relevant services/files;
3. make the smallest useful change that completes the task;
4. avoid unrelated refactors;
5. avoid changing versions unless explicitly requested;
6. keep public contracts stable;
7. explain changed files;
8. provide exact commands to build/test;
9. report validation results honestly;
10. do not silently change architecture.

When unsure, ask for clarification before changing the project direction.

## Fast development workflow

Prefer larger implementation blocks only when the user explicitly asks to accelerate.

Even in accelerated mode:

- keep changes scoped;
- preserve architecture;
- compile affected services;
- update scripts when useful;
- avoid manual-only steps when a script can be created;
- keep the project demonstrable at all times.

Typical fast loop:

```text
1. implement block;
2. run build for changed services or build-all;
3. run start-all;
4. run smoke-test;
5. run specific feature script;
6. fix failures;
7. commit stable state.
```

## Do not do without explicit instruction

Do not:

- add a database;
- replace GraphQL with REST between Orchestrator and AI service;
- remove the fake AI mode;
- make an AI key mandatory for local demos;
- replace the project MCP with external MCP;
- remove Eureka;
- remove Config Server;
- remove Resilience4j;
- remove Prometheus/Grafana;
- downgrade Java/Spring;
- add authentication/security complexity;
- add frontend UI;
- add Kubernetes;
- add complex persistence;
- add a database without an explicit request;
- introduce paid/external services as mandatory;
- commit secrets.
