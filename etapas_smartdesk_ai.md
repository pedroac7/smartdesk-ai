# SmartDesk AI — Etapas restantes e checklist de entrega

> Objetivo: manter uma lista prática do que ainda falta para fechar o Trabalho 3, em ordem de prioridade, sem perder tempo com tarefas que não impactam a nota.

## 1. Estado atual validado

### Já desenvolvido e testado

- [x] Aplicação Java/Spring com múltiplos serviços.
- [x] Arquitetura de microserviços.
- [x] `config-server` com Spring Cloud Config.
- [x] `eureka-server` em modo cluster local nas portas `8761` e `8762`.
- [x] `gateway-service` com rotas no `config-repo`.
- [x] Rota externa:
  - `GET /api/tickets/status`
  - `POST /api/tickets/analyze`
- [x] `ticket-orchestrator-service` como coordenador principal.
- [x] Comunicação do Orchestrator com `ai-support-service` via GraphQL.
- [x] `ai-support-service` com GraphQL, modo fake/local, Chat Memory, RAG e chamada ao MCP próprio.
- [x] `support-rules-mcp-server` como MCP próprio.
- [x] `sla-function-service` com Spring Cloud Function.
- [x] Resilience4j no Orchestrator: Circuit Breaker, Retry, TimeLimiter, Bulkhead e fallback.
- [x] Observabilidade com Actuator, Prometheus e Grafana.
- [x] Scripts locais: `start-all.ps1`, `stop-all.ps1`, `build-all.ps1`, `smoke-test.ps1`, `test-ai-features.ps1`, `start-observability.ps1`, `stop-observability.ps1`, `check-observability.ps1`.

### Testes já validados

- [x] `scripts/build-all.ps1` passou.
- [x] `scripts/start-all.ps1` sobe os serviços.
- [x] `scripts/test-ai-features.ps1` mostra `category`, `priority`, `ragSource`, `mcpRuleUsed` e memória funcionando.
- [x] `scripts/smoke-test.ps1` com `ai-support-service` ligado retorna `mode = FAKE_AI`.
- [x] `scripts/smoke-test.ps1` com `ai-support-service` desligado retorna `mode = FALLBACK`.

---

## 2. Pendências principais em ordem de prioridade

## Etapa 1 — Confirmar e melhorar painel de Circuit Breaker no Grafana

### Por que é prioridade

O critério de resiliência com observabilidade vale muito na apresentação. O professor pode pedir para desligar uma instância e observar o Circuit Breaker.

### O que fazer

- [ ] Confirmar se o dashboard do Grafana mostra o Circuit Breaker `aiSupportService`.
- [ ] Garantir painel para estado do Circuit Breaker.
- [ ] Garantir painel para chamadas bem-sucedidas, chamadas com falha e chamadas rejeitadas.
- [ ] Gerar tráfego normal com `smoke-test.ps1`.
- [ ] Desligar `ai-support-service`.
- [ ] Rodar `smoke-test.ps1` novamente.
- [ ] Verificar se o Grafana/Prometheus mostra mudança.

### Métricas úteis

```promql
resilience4j_circuitbreaker_state
```

```promql
resilience4j_circuitbreaker_calls_seconds_count
```

```promql
http_server_requests_seconds_count
```

### Validação esperada

- Com IA ligada: `mode = FAKE_AI`.
- Com IA desligada: `mode = FALLBACK`.
- Grafana mostra alteração nas métricas do Circuit Breaker.

---

## Etapa 2 — Implementar Rate Limit no Gateway

### Por que é prioridade

Está explicitamente na lista de requisitos adicionais: “Rate limit para evitar muitas requisições”.

### O que fazer

- [ ] Adicionar rate limit no `gateway-service`.
- [ ] Preferir configuração no `config-repo/gateway-service.yml`.
- [ ] Se precisar de Redis, tratá-lo como backing service.
- [ ] Atualizar observabilidade se necessário.
- [ ] Criar teste simples para mostrar bloqueio após excesso de requisições.

### Regras importantes

- Não hardcodar rota em Java.
- Não quebrar `/api/tickets/status`.
- Não quebrar `/api/tickets/analyze`.
- O Gateway deve continuar sendo a entrada principal.

### Validação esperada

