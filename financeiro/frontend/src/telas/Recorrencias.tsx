import { useState, type FormEvent } from 'react'
import { Plus, Repeat, Trash2 } from 'lucide-react'
import { Button, Checkbox, FilterBar, FormField, FormSection, Input, Modal, PageHeader, Pagination, Select } from '@/components/ui'
import { Conteudo, linha, Status, Tabela, Td, Th } from '@/components/modulo'
import { useEmpresas } from '@/lib/empresas'
import { useAcao, useDados, usePermissao } from '@/lib/hooks'
import { brl, competencia, data, FORMAS, PERIODICIDADES } from '@/lib/formato'
import { api, type Pagina } from '@/plataforma/api'
import { CampoUuid } from './Cobrancas'

interface Item {
  servicoId: string
  descricao: string
  quantidade: number | string
  unidade: string
  valorUnitario: number | string
  franquia?: number | string | null
}

interface Regra {
  id: string
  contratoId: string | null
  clienteId: string
  descricao: string | null
  periodicidade: string
  diaGeracao: number
  diasParaVencimento: number
  formaPagamento: string | null
  proximaCompetencia: string
  valorProximoCiclo: number
  itens: Item[]
  ativa: boolean
  vigenciaFim: string | null
}

const itemVazio = (): Item => ({ servicoId: '', descricao: '', quantidade: 1, unidade: 'UN', valorUnitario: '' })

/** RF-03: valor do ciclo sempre dos itens (RN-14); um aditivo vale da próxima geração (RN-17). */
export function Recorrencias(_: { detalhe?: string }) {
  const { tem } = usePermissao()
  const administra = tem('financeiro.recorrencia.administrar')
  const [filtro, setFiltro] = useState({ ativa: '', pagina: 0 })
  const lista = useDados<Pagina<Regra>>('/recorrencias', { ...filtro, tamanho: 20 })
  const empresas = useEmpresas(lista.dados?.itens.map((r) => r.clienteId) ?? [])
  const { executar, ocupado } = useAcao()
  const [formulario, setFormulario] = useState<Regra | 'nova' | null>(null)

  const gerar = (r: Regra) => executar(() => api(`/recorrencias/${r.id}/gerar-cobranca`, { method: 'POST' }), lista.recarregar)

  return (
    <>
      <PageHeader title="Recorrência" subtitle="Regras que geram a cobrança de cada ciclo no dia configurado"
        actions={administra && <Button size="sm" onClick={() => setFormulario('nova')}><Plus aria-hidden className="size-3.5" /> Nova regra</Button>} />
      <FilterBar>
        <Select aria-label="Situação" className="w-auto" value={filtro.ativa} onChange={(e) => setFiltro({ ativa: e.target.value, pagina: 0 })}>
          <option value="">Todas</option>
          <option value="true">Ativas</option>
          <option value="false">Encerradas</option>
        </Select>
      </FilterBar>
      <div className="flex-1 p-5">
        <Conteudo carga={lista.carga} recarregar={lista.recarregar} vazio={(p) => p.itens.length === 0} icone={<Repeat aria-hidden />}
          textoVazio="Nenhuma regra de recorrência">
          {(p) => (
            <>
              <Tabela atualizando={lista.atualizando} cabecalho={<>
                <Th>Empresa</Th><Th>Regra</Th><Th className="hidden md:table-cell">Periodicidade</Th><Th>Próximo ciclo</Th>
                <Th num>Valor do ciclo</Th><Th>Situação</Th><Th />
              </>}>
                {p.itens.map((r) => (
                  <tr key={r.id} className={linha}>
                    <Td><span className="font-medium text-titulo" title={r.clienteId}>{empresas.nome(r.clienteId)}</span></Td>
                    <Td>{r.descricao ?? 'Mensalidade'}<p className="text-xs text-texto-3">{r.itens.length} item(ns)</p></Td>
                    <Td className="hidden md:table-cell">{PERIODICIDADES[r.periodicidade]} · dia {r.diaGeracao}</Td>
                    <Td>{competencia(r.proximaCompetencia)}</Td>
                    <Td num>{brl(r.valorProximoCiclo)}</Td>
                    <Td><Status status={r.ativa ? 'ativa' : 'inativa'} />{r.vigenciaFim && <p className="text-xs text-texto-3">até {data(r.vigenciaFim)}</p>}</Td>
                    <Td className="text-right whitespace-nowrap">
                      {administra && (
                        <div className="flex justify-end gap-1.5">
                          <Button size="sm" variant="outline" onClick={() => setFormulario(r)}>Editar</Button>
                          {r.ativa && <Button size="sm" disabled={ocupado} onClick={() => gerar(r)} title="Gera a cobrança da competência atual (uma por ciclo)">Gerar ciclo</Button>}
                        </div>
                      )}
                    </Td>
                  </tr>
                ))}
              </Tabela>
              <Pagination pagina={p.pagina} tamanho={p.tamanho} total={p.total} onChange={(n) => setFiltro({ ...filtro, pagina: n })} />
            </>
          )}
        </Conteudo>
      </div>

      {formulario && (
        <FormularioDeRegra atual={formulario === 'nova' ? null : formulario} aoFechar={() => setFormulario(null)}
          aoSalvar={() => { setFormulario(null); lista.recarregar() }} />
      )}
    </>
  )
}

