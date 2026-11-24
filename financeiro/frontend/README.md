# Front do Financeiro

React + Vite + TypeScript, com Tailwind v4 e o tema da plataforma (Design System Centinela), ícones
`lucide-react` e fonte Inter — o mesmo padrão do `exemplo-modulo/front` e do `ui/README.md` do
`infra-integrador-2026`. Os componentes de `src/components/ui/index.tsx` são copiados da casca
(`plataforma-integrador-2026-2/casca/src/components/ui/index.tsx`) e o tema de `src/tema/plataforma.css`
é o `ui/plataforma.css` da infra: ao atualizar, copie de novo os dois arquivos, sem editar.

Servido em `/modulos/financeiro/` (porta 3005) e embutido pela casca em iframe, em `/app/financeiro/...`.

## Sessão (Contrato §12)

- **Embutido:** `src/main.tsx` chama `iniciarSessao()` antes do primeiro render — ouve as mensagens da casca e
  só então avisa `modulo:pronto`. A sessão (`plataforma:sessao`, `plataforma:token`, `plataforma:tema`) fica em
  memória, fora dos componentes; nunca em `localStorage`. O módulo informa a altura (`modulo:altura`), a tela
  interna (`modulo:navegar`, rota relativa: `/cobrancas`), o token vencido (`modulo:token-expirado`) e os avisos
  de sucesso e erro (`modulo:notificar`), que a casca mostra.
- **Aberto direto (desenvolvimento):** entrada com usuário de teste do identity ou com um token de
  `mvn -q compile exec:java@token -Dexec.args="<tenant> ADMINISTRADOR"` (pasta da API, sem o identity no ar).

As chamadas vão para `/api/financeiro/...` na mesma origem, com `Authorization: Bearer`. Em desenvolvimento,
o proxy do Vite faz o papel do gateway (`VITE_GATEWAY`, `VITE_API_FINANCEIRO` no `.env`).

## Telas

| Rota | Permissão | Conteúdo |
|---|---|---|
| `/dashboard` | `financeiro.dashboard.ver` | Home do financeiro e MRR por período |
| `/cobrancas` e `/cobrancas/{id}` | `financeiro.contareceber.ver` | Lista, lançamento, detalhe (PIX/boleto/cartão, simulação do provedor, baixa manual, promessas, régua), exportação |
| `/contas-pagar` | `financeiro.contapagar.ver` | Lançamento, aprovação e pagamento |
| `/recorrencias` | `financeiro.recorrencia.ver` | Regras com itens, aditivo e geração do ciclo |
| `/regua` | `financeiro.regua.ver` | Etapas da régua e execução do job |
| `/inadimplencia` | `financeiro.inadimplencia.ver` | Aging, empresas em atraso e histórico |
| `/auditoria` | `financeiro.auditoria.ver` | Registro das alterações |

## Comandos

```bash
cp .env.example .env && npm install
npm run dev        # http://localhost:3005/modulos/financeiro/
npm run build      # typecheck + dist/, servido pelo nginx da imagem (Dockerfile)
```
