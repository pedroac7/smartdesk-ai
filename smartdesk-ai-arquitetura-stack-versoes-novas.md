# SmartDesk AI — Arquitetura e Stack do Projeto

## 1. Ideia do projeto

**SmartDesk AI** é uma aplicação web distribuída para triagem inteligente de chamados de suporte técnico.

O usuário envia uma descrição de problema, por exemplo:

> “Meu notebook não conecta no Wi-Fi.”

O sistema deve:

1. receber a requisição pelo **Spring Cloud Gateway**;
2. encaminhar para um microserviço coordenador;
3. chamar um microserviço de IA via **GraphQL**;
4. usar **Spring AI** para classificar o chamado;
5. usar **Chat Memory** para manter contexto da conversa;
6. usar **RAG** para consultar uma base de conhecimento local;
7. usar **MCP** para consultar regras/procedimentos externos;
8. chamar uma função serverless para calcular SLA;
9. expor métricas para **Prometheus/Grafana**;
10. resistir a falhas usando **Resilience4j**.

O objetivo não é criar um sistema grande. O objetivo é criar uma arquitetura distribuída simples, demonstrável e aderente aos requisitos do trabalho.

---

## 2. Por que esse tema é simples?

O tema é simples porque a regra de negócio é pequena:

- não exige cadastro complexo;
- não exige banco relacional obrigatório;
- não exige autenticação real;
- não exige frontend sofisticado;
- não exige muitas entidades;
- permite demonstrar todos os requisitos com um único fluxo principal.

O fluxo principal é:

```text
Cliente/JMeter
  ↓
Gateway
  ↓
Ticket Orchestrator
  ↓ GraphQL
AI Support Service
  ↓ Spring AI
LLM + Chat Memory + RAG + MCP
  ↓
Ticket Orchestrator
  ↓ HTTP
SLA Function Service
  ↓
Resposta final
```

---

## 3. Arquitetura geral

```text
smartdesk-ai
│
├── infra
│   ├── eureka-server-a
│   ├── eureka-server-b
│   ├── config-server
│   ├── redis
│   ├── prometheus
│   └── grafana
│
├── edge
│   └── gateway-service
│
├── services
│   ├── ticket-orchestrator-service
│   ├── ai-support-service
│   ├── sla-function-service
│   └── support-rules-mcp-server
│
├── config-repo
│   ├── gateway-service.yml
│   ├── ticket-orchestrator-service.yml
│   ├── ai-support-service.yml
│   ├── sla-function-service.yml
│   └── application.yml
│
├── rag-docs
│   ├── faq-rede.md
│   ├── faq-hardware.md
│   ├── faq-sistemas.md
│   └── politica-sla.md
│
└── jmeter
    └── smartdesk-load-test.jmx
```

---

## 4. Componentes

### 4.1 `gateway-service`

**Tecnologia:** Spring Cloud Gateway

Responsabilidades:

- ser o ponto único de entrada da aplicação;
- receber requisições HTTP externas;
- aplicar rate limit;
- aplicar retry;
- rotear usando nomes de serviços registrados no Eureka;
- buscar as rotas no Config Server;
- não conter rotas hardcoded no código Java.

Rotas planejadas:

```text
/api/tickets/**  → lb://ticket-orchestrator-service
/api/status/**   → lb://ticket-orchestrator-service
```

Recursos exigidos aqui:

- retry no gateway;
- rate limit;
- rotas via Eureka;
- rotas configuradas em `application.yml` no Config Server.

---

### 4.2 `ticket-orchestrator-service`

**Papel:** microserviço coordenador da lógica de negócio.

Responsabilidades:

- receber chamadas do Gateway;
- expor endpoint REST simples para o cliente/JMeter;
- chamar o `ai-support-service` via GraphQL;
- chamar o `sla-function-service` via HTTP;
- consolidar a resposta final;
- aplicar os padrões de resiliência com Resilience4j;
- expor métricas do circuit breaker para Prometheus/Grafana.

Endpoint principal:

```text
POST /tickets/analyze
```

Entrada conceitual:

