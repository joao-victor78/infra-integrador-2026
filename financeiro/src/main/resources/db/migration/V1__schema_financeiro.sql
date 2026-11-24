-- Schema do módulo Financeiro (Grupo 8), alinhado ao DER e à EOR v1.0 (Fase 2).
-- Roda como own_financeiro (Contrato §7.1). Toda tabela de negócio tem as sete colunas da §7.2:
-- id, tenant_id, created_at, updated_at, deleted_at, created_by, updated_by.
-- Referências a outros módulos (empresa_id, contrato_id, servico_id, comprovante_id) são só UUID,
-- sem chave estrangeira entre schemas (§7.1). FKs existem apenas dentro deste schema.
-- auditoria, eventos_outbox e eventos_processados têm estrutura própria (não são de negócio).

-- ------------------------------------------------------------------ recorrência (RF-03)

CREATE TABLE financeiro.recorrencias (
  id                   uuid PRIMARY KEY,
  tenant_id            uuid NOT NULL,
  empresa_id           uuid NOT NULL,                     -- CRM
  contrato_id          uuid,                              -- Contratos; nulo enquanto a regra nasce manual
  descricao            varchar(200),
  periodicidade        varchar(12) NOT NULL CHECK (periodicidade IN ('MENSAL','TRIMESTRAL','ANUAL')),
  dia_geracao          smallint NOT NULL CHECK (dia_geracao BETWEEN 1 AND 28),          -- RN-16
  dias_para_vencimento smallint NOT NULL DEFAULT 10 CHECK (dias_para_vencimento BETWEEN 0 AND 60),
  proxima_competencia  char(7) NOT NULL,
  forma_pagamento      varchar(15) CHECK (forma_pagamento IN ('PIX','BOLETO','CARTAO','TRANSFERENCIA')),
  ativa                boolean NOT NULL DEFAULT true,
  vigencia_inicio      date NOT NULL,
  vigencia_fim         date,
  created_at           timestamptz NOT NULL,
  updated_at           timestamptz NOT NULL,
  deleted_at           timestamptz,
  created_by           uuid,
  updated_by           uuid
);
CREATE INDEX idx_recorrencias_tenant ON financeiro.recorrencias (tenant_id, ativa, proxima_competencia) WHERE deleted_at IS NULL;
-- Um contrato gera no máximo uma regra (o consumidor de contrato assinado também é idempotente por evento).
CREATE UNIQUE INDEX ux_recorrencias_contrato ON financeiro.recorrencias (tenant_id, contrato_id)
  WHERE contrato_id IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE financeiro.recorrencia_itens (
  id              uuid PRIMARY KEY,
  tenant_id       uuid NOT NULL,
  recorrencia_id  uuid NOT NULL REFERENCES financeiro.recorrencias (id),
  servico_id      uuid NOT NULL,                          -- Produtos e Serviços
  descricao       varchar(200) NOT NULL,
  quantidade      numeric(12,3) NOT NULL CHECK (quantidade > 0),
  unidade         varchar(20) NOT NULL,
  valor_unitario  numeric(15,2) NOT NULL CHECK (valor_unitario >= 0),
  franquia        numeric(12,3) CHECK (franquia >= 0),
  vigente_desde   char(7) NOT NULL,                       -- RN-17: aditivo vale da competência em diante
  vigente_ate     char(7),
  created_at      timestamptz NOT NULL,
  updated_at      timestamptz NOT NULL,
  deleted_at      timestamptz,
  created_by      uuid,
  updated_by      uuid
);
CREATE INDEX idx_recorrencia_itens_regra ON financeiro.recorrencia_itens (recorrencia_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_recorrencia_itens_tenant ON financeiro.recorrencia_itens (tenant_id);

-- ------------------------------------------------------------------ contas a receber (RF-01)

CREATE TABLE financeiro.cobrancas (
  id                   uuid PRIMARY KEY,
  tenant_id            uuid NOT NULL,
  empresa_id           uuid NOT NULL,                     -- RN-03: sempre com empresa de origem (CRM)
  contrato_id          uuid,
  recorrencia_id       uuid REFERENCES financeiro.recorrencias (id),
  competencia          char(7) NOT NULL,
  descricao            varchar(200),
  data_vencimento      date NOT NULL,
  valor                numeric(15,2) NOT NULL CHECK (valor > 0),
  desconto             numeric(15,2) NOT NULL DEFAULT 0 CHECK (desconto >= 0),
  juros                numeric(15,2) NOT NULL DEFAULT 0 CHECK (juros >= 0),
  multa                numeric(15,2) NOT NULL DEFAULT 0 CHECK (multa >= 0),
  valor_pago           numeric(15,2) NOT NULL DEFAULT 0 CHECK (valor_pago >= 0),
  forma_pagamento      varchar(15) CHECK (forma_pagamento IN ('PIX','BOLETO','CARTAO','TRANSFERENCIA')),
  status               varchar(12) NOT NULL CHECK (status IN
                         ('ABERTO','AGENDADO','PAGO','ATRASADO','CANCELADO','ESTORNADO','PARCIAL')),  -- RN-02
  atraso_publicado_em  timestamptz,                       -- financeiro.pagamento.atrasado sai uma vez só
  created_at           timestamptz NOT NULL,
  updated_at           timestamptz NOT NULL,
  deleted_at           timestamptz,
  created_by           uuid,
  updated_by           uuid
);
CREATE INDEX idx_cobrancas_tenant_venc ON financeiro.cobrancas (tenant_id, data_vencimento) WHERE deleted_at IS NULL;
CREATE INDEX idx_cobrancas_tenant_empresa ON financeiro.cobrancas (tenant_id, empresa_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_cobrancas_tenant_status ON financeiro.cobrancas (tenant_id, status) WHERE deleted_at IS NULL;

CREATE TABLE financeiro.recorrencia_geracoes (
  id              uuid PRIMARY KEY,
  tenant_id       uuid NOT NULL,
  recorrencia_id  uuid NOT NULL REFERENCES financeiro.recorrencias (id),
  competencia     char(7) NOT NULL,
  cobranca_id     uuid NOT NULL REFERENCES financeiro.cobrancas (id),
  origem          varchar(6) NOT NULL CHECK (origem IN ('JOB','MANUAL')),
  created_at      timestamptz NOT NULL,
  updated_at      timestamptz NOT NULL,
  deleted_at      timestamptz,
  created_by      uuid,
  updated_by      uuid,
  CONSTRAINT ux_recorrencia_competencia UNIQUE (recorrencia_id, competencia)                 -- RN-15
);
CREATE INDEX idx_recorrencia_geracoes_tenant ON financeiro.recorrencia_geracoes (tenant_id);

-- ------------------------------------------------------------------ pagamentos (RF-04, RF-05, RF-12)

CREATE TABLE financeiro.pagamentos (
  id                      uuid PRIMARY KEY,
  tenant_id               uuid NOT NULL,
  cobranca_id             uuid NOT NULL REFERENCES financeiro.cobrancas (id),
  meio                    varchar(15) NOT NULL CHECK (meio IN ('PIX','BOLETO','CARTAO','TRANSFERENCIA')),
  status                  varchar(12) NOT NULL CHECK (status IN
                            ('PENDENTE','PROCESSANDO','CONFIRMADO','ESTORNADO','FALHOU','EXPIRADO')),   -- RN-21
  valor                   numeric(15,2) NOT NULL CHECK (valor > 0),
  provedor                varchar(40),
  referencia_provedor     varchar(100),                   -- RN-22: só a referência, nunca dado de cartão
  pix_copia_cola          text,
  boleto_linha_digitavel  varchar(60),
  boleto_url              varchar(500),
  link_pagamento          varchar(500),
  expira_em               timestamptz,
  confirmado_em           timestamptz,
  comprovante_id          uuid,                           -- RN-62: documento no serviço do Grupo 5
  created_at              timestamptz NOT NULL,
  updated_at              timestamptz NOT NULL,
  deleted_at              timestamptz,
  created_by              uuid,
  updated_by              uuid
);
CREATE INDEX idx_pagamentos_cobranca ON financeiro.pagamentos (cobranca_id);
CREATE INDEX idx_pagamentos_tenant ON financeiro.pagamentos (tenant_id, status, confirmado_em);
CREATE UNIQUE INDEX ux_pagamentos_referencia ON financeiro.pagamentos (provedor, referencia_provedor)
  WHERE referencia_provedor IS NOT NULL;

CREATE TABLE financeiro.webhook_eventos (
  id                  uuid PRIMARY KEY,
  tenant_id           uuid NOT NULL,                      -- o da cobrança citada (RN-26)
  provedor            varchar(40) NOT NULL,
  evento_id_provedor  varchar(100) NOT NULL,
  tipo                varchar(40) NOT NULL,
  cobranca_id         uuid NOT NULL REFERENCES financeiro.cobrancas (id),
  pagamento_id        uuid REFERENCES financeiro.pagamentos (id),
  assinatura_valida   boolean NOT NULL,
  resultado           varchar(12) NOT NULL CHECK (resultado IN ('PROCESSADO','IGNORADO','REJEITADO')),
  recebido_em         timestamptz NOT NULL,
  created_at          timestamptz NOT NULL,
  updated_at          timestamptz NOT NULL,
  deleted_at          timestamptz,
  created_by          uuid,
  updated_by          uuid,
  CONSTRAINT ux_webhook_evento UNIQUE (provedor, evento_id_provedor)                          -- RN-28
);
CREATE INDEX idx_webhook_eventos_tenant ON financeiro.webhook_eventos (tenant_id);

CREATE TABLE financeiro.promessas_pagamento (
  id              uuid PRIMARY KEY,
  tenant_id       uuid NOT NULL,
  cobranca_id     uuid NOT NULL REFERENCES financeiro.cobrancas (id),
  data_prometida  date NOT NULL,
  valor           numeric(15,2) NOT NULL CHECK (valor > 0),
  observacao      varchar(500),
  status          varchar(10) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE','CUMPRIDA','QUEBRADA')),
  created_at      timestamptz NOT NULL,
  updated_at      timestamptz NOT NULL,
  deleted_at      timestamptz,
  created_by      uuid,
  updated_by      uuid
);
CREATE INDEX idx_promessas_cobranca ON financeiro.promessas_pagamento (tenant_id, cobranca_id) WHERE deleted_at IS NULL;

-- ------------------------------------------------------------------ contas a pagar (RF-02)

CREATE TABLE financeiro.contas_pagar (
  id                    uuid PRIMARY KEY,
  tenant_id             uuid NOT NULL,
  fornecedor_nome       varchar(150) NOT NULL,            -- RN-10: cadastro simplificado na própria conta
  fornecedor_documento  varchar(18),
  categoria             varchar(60),                      -- RN-13: opcionais até PD-5
  centro_custo          varchar(60),
  competencia           char(7) NOT NULL,
  data_vencimento       date NOT NULL,
  valor                 numeric(15,2) NOT NULL CHECK (valor > 0),
  documento             varchar(60),
  observacoes           varchar(500),
  status                varchar(10) NOT NULL CHECK (status IN ('PENDENTE','APROVADA','PAGA','CANCELADA')),
  aprovado_por          uuid,
  aprovado_em           timestamptz,
  data_pagamento        date,
  valor_pago            numeric(15,2) CHECK (valor_pago > 0),
  created_at            timestamptz NOT NULL,
  updated_at            timestamptz NOT NULL,
  deleted_at            timestamptz,
  created_by            uuid,
  updated_by            uuid
);
CREATE INDEX idx_contas_pagar_tenant ON financeiro.contas_pagar (tenant_id, status, data_vencimento) WHERE deleted_at IS NULL;

-- ------------------------------------------------------------------ régua de cobrança (RF-06)

CREATE TABLE financeiro.regua_etapas (
  id                 uuid PRIMARY KEY,
  tenant_id          uuid NOT NULL,
  deslocamento_dias  smallint NOT NULL,                   -- negativo = antes do vencimento
  acao               varchar(60),
  canal              varchar(20) NOT NULL CHECK (canal IN ('EMAIL','WHATSAPP','NOTIFICACAO_INTERNA')),  -- RN-34
  modelo_mensagem    text NOT NULL,
  ativa              boolean NOT NULL DEFAULT true,
  created_at         timestamptz NOT NULL,
  updated_at         timestamptz NOT NULL,
  deleted_at         timestamptz,
  created_by         uuid,
  updated_by         uuid
);
CREATE UNIQUE INDEX ux_regua_etapas_deslocamento ON financeiro.regua_etapas (tenant_id, deslocamento_dias)
  WHERE deleted_at IS NULL;

CREATE TABLE financeiro.regua_disparos (
  id            uuid PRIMARY KEY,
  tenant_id     uuid NOT NULL,
  cobranca_id   uuid NOT NULL REFERENCES financeiro.cobrancas (id),
  etapa_id      uuid NOT NULL REFERENCES financeiro.regua_etapas (id),
  disparado_em  timestamptz NOT NULL,
  canal         varchar(20) NOT NULL,
  situacao      varchar(20) NOT NULL CHECK (situacao IN ('SOLICITADO','SEM_DESTINATARIO','CANAL_INDISPONIVEL')),
  created_at    timestamptz NOT NULL,
  updated_at    timestamptz NOT NULL,
  deleted_at    timestamptz,
  created_by    uuid,
  updated_by    uuid,
  CONSTRAINT ux_regua_disparo UNIQUE (cobranca_id, etapa_id)                                  -- RN-32
);
CREATE INDEX idx_regua_disparos_tenant ON financeiro.regua_disparos (tenant_id, cobranca_id);

-- ------------------------------------------------------------------ auditoria (RF-13)

CREATE TABLE financeiro.auditoria (
  id              uuid PRIMARY KEY,
  tenant_id       uuid NOT NULL,
  usuario_id      uuid,                                   -- nulo em rotina automática
  servico         varchar(60),                            -- svc:{clientId} em chamada com token de serviço
  ip              varchar(45),
  data_hora       timestamptz NOT NULL,
  acao            varchar(40) NOT NULL,
  entidade        varchar(40) NOT NULL,
  registro_id     uuid NOT NULL,
  valor_anterior  jsonb,
  valor_novo      jsonb
);
CREATE INDEX idx_auditoria_registro ON financeiro.auditoria (tenant_id, entidade, registro_id, data_hora DESC);
CREATE INDEX idx_auditoria_data ON financeiro.auditoria (tenant_id, data_hora DESC);

-- RN-67: a aplicação não altera nem apaga auditoria. O gatilho vale para qualquer usuário de
-- aplicação; o REVOKE abaixo tira a permissão de usr_financeiro no ambiente da plataforma.
CREATE FUNCTION financeiro.auditoria_somente_insercao() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'auditoria aceita somente inserção (RN-67)' USING ERRCODE = 'insufficient_privilege';
END $$;

CREATE TRIGGER trg_auditoria_imutavel BEFORE UPDATE OR DELETE ON financeiro.auditoria
  FOR EACH ROW EXECUTE FUNCTION financeiro.auditoria_somente_insercao();
CREATE TRIGGER trg_auditoria_sem_truncate BEFORE TRUNCATE ON financeiro.auditoria
  FOR EACH STATEMENT EXECUTE FUNCTION financeiro.auditoria_somente_insercao();

-- ------------------------------------------------------------------ mensageria (Contrato §9.7)

-- Outbox: o evento é gravado na mesma transação do fato e publicado depois do commit.
CREATE TABLE financeiro.eventos_outbox (
  id            uuid PRIMARY KEY,                         -- é também o id do envelope (idempotência)
  tenant_id     uuid NOT NULL,
  routing_key   varchar(80) NOT NULL,
  payload       jsonb NOT NULL,
  status        varchar(10) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE','PUBLICADO','FALHOU')),
  tentativas         smallint NOT NULL DEFAULT 0,       -- três falhas: FALHOU (fica para reprocessar)
  ultimo_erro        varchar(500),
  criado_em          timestamptz NOT NULL,
  proxima_tentativa  timestamptz NOT NULL,
  publicado_em       timestamptz
);
CREATE INDEX idx_outbox_pendentes ON financeiro.eventos_outbox (proxima_tentativa) WHERE status = 'PENDENTE';

-- Consumidor idempotente: o id do evento é gravado na mesma transação do efeito.
CREATE TABLE financeiro.eventos_processados (
  evento_id      uuid PRIMARY KEY,
  tipo           varchar(100) NOT NULL,
  origem         varchar(40),
  processado_em  timestamptz NOT NULL DEFAULT now()
);

-- ------------------------------------------------------------------ view pública (Contrato §9.8)

-- Para o motor de relatórios do Grupo 3 (RN-59). Documentada em contratos/financeiro.views.md.
CREATE VIEW financeiro.vw_pub_cobrancas AS
  SELECT id, tenant_id, empresa_id, contrato_id, competencia, data_vencimento,
         valor, desconto, juros, multa, valor_pago, forma_pagamento, status, created_at
    FROM financeiro.cobrancas
   WHERE deleted_at IS NULL;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'usr_financeiro') THEN
    REVOKE UPDATE, DELETE, TRUNCATE ON financeiro.auditoria FROM usr_financeiro;
    REVOKE UPDATE, DELETE, TRUNCATE ON financeiro.eventos_processados FROM usr_financeiro;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'usr_chamados') THEN
    GRANT USAGE  ON SCHEMA financeiro          TO usr_chamados;
    GRANT SELECT ON financeiro.vw_pub_cobrancas TO usr_chamados;
  END IF;
END $$;
