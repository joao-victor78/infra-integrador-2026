import { useEffect, useState } from 'react'
import { BellRing, Play, Plus, Save, Trash2 } from 'lucide-react'
import { Button, Checkbox, EmptyState, Input, PageHeader, Select } from '@/components/ui'
import { Conteudo, linha, Tabela, Td, Th } from '@/components/modulo'
import { useAcao, useDados, usePermissao } from '@/lib/hooks'
import { CANAIS, etapa } from '@/lib/formato'
import { api } from '@/plataforma/api'

interface Etapa {
  id?: string
  diasOffset: number | string
  acao?: string | null
  canal: string
  templateMensagem: string
  ativa: boolean
}

/** Sugestão do §30 (RN-30). A régua é configurável (RN-31): nada disso é fixo no código. */
const SUGERIDAS: Etapa[] = [
  { diasOffset: -5, acao: 'lembrete', canal: 'EMAIL', templateMensagem: 'Olá! Sua fatura de {valor} vence em {vencimento}.', ativa: true },
  { diasOffset: -1, acao: 'lembrete', canal: 'EMAIL', templateMensagem: 'Lembrete: a fatura de {valor} vence amanhã ({vencimento}).', ativa: true },
  { diasOffset: 0, acao: 'cobrança', canal: 'EMAIL', templateMensagem: 'Sua fatura de {valor} vence hoje.', ativa: true },
  { diasOffset: 1, acao: 'aviso de atraso', canal: 'EMAIL', templateMensagem: 'Não identificamos o pagamento de {saldo}, vencido em {vencimento}.', ativa: true },
  { diasOffset: 5, acao: 'nova cobrança', canal: 'EMAIL', templateMensagem: 'Fatura em atraso há {dias} dias: {saldo}.', ativa: true },
  { diasOffset: 10, acao: 'notificar o financeiro', canal: 'NOTIFICACAO_INTERNA', templateMensagem: 'Cobrança {cobranca} com {dias} dias de atraso.', ativa: true },
  { diasOffset: 15, acao: 'alertar o gestor', canal: 'NOTIFICACAO_INTERNA', templateMensagem: 'Cobrança {cobranca} com 15 dias de atraso: {saldo}.', ativa: true },
  { diasOffset: 30, acao: 'marcar cliente como crítico', canal: 'NOTIFICACAO_INTERNA', templateMensagem: 'Cliente crítico: cobrança {cobranca} com 30 dias de atraso.', ativa: true },
]

interface Execucao { marcadasAtrasadas: number; eventosDeAtraso: number; disparos: number; promessasQuebradas: number }

