# Módulo Financeiro — Grupo 8

API e front do módulo Financeiro do Projeto Integrador 2026, conforme a **EOR v1.0 (Fase 2)** e o
**Contrato de Integração** da plataforma (`infra-integrador-2026`). Código do módulo: `financeiro`.

| | |
|---|---|
| API | Java 21 · Spring Boot 3.5 · porta **8085** · rotas em `/api/financeiro` e `/public/financeiro` |
| Front | React + Vite + Tailwind v4 (Design System da plataforma) · porta **3005** · servido em `/modulos/financeiro/` e embutido na casca por iframe |
| Banco | PostgreSQL, schema `financeiro`; migrations com `own_financeiro`, aplicação com `usr_financeiro` |
| Mensageria | RabbitMQ: publica em `financeiro.eventos`, pede timeline/notificação/e-mail em `identity.entrada` |
| Contratos | `infra-integrador-2026/contratos/financeiro.yaml` (OpenAPI 0.4.0), `financeiro.asyncapi.yaml`, `financeiro.views.md` |

## O que está implementado

| RF | Requisito | Onde |
|---|---|---|
| RF-01 | Contas a receber: lançar, consultar, editar (RN-05), cancelar (exclusão lógica) | `cobrancas/` |
| RF-02 | Contas a pagar: lançar, aprovar (permissão distinta), registrar pagamento só se aprovada | `contaspagar/` |
| RF-03 | Recorrência: valor sempre dos itens, uma cobrança por competência, aditivo só para frente, job diário, consumidor de `contratos.contrato.assinado`/`cancelado` | `recorrencias/`, `eventos/ProcessadorContratos` |
| RF-04 | PIX (BR Code + QR), boleto e link de cartão por um adaptador de provedor (simulado) | `pagamentos/`, `pagamentos/provedor/` |
| RF-05 | Webhook público com assinatura HMAC, limite por IP (429) e idempotência por evento | `webhooks/` |
| RF-06 | Régua configurável, job diário (atraso + disparos), um disparo por cobrança e etapa | `regua/` |
| RF-07 | Inadimplência com aging, histórico da empresa e promessas de pagamento | `inadimplencia/` |
| RF-08 | MRR decomposto (Novo, Expansion, Contraction, Churned, Net New), ARR, churn, home | `indicadores/` |
| RF-09 | Busca global (até 5, filtrada por permissão) | `busca/` |
| RF-10 | Status financeiro da empresa, individual e em lote (até 100), sem valores | `integracoes/` |
| RF-11 | Exportação CSV, XLSX e PDF | `relatorios/` |
| RF-12 | Baixa manual (transferência), total ou parcial | `cobrancas/CobrancaService#baixaManual` |
| RF-13 | Auditoria só de inserção (gatilho no banco + `REVOKE`) e tela de consulta | `auditoria/`, migration |

Fora do escopo desta fase (EOR §9): conciliação OFX, contabilidade, comissões, provedor real, cartão recorrente.

## Rodar na máquina

Pré-requisitos: JDK 21, Maven 3.9+, Node 22 e PostgreSQL (ou `docker compose up -d`, que sobe
PostgreSQL com os bancos `erp`/`erp_test` e um RabbitMQ).

```bash
cp .env.example .env
mvn -q compile exec:java@chaves     # par RSA de desenvolvimento em keys/ (faz o papel do identity)
mvn spring-boot:run                 # aplica a migration e sobe em http://localhost:8085
cd frontend && cp .env.example .env && npm install && npm run dev   # http://localhost:3005/modulos/financeiro/
```

Sem o identity no ar, gere um token com `mvn -q compile exec:java@token -Dexec.args="<tenant> ADMINISTRADOR"` e cole na
tela de entrada do front (perfis: ADMINISTRADOR, GESTOR, FINANCEIRO, CONTABILIDADE, VENDEDOR). O token é
assinado com `keys/auth-private.pem`, que faz o papel do identity.
Com o identity no ar, deixe `JWT_PUBLIC_KEY_PATH` vazio: a API valida pelo JWKS.

Sem RabbitMQ, a API funciona normalmente: os eventos ficam `PENDENTE` no outbox e são publicados quando
o broker voltar (três tentativas, com espera de 1, 5 e 25 minutos).

> **Banco criado pela versão anterior (v0.1):** a estrutura mudou por completo para seguir o DER. Apague
> o schema antigo antes de subir: `psql -U financeiro -d erp -c "DROP SCHEMA financeiro CASCADE"`.

## Rodar na plataforma

No `infra-integrador-2026`: `docker compose --profile plataforma --profile modulos up -d financeiro financeiro-front`.
As imagens `ghcr.io/joao-victor78/financeiro` e `financeiro-front` são publicadas a cada push na `main`
(`.github/workflows/ci.yml`).

## Testes

```bash
mvn verify    # 56 testes: cada critério de aceite da EOR (DADO QUE / QUANDO / ENTÃO), 401, 403, isolamento entre tenants
```

Os testes usam PostgreSQL real (`TEST_DB_URL`, padrão `jdbc:postgresql://localhost:5432/erp_test`) e
**apagam o schema `financeiro` desse banco** — por segurança, só aceitam banco terminado em `_test`.
RabbitMQ e CRM são simulados; nenhum outro módulo precisa estar no ar (Contrato §14.5).

## Decisões tomadas onde a documentação deixa em aberto

| Ponto | Decisão |
|---|---|
| PD-8 juros e multa | Campos editáveis na cobrança; sem cálculo automático até a regra ser definida |
| RN-63 permissão da baixa manual | Reaproveita `financeiro.contareceber.editar` |
| RF-13 permissão da auditoria | Nova: `financeiro.auditoria.ver` (ADMINISTRADOR) |
| Vencimento da cobrança recorrente | Dia de geração + `diasParaVencimento` (padrão 10) |
| Destinatário do e-mail da régua | Primeiro contato com e-mail da empresa no CRM, com token de serviço; sem ele, o disparo fica `SEM_DESTINATARIO` e só vai para a timeline |
| Categoria e centro de custo | Texto livre (PD-5: sem cadastro) |
| Tabela de idempotência | `eventos_processados`, como manda o Contrato §9.7 (o DER chama de `eventos_consumidos`) |