function FormularioDeRegra({ atual, aoFechar, aoSalvar }: { atual: Regra | null; aoFechar: () => void; aoSalvar: () => void }) {
  const { executar, ocupado } = useAcao()
  const [f, setF] = useState({
    clienteId: atual?.clienteId ?? '', contratoId: atual?.contratoId ?? '', descricao: atual?.descricao ?? '',
    periodicidade: atual?.periodicidade ?? 'MENSAL', diaGeracao: String(atual?.diaGeracao ?? 5),
    diasParaVencimento: String(atual?.diasParaVencimento ?? 10), formaPagamento: atual?.formaPagamento ?? 'PIX', ativa: atual?.ativa ?? true,
  })
  const [itens, setItens] = useState<Item[]>(atual?.itens.length ? atual.itens.map((i) => ({ ...i, franquia: i.franquia ?? '' })) : [itemVazio()])
  const total = itens.reduce((s, i) => s + Number(i.quantidade || 0) * Number(i.valorUnitario || 0), 0)
  const alterar = (idx: number, campo: keyof Item, valor: string) => setItens(itens.map((it, i) => (i === idx ? { ...it, [campo]: valor } : it)))

  const salvar = (e: FormEvent) => {
    e.preventDefault()
    const corpo = { ...f, diaGeracao: Number(f.diaGeracao), diasParaVencimento: Number(f.diasParaVencimento), itens }
    void executar(() => (atual ? api(`/recorrencias/${atual.id}`, { method: 'PUT', body: corpo }) : api('/recorrencias', { method: 'POST', body: corpo })), aoSalvar)
  }

  return (
    <Modal open onClose={aoFechar} size="full" title={atual ? 'Editar regra' : 'Nova regra de recorrência'}
      subtitle={atual ? 'A alteração vale a partir do próximo ciclo; as cobranças já geradas não mudam.' : 'A primeira competência é a do mês atual.'}
      footer={<><span className="mr-auto text-sm text-texto-2">Valor por ciclo: <strong className="text-titulo">{brl(total)}</strong></span>
        <Button variant="outline" onClick={aoFechar}>Cancelar</Button><Button type="submit" form="form-regra" loading={ocupado}>Salvar regra</Button></>}>
      <form id="form-regra" onSubmit={salvar}>
        <FormSection number={1} title="Empresa e contrato">
          <CampoUuid label="Empresa (id do CRM)" required valor={f.clienteId} onChange={(v) => setF({ ...f, clienteId: v })} />
          <CampoUuid label="Contrato (opcional)" valor={f.contratoId} onChange={(v) => setF({ ...f, contratoId: v })} />
          <FormField label="Descrição" colSpan={2}><Input maxLength={200} placeholder="Plano gerenciado" value={f.descricao} onChange={(e) => setF({ ...f, descricao: e.target.value })} /></FormField>
        </FormSection>
        <FormSection number={2} title="Ciclo">
          <FormField label="Periodicidade" required>
            <Select value={f.periodicidade} onChange={(e) => setF({ ...f, periodicidade: e.target.value })}>
              {Object.entries(PERIODICIDADES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </Select>
          </FormField>
          <FormField label="Forma de pagamento">
            <Select value={f.formaPagamento} onChange={(e) => setF({ ...f, formaPagamento: e.target.value })}>
              {Object.entries(FORMAS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </Select>
          </FormField>
          <FormField label="Dia de geração" required hint="1 a 28"><Input type="number" min={1} max={28} required value={f.diaGeracao} onChange={(e) => setF({ ...f, diaGeracao: e.target.value })} /></FormField>
          <FormField label="Vence em (dias)" required hint="Depois do dia de geração"><Input type="number" min={0} max={60} required value={f.diasParaVencimento} onChange={(e) => setF({ ...f, diasParaVencimento: e.target.value })} /></FormField>
          {atual && (
            <label className="flex items-center gap-2 text-sm text-texto-2"><Checkbox checked={f.ativa} onChange={(e) => setF({ ...f, ativa: e.target.checked })} /> Regra ativa</label>
          )}
        </FormSection>
        <FormSection number={3} title="Itens (serviço × quantidade × valor unitário)">
          <div className="space-y-2 md:col-span-2">
            {itens.map((it, i) => (
              <div key={i} className="grid grid-cols-2 items-end gap-2 rounded-lg border border-borda p-3 md:grid-cols-[2fr_2fr_1fr_1fr_1fr_1fr_auto]">
                <FormField label="Serviço (id)" required>
                  <div className="flex gap-1">
                    <Input required value={it.servicoId} onChange={(e) => alterar(i, 'servicoId', e.target.value.trim())} className="flex-1 font-mono text-xs" placeholder="UUID de Produtos e Serviços" />
                    <Button variant="ghost" size="sm" onClick={() => alterar(i, 'servicoId', crypto.randomUUID())} title="Gerar id">⟳</Button>
                  </div>
                </FormField>
                <FormField label="Descrição" required><Input required maxLength={200} value={it.descricao} onChange={(e) => alterar(i, 'descricao', e.target.value)} /></FormField>
                <FormField label="Qtd." required><Input type="number" step="0.001" min="0.001" required value={it.quantidade} onChange={(e) => alterar(i, 'quantidade', e.target.value)} /></FormField>
                <FormField label="Unid." required><Input required maxLength={20} value={it.unidade} onChange={(e) => alterar(i, 'unidade', e.target.value)} /></FormField>
                <FormField label="Unitário" required><Input type="number" step="0.01" min="0" required value={it.valorUnitario} onChange={(e) => alterar(i, 'valorUnitario', e.target.value)} /></FormField>
                <FormField label="Franquia"><Input type="number" step="0.001" min="0" value={it.franquia ?? ''} onChange={(e) => alterar(i, 'franquia', e.target.value)} /></FormField>
                <Button variant="ghost" size="md" aria-label="Remover item" disabled={itens.length === 1} onClick={() => setItens(itens.filter((_, j) => j !== i))}>
                  <Trash2 aria-hidden className="size-4" />
                </Button>
              </div>
            ))}
            <Button variant="outline" size="sm" onClick={() => setItens([...itens, itemVazio()])}><Plus aria-hidden className="size-3.5" /> Item</Button>
          </div>
        </FormSection>
      </form>
    </Modal>
  )
}