/** RF-06: régua de cobrança. Pagamento confirmado encerra as etapas seguintes (RN-35). */
export function Regua(_: { detalhe?: string }) {
  const { tem } = usePermissao()
  const pode = tem('financeiro.regua.administrar')
  const regua = useDados<{ etapas: Etapa[] }>('/regua')
  const [etapas, setEtapas] = useState<Etapa[]>([])
  const [execucao, setExecucao] = useState<Execucao | null>(null)
  const { executar, ocupado } = useAcao()

  useEffect(() => {
    if (regua.dados) setEtapas(regua.dados.etapas)
  }, [regua.dados])

  const alterar = (i: number, campo: keyof Etapa, valor: string | boolean) => setEtapas(etapas.map((e, j) => (j === i ? { ...e, [campo]: valor } : e)))
  const salvar = () => executar(() => api<{ etapas: Etapa[] }>('/regua', { method: 'PUT', body: { etapas: etapas.map((e) => ({ ...e, diasOffset: Number(e.diasOffset) })) } }),
    (d) => setEtapas(d.etapas))
  const rodar = () => executar(async () => ({ ...(await api<Execucao>('/regua/executar', { method: 'POST' })), message: 'Job de atraso e régua executado para esta empresa.' }), setExecucao)

  return (
    <>
      <PageHeader title="Régua de cobrança" subtitle="Avisos em relação ao vencimento · o job roda sozinho todo dia às 7h"
        actions={pode && <>
          <Button variant="secondary" size="sm" disabled={ocupado} onClick={rodar}><Play aria-hidden className="size-3.5" /> Executar agora</Button>
          <Button size="sm" loading={ocupado} onClick={salvar}><Save aria-hidden className="size-3.5" /> Salvar régua</Button>
        </>} />
      <div className="flex-1 space-y-4 p-5">
        {execucao && (
          <p className="rounded-xl border border-borda bg-superficie p-3 text-sm text-texto-2">
            Última execução: {execucao.marcadasAtrasadas} marcada(s) como atrasada(s), {execucao.eventosDeAtraso} evento(s) de atraso,{' '}
            {execucao.disparos} aviso(s) disparado(s), {execucao.promessasQuebradas} promessa(s) quebrada(s).
          </p>
        )}
        <p className="text-xs text-texto-3">
          Variáveis do modelo: {'{valor}'}, {'{saldo}'}, {'{vencimento}'}, {'{dias}'}, {'{descricao}'}, {'{cobranca}'}. Nesta fase os canais efetivos são
          e-mail e notificação interna (WhatsApp sem dono, RN-37). Mais de 30 dias de atraso deixa a empresa crítica no status financeiro.
        </p>
        <Conteudo carga={regua.carga} recarregar={regua.recarregar}>
          {() => etapas.length === 0 ? (
            <EmptyState icon={<BellRing aria-hidden />} title="Nenhuma etapa configurada"
              action={pode && <Button variant="outline" onClick={() => setEtapas(SUGERIDAS)}>Usar a régua sugerida</Button>}>
              A régua sugerida segue o §30: D-5, D-1, D0, D+1, D+5, D+10, D+15 e D+30.
            </EmptyState>
          ) : (
            <Tabela cabecalho={<><Th>Etapa</Th><Th>Dias</Th><Th>Ação</Th><Th>Canal</Th><Th>Mensagem</Th><Th>Ativa</Th><Th /></>}>
              {etapas.map((e, i) => (
                <tr key={e.id ?? `nova-${i}`} className={linha}>
                  <Td className="font-semibold text-titulo">{etapa(Number(e.diasOffset))}</Td>
                  <Td><Input type="number" min={-60} max={120} disabled={!pode} value={e.diasOffset} onChange={(x) => alterar(i, 'diasOffset', x.target.value)} className="w-20" aria-label="Dias" /></Td>
                  <Td><Input maxLength={60} disabled={!pode} value={e.acao ?? ''} onChange={(x) => alterar(i, 'acao', x.target.value)} className="min-w-36" aria-label="Ação" /></Td>
                  <Td>
                    <Select disabled={!pode} value={e.canal} onChange={(x) => alterar(i, 'canal', x.target.value)} className="min-w-44" aria-label="Canal">
                      {Object.entries(CANAIS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                    </Select>
                  </Td>
                  <Td><Input required disabled={!pode} value={e.templateMensagem} onChange={(x) => alterar(i, 'templateMensagem', x.target.value)} className="min-w-72" aria-label="Mensagem" /></Td>
                  <Td><Checkbox disabled={!pode} checked={e.ativa} onChange={(x) => alterar(i, 'ativa', x.target.checked)} aria-label="Ativa" /></Td>
                  <Td>{pode && <Button variant="ghost" size="sm" aria-label="Remover etapa" onClick={() => setEtapas(etapas.filter((_, j) => j !== i))}><Trash2 aria-hidden className="size-4" /></Button>}</Td>
                </tr>
              ))}
            </Tabela>
          )}
        </Conteudo>
        {pode && etapas.length > 0 && (
          <Button variant="outline" size="sm" onClick={() => setEtapas([...etapas, { diasOffset: 0, canal: 'EMAIL', templateMensagem: '', ativa: true }])}>
            <Plus aria-hidden className="size-3.5" /> Etapa
          </Button>
        )}
      </div>
    </>
  )
}