```json
{
  "conversationId": "demo-1",
  "description": "Meu notebook não conecta no Wi-Fi da universidade"
}
```

Saída conceitual:

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

Resiliência planejada:

- Circuit Breaker na chamada GraphQL para o `ai-support-service`;
- Retry em falhas transitórias;
- TimeLimiter para evitar espera longa;
- Bulkhead para isolar chamadas remotas;
- fallback quando a IA estiver indisponível.

Fallback esperado:

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

---

### 4.3 `ai-support-service`

**Papel:** microserviço AI-powered.

Responsabilidades:

- expor API GraphQL;
- receber descrição do chamado;
- montar prompt;
- usar Spring AI;
- usar Chat Memory;
- usar RAG;
- usar MCP;
- devolver classificação e resposta sugerida.

Operação GraphQL planejada:

```graphql
type Query {
  analyzeTicket(input: AnalyzeTicketInput!): TicketAnalysis!
}

input AnalyzeTicketInput {
  conversationId: String!
  description: String!
}

type TicketAnalysis {
  category: String!
  priority: String!
  summary: String!
  suggestedAnswer: String!
  ragSource: String
  mcpRuleUsed: String
}
```

Uso de Spring AI:

- `ChatClient` para comunicação com o modelo;
- `ChatMemory` para manter contexto por `conversationId`;
- `VectorStore` simples para RAG com documentos locais;
- MCP para buscar regras de atendimento e procedimentos.

RAG planejado:

```text
rag-docs/
  faq-rede.md
  faq-hardware.md
  faq-sistemas.md
  politica-sla.md
```

Exemplo de documentos:

- FAQ de problemas de rede;
- FAQ de hardware;
- FAQ de sistemas internos;
- política de SLA.

---

### 4.4 `support-rules-mcp-server`

**Papel:** MCP Server criado pelo aluno.

Responsabilidades:

- expor ferramentas consultáveis pelo `ai-support-service`;
- manter regras simples de atendimento;
- desacoplar regras externas da lógica do chatbot/IA.

Ferramentas planejadas:

```text
getSupportProcedure(category)
getEscalationRule(priority)
getAllowedActions(category)
```

Exemplo de uso:

```text
Categoria: REDE
Tool MCP: getSupportProcedure("REDE")
Resposta: "Verificar adaptador Wi-Fi, autenticação institucional e disponibilidade da rede."
```

Esse componente pode ser mantido pequeno. Ele existe principalmente para demonstrar o conceito de MCP e desacoplamento.

---

### 4.5 `sla-function-service`

**Papel:** função serverless usando Spring Cloud Function.

Responsabilidades:

- receber categoria e prioridade;
- calcular SLA;
- devolver equipe responsável;
- ser stateless;
- ter lógica curta e bem delimitada.

Função planejada:

```text
calculateSla
```

Entrada:

```json
{
  "category": "REDE",
  "priority": "MEDIA"
}
```

Saída:

```json
{
  "slaHours": 8,
  "supportTeam": "Suporte de Redes"
}
```

Essa função cumpre o requisito de serviço implementado com conceito serverless.

---

### 4.6 `config-server`

**Papel:** configuração centralizada.

Responsabilidades:

- fornecer configuração para todos os serviços;
- armazenar rotas do Gateway;
- armazenar parâmetros do Resilience4j;
- armazenar rate limit;
- armazenar mensagem de fallback;
- permitir alteração em tempo de execução.

Configuração alterável em runtime sugerida:

```yaml
smartdesk:
  fallback:
    message: "Seu chamado foi registrado e será analisado manualmente."
  ai:
    enabled: true
```

Demonstração:

1. alterar `smartdesk.fallback.message` no config repo;
2. chamar `/actuator/refresh` no serviço;
3. derrubar o serviço de IA;
4. mostrar que o fallback mudou sem rebuild.

---

### 4.7 `eureka-server-a` e `eureka-server-b`

**Papel:** service discovery em modo cluster.

Responsabilidades:

- registrar instâncias dos serviços;
- permitir descoberta dinâmica;
- permitir Gateway rotear usando `lb://service-name`;
- demonstrar alta disponibilidade do serviço de descoberta.