- Requisições normais continuam funcionando.
- Muitas requisições seguidas recebem resposta de bloqueio ou limitação.
- A configuração do rate limit fica em YAML/config externa.

---

## Etapa 3 — Configuração alterável em tempo de execução

### Por que é prioridade

Está na lista adicional: “O sistema deve ter uma configuração alterável em tempo de execução”.

### O que fazer

Criar uma demonstração simples e segura de runtime config, por exemplo:

- [ ] Propriedade `smartdesk.demo.message`.
- [ ] Endpoint no Orchestrator ou AI Service que lê essa propriedade.
- [ ] Usar `@RefreshScope`, se compatível.
- [ ] Expor `/actuator/refresh`, se necessário.
- [ ] Alterar valor no `config-repo`.
- [ ] Chamar refresh.
- [ ] Mostrar que a resposta mudou sem recompilar.

### Validação esperada

1. Endpoint retorna valor antigo.
2. Altera YAML no `config-repo`.
3. Chama refresh.
4. Endpoint retorna valor novo.
5. Serviço não é recompilado.

---

## Etapa 4 — MCP externo ou de terceiro

### Situação atual

Atualmente existe MCP próprio:

```text
support-rules-mcp-server
```

Ainda não existe MCP externo/de terceiro.

### Por que fazer

A imagem e a descrição do trabalho mostram “Seu MCP Server” e “MCP Server de Terceiro”. Para reduzir risco na avaliação, é melhor demonstrar os dois.

### Solução recomendada

Criar uma integração simples e controlada chamada:

```text
third-party-mcp-server
```

ou um “external MCP mock” configurável.

### O que fazer

- [ ] Criar serviço simples `third-party-mcp-server` ou equivalente.
- [ ] Porta sugerida: `8085`.
- [ ] Endpoints sugeridos:
  - `GET /external-mcp/status`
  - `GET /external-mcp/tools`
  - `POST /external-mcp/tools/vendor-advice`
- [ ] Fazer `ai-support-service` chamar esse serviço.
- [ ] Adicionar campos internos ou logs demonstrando uso.
- [ ] Não quebrar contrato final do Orchestrator.
- [ ] Atualizar scripts e Prometheus se necessário.

### Validação esperada

- MCP próprio continua retornando `NETWORK_STANDARD_TRIAGE` e `HARDWARE_STANDARD_TRIAGE`.
- MCP externo retorna uma recomendação adicional.
- Falha do MCP externo não quebra GraphQL.

---

## Etapa 5 — Spring AI com modelo real

### Situação atual

O sistema já tem serviço de IA arquiteturalmente, mas os testes mostram modo local/fake:

```text
mode = FAKE_AI
```

Isso é bom para estabilidade, mas ainda é recomendável demonstrar uma chamada real a modelo de IA.

### O que fazer

- [ ] Confirmar se `smartdesk.ai.mode=openai` está implementado de verdade.
- [ ] Usar variável de ambiente `OPENAI_API_KEY`.
- [ ] Não commitar chave.
- [ ] Criar fallback para manter modo fake caso não haja chave.
- [ ] Criar script opcional `scripts/test-openai-mode.ps1`.
- [ ] Documentar como ativar/desativar.

### Validação esperada

- Sem chave: serviço inicia e modo fake funciona.
- Com chave: o AI Service faz chamada real via Spring AI e o sistema continua retornando pelo Gateway.

### Observação

A demonstração com chave real deve ser opcional. Para apresentação, o modo fake é mais estável. Mas ter o modo real implementado reduz risco conceitual no requisito “AI-Powered”.

---

## Etapa 6 — Docker Compose completo da aplicação

### Situação atual

Prometheus e Grafana já sobem com Docker. A aplicação Java ainda está rodando localmente via scripts PowerShell.

### O que fazer

- [ ] Criar Dockerfiles para os serviços Java, se necessário.
- [ ] Criar `docker-compose.yml` completo.
- [ ] Incluir:
  - `config-server`;
  - `eureka-server-a`;
  - `eureka-server-b`;
  - `gateway-service`;
  - `ticket-orchestrator-service`;
  - `ai-support-service`;
  - `sla-function-service`;
  - `support-rules-mcp-server`;
  - `third-party-mcp-server`, se criado;
  - `redis`, se usado no rate limit;
  - `prometheus`;
  - `grafana`.
