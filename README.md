# SmartDesk AI

Aplicação distribuída AI-powered para triagem inteligente de chamados de suporte técnico.

Stack principal:
- Java 25
- Spring Boot 4
- Spring Framework 7
- Spring Cloud 2025.1.x
- Spring AI 2.x
- Resilience4j
- Eureka
- Config Server
- Spring Cloud Gateway
- GraphQL
- Spring Cloud Function
- Prometheus
- Grafana
- JMeter

## Execucao com Docker Compose

Antes de subir o stack Docker, pare a execucao local para liberar as portas 8080-8084, 8761-8762, 8888, 9090 e 3000.

Modo fake/local:

```powershell
.\scripts\docker-up.ps1
.\scripts\docker-smoke-test.ps1
```

Modo Gemini:

```powershell
$env:SMARTDESK_AI_MODE = "gemini"
$env:GEMINI_API_KEY = "sua-chave"
.\scripts\docker-up.ps1
.\scripts\docker-smoke-test.ps1
```

URLs principais:

```text
Gateway:    http://localhost:8080/api/tickets/status
Eureka 1:   http://localhost:8761
Eureka 2:   http://localhost:8762
Prometheus: http://localhost:9090
Grafana:    http://localhost:3000
```

Para derrubar:

```powershell
.\scripts\docker-down.ps1
```