Portas sugeridas:

```text
eureka-server-a: 8761
eureka-server-b: 8762
```

---

### 4.8 Observabilidade

Stack planejada:

```text
Spring Boot Actuator
Micrometer
Prometheus
Grafana
```

Métricas importantes:

- estado do circuit breaker;
- número de chamadas com sucesso;
- número de chamadas com falha;
- tempo de resposta;
- throughput;
- taxa de erro;
- uso de memória;
- status dos serviços;
- requisições no Gateway.

Painéis mínimos no Grafana:

1. **Visão geral dos serviços**
   - UP/DOWN;
   - latência média;
   - taxa de erro.

2. **Circuit Breakers**
   - estado: CLOSED, OPEN, HALF_OPEN;
   - chamadas bem-sucedidas;
   - chamadas com falha;
   - chamadas rejeitadas.

3. **Carga**
   - throughput;
   - tempo médio;
   - p95/p99, se disponível.

---

## 5. Stack sugerida

A escolha abaixo prioriza versões mais novas do ecossistema Spring, mantendo compatibilidade entre Spring Boot 4, Spring Framework 7, Spring Cloud 2025.1.x e Spring AI 2.x.

| Categoria | Tecnologia | Versão sugerida |
|---|---:|---:|
| Linguagem | Java | 25 |
| Build | Maven | 3.9.x |
| Framework base | Spring Boot | 4.0.7 |
| Framework base | Spring Framework | 7.x, gerenciado pelo Spring Boot |
| Spring Cloud BOM | Spring Cloud | 2025.1.2 |
| Configuração centralizada | Spring Cloud Config | 5.0.4 |
| API Gateway | Spring Cloud Gateway | 5.0.2 |
| Service Discovery | Spring Cloud Netflix Eureka | 5.0.2 |
| Serverless Function | Spring Cloud Function | 5.0.3 |
| GraphQL | Spring for GraphQL | versão gerenciada pelo Spring Boot 4.0.7 |
| IA | Spring AI | 2.0.0 |
| Resiliência | Resilience4j Spring Boot 4 | 2.4.0 |
| Métricas | Spring Boot Actuator | versão gerenciada pelo Spring Boot 4.0.7 |
| Métricas | Micrometer Prometheus Registry | versão gerenciada pelo Spring Boot 4.0.7 |
| Rate limit | Redis | 8.x |
| Observabilidade | Prometheus | 3.12.0 |
| Dashboard | Grafana | 12.x |
| Teste de carga | Apache JMeter | 5.6.3 |
| Containerização | Docker Compose | 2.x |

Observação: em Maven, a forma mais limpa é fixar explicitamente as versões dos BOMs no `pom.xml` pai e deixar as dependências internas serem resolvidas por esses BOMs. Para bibliotecas fora dos BOMs, como Resilience4j, fixar a versão diretamente.

---

## 6. Comunicação entre componentes

| Origem | Destino | Protocolo | Observação |
|---|---|---|---|
| JMeter/Cliente | Gateway | HTTP/TCP | entrada externa |
| Gateway | Ticket Orchestrator | HTTP/TCP via Eureka | `lb://ticket-orchestrator-service` |
| Ticket Orchestrator | AI Support Service | GraphQL sobre HTTP/TCP | chamada protegida por Resilience4j |
| Ticket Orchestrator | SLA Function Service | HTTP/TCP | função serverless |
| AI Support Service | LLM | HTTP/TCP | OpenAI/Gemini/Claude/etc. |
| AI Support Service | MCP Server | MCP | consulta de ferramentas |
| Serviços | Config Server | HTTP/TCP | configuração centralizada |
| Serviços | Eureka | HTTP/TCP | registro e descoberta |
| Prometheus | Serviços | HTTP/TCP | scrape de `/actuator/prometheus` |
| Grafana | Prometheus | HTTP/TCP | dashboards |

---

## 7. Fluxo principal da demonstração

### 7.1 Cenário normal