- [ ] Ajustar URLs: trocar `localhost` por nomes de serviço Docker.
- [ ] Ajustar Prometheus targets.
- [ ] Testar Gateway dentro do Docker.

### Validação esperada

Um comando sobe tudo:

```powershell
docker compose up -d
```

E o fluxo funciona:

```powershell
.\scripts\smoke-test.ps1
```

---

## Etapa 7 — JMeter

### Por que é prioridade

O PDF exige testes de carga com JMeter, mais de 5 usuários simultâneos, Summary Report com zero erros em operação normal e investigação de Knee Capacity e Usable Capacity.

### O que fazer

- [ ] Criar plano JMeter em `jmeter/smartdesk-load-test.jmx`.
- [ ] Alvo: `POST http://localhost:8080/api/tickets/analyze`.
- [ ] Configurar Thread Group com mais de 5 usuários.
- [ ] Configurar ramp-up simples.
- [ ] Configurar HTTP Header Manager com `Content-Type: application/json`.
- [ ] Configurar Summary Report.
- [ ] Configurar View Results Tree apenas para debug.
- [ ] Configurar Aggregate Report, se útil.
- [ ] Criar massa JSON de teste.
- [ ] Rodar carga normal.
- [ ] Registrar throughput, erro, latência média, percentis e ponto em que começa degradação.

### Validação esperada

- Cenário normal: mais de 5 usuários, zero erros ou o menor possível, carga abaixo do Knee Capacity.
- Cenário com falha: desligar componente, erro/fallback aumenta, religar componente, erro/fallback diminui.

---

## Etapa 8 — Knee Capacity e Usable Capacity

### O que medir

- [ ] Testar 5 usuários.
- [ ] Testar 10 usuários.
- [ ] Testar 20 usuários.
- [ ] Testar 30 usuários.
- [ ] Testar 50 usuários, se o ambiente aguentar.
- [ ] Anotar throughput, latência média, p95, taxa de erro e CPU/memória, se disponível.

### Como interpretar

- **Usable Capacity:** maior carga com latência aceitável e erro próximo de zero.
- **Knee Capacity:** ponto em que aumentar carga passa a piorar muito a latência/erro sem ganho proporcional de throughput.

### Entrega esperada

Criar um resumo em:

```text
jmeter/README.md
```

com uma tabela simples:

| Usuários | Throughput | Latência média | p95 | Erros | Observação |
|---:|---:|---:|---:|---:|---|
| 5 | | | | | |
| 10 | | | | | |
| 20 | | | | | |
| 30 | | | | | |

---

## Etapa 9 — Revisão Twelve-Factor

### O que verificar

- [ ] Configuração fora do código.
- [ ] URLs configuráveis em `config-repo`.
- [ ] Sem secrets commitados.
- [ ] Logs no stdout.
- [ ] Serviços stateless sempre que possível.
- [ ] Dependências declaradas no Maven.
- [ ] Port binding por configuração.
- [ ] Paridade dev/prod com Docker Compose.
- [ ] Scripts de build/run documentados.

### Entrega esperada

Adicionar seção no README explicando como o projeto atende aos 12 fatores.

---

## Etapa 10 — README final e roteiro de apresentação

### O que fazer

- [ ] Atualizar README principal.
- [ ] Explicar arquitetura.
- [ ] Explicar serviços e portas.
- [ ] Explicar como subir localmente.
- [ ] Explicar observabilidade.
- [ ] Explicar Resilience4j.
- [ ] Explicar Spring AI, Chat Memory, RAG e MCP.
- [ ] Explicar JMeter.
- [ ] Explicar Knee Capacity e Usable Capacity.
- [ ] Criar roteiro de apresentação.

### Roteiro recomendado

1. Mostrar arquitetura.
2. Subir serviços com script.
3. Mostrar Eureka.
4. Rodar smoke test.
5. Mostrar GraphQL/RAG/MCP.
6. Mostrar Prometheus targets.
7. Mostrar Grafana.
8. Parar AI Service.
9. Mostrar fallback.
10. Mostrar Circuit Breaker.
11. Reiniciar AI Service.
12. Rodar JMeter.
13. Explicar Knee Capacity e Usable Capacity.

---

# Checklist final de entrega