1. Subir todos os serviços.
2. Abrir Eureka e mostrar serviços registrados.
3. Abrir Grafana.
4. Executar JMeter com mais de 5 usuários simultâneos e abaixo do Knee Capacity.
5. Mostrar zero erros no Summary Report.
6. Mostrar circuit breaker em estado `CLOSED`.

### 7.2 Cenário de falha

1. Derrubar uma instância do `ai-support-service`.
2. Continuar execução do JMeter.
3. Mostrar aumento de erros ou aumento de fallback.
4. Mostrar circuit breaker mudando para `OPEN`.
5. Mostrar que o sistema não trava completamente.

### 7.3 Cenário de recuperação

1. Subir novamente a instância do `ai-support-service`.
2. Esperar o Eureka registrar a instância.
3. Mostrar circuit breaker indo para `HALF_OPEN` e depois `CLOSED`.
4. Mostrar redução de erros no JMeter.
5. Mostrar recuperação no Grafana.

---

## 8. Knee Capacity e Usable Capacity

Plano de medição:

| Rodada | Usuários simultâneos | Duração | Objetivo |
|---:|---:|---:|---|
| 1 | 5 | 2 min | baseline |
| 2 | 10 | 2 min | carga leve |
| 3 | 20 | 2 min | carga média |
| 4 | 40 | 2 min | procurar degradação |
| 5 | 80 | 2 min | procurar joelho |
| 6 | 120 | 2 min | confirmar saturação |

Métricas a observar:

- throughput;
- latência média;
- latência p95;
- taxa de erro;
- CPU/memória;
- estado do circuit breaker.

Definições para a apresentação:

```text
Knee Capacity:
ponto em que aumentar usuários não aumenta proporcionalmente o throughput
e a latência começa a crescer rapidamente.

Usable Capacity:
carga segura antes do Knee Capacity, mantendo baixa latência e zero ou quase zero erros.
```

---

## 9. Requisitos do trabalho mapeados

| Requisito | Como será atendido |
|---|---|
| Java Spring | Todos os serviços em Spring Boot |
| Microserviços | Orchestrator, AI Service, Function Service e componentes de infraestrutura |
| Twelve-Factor | Config externa, logs, port binding, processos independentes, dependências explícitas |
| Pelo menos 3 microserviços | `ticket-orchestrator`, `ai-support`, `sla-function` |
| Serviço AI-powered | `ai-support-service` |
| Função serverless | `sla-function-service` |
| Gateway | `gateway-service` |
| Config Server | `config-server` |
| Eureka | `eureka-server-a` e `eureka-server-b` |
| Eureka cluster | duas instâncias peer-to-peer |
| GraphQL | Orchestrator chama AI Service via GraphQL |
| Resilience4j | Circuit Breaker, Retry, TimeLimiter, Bulkhead |
| Retry no Gateway | filtro Retry no Spring Cloud Gateway |
| Circuit Breaker no serviço de IA | Orchestrator protegendo chamada ao AI Service |
| Rate Limit | Gateway com RedisRateLimiter |
| Grafana com Circuit Breakers | métricas Resilience4j via Prometheus |
| Configuração alterável em runtime | Config Server + `/actuator/refresh` |
| JMeter | teste no endpoint `/api/tickets/analyze` |
| Knee/Usable Capacity | medição progressiva de carga |
| Chat Memory | Spring AI Chat Memory por `conversationId` |
| RAG | documentos locais em `rag-docs` |
| MCP | `support-rules-mcp-server` |

---

## 10. Escopo mínimo viável

Para não complicar demais, o MVP deve ter apenas um fluxo de negócio:

```text
Analisar chamado de suporte
```

Não implementar no MVP:

- login;
- cadastro completo de usuários;
- tela web sofisticada;
- banco relacional obrigatório;
- múltiplos tipos de chamados persistidos;
- filas/eventos reais;
- autenticação OAuth2.

Implementar apenas se sobrar tempo:

- frontend simples;
- persistência dos chamados;
- segundo MCP;
- tracing distribuído;
- autenticação no Gateway.

---

## 11. Ordem recomendada de desenvolvimento

### Etapa 1 — Infraestrutura básica

- Config Server;
- Eureka cluster;
- Gateway;
- Actuator em todos os serviços.

Critério de pronto:

```text
Todos os serviços sobem e aparecem no Eureka.
```

### Etapa 2 — Fluxo sem IA real

- `ticket-orchestrator-service`;
- `ai-support-service` com resposta fake via GraphQL;
- `sla-function-service`.

Critério de pronto:

```text
Gateway → Orchestrator → AI fake → Function → resposta final.
```

### Etapa 3 — Resiliência

- Circuit Breaker;
- Retry;
- TimeLimiter;
- Bulkhead;
- fallback.

Critério de pronto:

```text
Derrubar o AI Service não derruba o Orchestrator.
```

### Etapa 4 — Observabilidade

- Prometheus;
- Grafana;
- dashboard de circuit breakers;
- dashboard de latência e erro.

Critério de pronto:

```text
Grafana mostra estado do circuit breaker.
```

### Etapa 5 — Spring AI

- integrar modelo LLM;
- criar prompt;
- adicionar Chat Memory;
- adicionar RAG;
- adicionar MCP.

Critério de pronto:

```text
AI Service classifica chamado usando contexto, RAG e regra MCP.
```

### Etapa 6 — JMeter

- criar plano de teste;
- medir Knee Capacity;
- definir Usable Capacity;
- preparar roteiro de apresentação.

Critério de pronto:

```text
Summary Report começa com zero erros em carga segura.
```

---

## 12. Decisões de simplificação

Para manter o projeto viável:

1. **Sem banco relacional obrigatório no início.**
   - Chat Memory pode começar em memória.
   - RAG pode usar documentos locais.

2. **Um fluxo principal.**
   - Tudo gira em torno de analisar chamados.

3. **IA com fallback.**
   - Se a API externa falhar, o sistema continua demonstrável.

4. **MCP pequeno.**
   - O MCP existe para demonstrar desacoplamento, não para ser um sistema complexo.

5. **Function Service simples.**
   - Só calcula SLA.

6. **Gateway centraliza preocupação transversal.**
   - rate limit, retry e roteamento.

---

## 13. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| API de IA falhar ou ficar lenta | fallback + circuit breaker + opção de resposta simulada |
| RAG complicar demais | usar documentos pequenos e locais |
| MCP complicar demais | criar MCP com poucas tools |
| JMeter gerar carga alta demais | medir antes e usar carga abaixo do Knee Capacity |
| Grafana não mostrar métrica | validar `/actuator/prometheus` cedo |
| Eureka cluster consumir tempo | implementar cedo, antes da lógica de IA |
| Rate limit com Redis dar problema | deixar Redis no Docker Compose desde o começo |

---

## 14. Roteiro resumido da apresentação

1. Explicar o problema: triagem manual de chamados é lenta.
2. Mostrar arquitetura distribuída.
3. Mostrar Eureka com serviços registrados.
4. Mostrar Config Server centralizando configurações.
5. Mostrar Gateway roteando para o Orchestrator.
6. Executar chamada normal de análise de chamado.
7. Mostrar GraphQL entre Orchestrator e AI Service.
8. Mostrar Spring AI usando Chat Memory, RAG e MCP.
9. Mostrar Function Service calculando SLA.
10. Rodar JMeter com carga segura.
11. Mostrar Grafana com zero erros e circuit breaker fechado.
12. Derrubar instância do AI Service.
13. Mostrar aumento de erro/fallback e circuit breaker aberto.
14. Subir instância novamente.
15. Mostrar recuperação e circuit breaker fechando.
16. Explicar Knee Capacity e Usable Capacity.

---

## 15. Conclusão

O SmartDesk AI é uma boa escolha porque permite cumprir todos os requisitos com uma regra de negócio simples. A complexidade fica concentrada onde o professor quer avaliar: arquitetura distribuída, gateway, service discovery, config externa, GraphQL, serverless, resiliência, observabilidade, Spring AI, RAG, Chat Memory, MCP e teste de carga.