## Requisitos do PDF

- [x] Java + Spring.
- [x] Microserviços.
- [ ] Twelve-Factor revisado e documentado.
- [x] Pelo menos três microserviços.
- [x] Microserviço de IA.
- [x] Microserviço Serverless Function.
- [x] Resiliência.
- [x] Observabilidade.
- [x] HTTP/TCP.
- [ ] JMeter.
- [ ] Knee Capacity.
- [ ] Usable Capacity.

## Critérios de avaliação

### 1. Twelve-Factor — 1,0

- [ ] Revisar e documentar no README.

### 2. Gateway, Config Server, Eureka, GraphQL, Serverless Function — 3,0

- [x] Gateway.
- [x] Config Server.
- [x] Eureka.
- [x] GraphQL.
- [x] Serverless Function.

### 3. Resiliência com Observabilidade — 3,0

- [x] Resilience4j.
- [x] Fallback.
- [x] Prometheus.
- [x] Grafana.
- [ ] Confirmar painel claro de Circuit Breaker.
- [ ] Demonstrar falha e recuperação.

### 4. Spring AI, Chat Memory, RAG, MCPs — 3,0

- [x] AI Service.
- [x] Chat Memory.
- [x] RAG.
- [x] MCP próprio.
- [ ] MCP externo/de terceiro.
- [ ] Spring AI com modelo real, se possível.

---

# Alterações sugeridas no AGENTS.md

O `AGENTS.md` atual está bom e já cobre grande parte do escopo completo. Ele já descreve a arquitetura, stack, serviços, scripts, regras de configuração, observabilidade, Resilience4j, GraphQL, Spring AI, RAG, MCP, Docker, JMeter e apresentação.

Mesmo assim, eu faria pequenos ajustes para refletir o estado atual e evitar que o Codex se confunda nas próximas etapas.

## Ajuste 1 — Atualizar status atual

Trocar a seção `Current implementation status` para algo como:

```text
1. Config Server — implemented
2. Eureka cluster — implemented
3. Config/Eureka clients — implemented
4. Gateway routes — implemented
5. Minimal fake business flow — implemented
6. Resilience4j — implemented
7. Local startup/build scripts — implemented
8. Observability with Prometheus/Grafana — implemented
9. Spring AI / RAG / Chat Memory / own MCP — implemented/stabilized locally
10. Third-party/external MCP — pending
11. Gateway rate limiting — pending
12. Runtime-refreshable configuration — pending
13. Docker Compose for full stack — pending
14. JMeter load testing — pending
15. Final README and presentation scripts — pending
```

## Ajuste 2 — Registrar MCP externo

Adicionar em `MCP rules`:

```text
The project should demonstrate:
- one own MCP server: support-rules-mcp-server;
- one third-party/external MCP integration, which may be simulated locally for demo reliability.
```

## Ajuste 3 — Registrar estado da IA real

Adicionar em `Spring AI rules`:

```text
Fake mode is acceptable for stable local demos, but the project should preferably include an optional real LLM mode using Spring AI and OPENAI_API_KEY to clearly satisfy the AI-powered requirement.
```

## Ajuste 4 — Adicionar Rate Limit como pendência explícita

Em `Gateway and rate limiting rules`, adicionar:

```text
Rate limiting is still pending until explicitly implemented and validated.
```

## Ajuste 5 — Adicionar runtime refresh como pendência explícita

Criar pequena seção:

```text
## Runtime configuration refresh rules

The project should demonstrate at least one runtime-changeable property loaded from Config Server.
Prefer a simple demo property and Actuator refresh if compatible.
```

## Ajuste 6 — Atualizar sequência recomendada

Em `Presentation demo rules`, adicionar antes do JMeter:

```text
Run rate limit demo if implemented.
Show runtime config refresh if implemented.
Show own MCP and external MCP result if implemented.
```

---

# Próxima etapa recomendada

A próxima etapa que eu faria é:

```text
Rate Limit no Gateway
```

Motivo: é um requisito explícito da sua lista adicional e provavelmente mais rápido que Docker/JMeter.

Depois:

```text
Runtime config refresh
Grafana Circuit Breaker polish
MCP externo/de terceiro
Spring AI real opcional
JMeter
Knee/Usable Capacity
Docker Compose completo
README final
```
