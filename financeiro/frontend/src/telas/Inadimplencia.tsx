import { useState } from 'react'
import { PartyPopper } from 'lucide-react'
import { Button, cx, DetailField, DetailSection, FilterBar, Modal, PageHeader, Pagination, Select, Spinner } from '@/components/ui'
import { CabecalhoDeDetalhe, Conteudo, Indicador, linha, Status, Tabela, Td, Th } from '@/components/modulo'
import { useEmpresas } from '@/lib/empresas'
import { useDados } from '@/lib/hooks'
import { brl, CANAIS, curto, data, dataHora, etapa, FAIXAS, FORMAS } from '@/lib/formato'
import { navegar } from '@/plataforma/rotas'

interface Item { cobrancaId: string; clienteId: string; vencimento: string; diasAtraso: number; faixa: string; saldoDevedor: number; status: string; descricao: string | null }
interface Painel {
  itens: Item[]
  totalPorFaixa: Record<string, number>
  empresas: { clienteId: string; cobrancas: number; maiorAtraso: number; saldoDevedor: number }[]
  total: number
  pagina: number
  tamanho: number
  totalItens: number
}
interface Historico {
  cobrancas: { id: string; vencimento: string; valor: number; saldo: number; status: string; descricao: string | null }[]
  pagamentos: { pagamentoId: string; meio: string; status: string; valor: number; confirmadoEm: string | null; createdAt: string }[]
  disparos: { id: string; diasOffset: number; canal: string; situacao: string; disparadoEm: string }[]
  promessas: { id: string; dataPrometida: string; valor: number; status: string }[]
}

/** RF-07: aging (RN-38), PARCIAL pelo saldo (RN-39) e histórico da empresa (RN-40). */
export function Inadimplencia(_: { detalhe?: string }) {
  const [filtro, setFiltro] = useState({ faixa: '', ordenar: 'dias', pagina: 0 })
  const painel = useDados<Painel>('/inadimplencia', { ...filtro, tamanho: 20 })
  const p = painel.dados
  const empresas = useEmpresas([...(p?.itens.map((i) => i.clienteId) ?? []), ...(p?.empresas.map((e) => e.clienteId) ?? [])])
  const [empresa, setEmpresa] = useState<string | null>(null)
  const maior = p ? Math.max(1, ...Object.values(p.totalPorFaixa)) : 1

  return (
    <>
      <PageHeader title="Inadimplência" subtitle={p ? `${brl(p.total)} vencidos · ${p.empresas.length} empresa(s)` : 'Cobranças vencidas por faixa de atraso'} />
      <FilterBar>
        <Select aria-label="Faixa" className="w-auto" value={filtro.faixa} onChange={(e) => setFiltro({ ...filtro, faixa: e.target.value, pagina: 0 })}>
          <option value="">Todas as faixas</option>
          {FAIXAS.map((f) => <option key={f} value={f}>{f} dias</option>)}
        </Select>
        <Select aria-label="Ordenar por" className="w-auto" value={filtro.ordenar} onChange={(e) => setFiltro({ ...filtro, ordenar: e.target.value, pagina: 0 })}>
          <option value="dias">Mais dias de atraso</option>
          <option value="valor">Maior valor</option>
        </Select>
      </FilterBar>
      <div className="flex-1 space-y-5 p-5">
        {empresas.indisponivel && <p className="text-xs text-texto-3">Dados das empresas indisponíveis no CRM agora; mostrando o identificador.</p>}
        <Conteudo carga={painel.carga} recarregar={painel.recarregar}>
          {(d) => (
            <>
              <div className="grid gap-4 lg:grid-cols-[1fr_2fr]">
                <div className="grid grid-cols-2 gap-3 lg:grid-cols-1">
                  <Indicador rotulo="Saldo vencido" valor={brl(d.total)} tom={d.total ? 'erro' : undefined} />
                  <Indicador rotulo="Cobranças vencidas" valor={d.totalItens} detalhe={`${d.empresas.length} empresa(s)`} />
                </div>
                <div className="rounded-xl border border-borda bg-superficie p-4 shadow-sm">
                  <p className="mb-3 text-sm font-semibold text-titulo">Aging</p>
                  {FAIXAS.map((f) => (
                    <button key={f} type="button" onClick={() => setFiltro({ ...filtro, faixa: filtro.faixa === f ? '' : f, pagina: 0 })}
                      className={cx('grid w-full grid-cols-[4.5rem_1fr_7rem] items-center gap-3 rounded-lg px-2 py-1.5 text-left text-sm hover:bg-superficie-2', filtro.faixa === f && 'bg-brand-50 dark:bg-superficie-2')}>
                      <span className="text-texto-2">{f} dias</span>
                      <span className="h-2 overflow-hidden rounded-full bg-superficie-2">
                        <span className="block h-full rounded-full bg-brand-700" style={{ width: `${(100 * (d.totalPorFaixa[f] ?? 0)) / maior}%` }} />
                      </span>
                      <span className="text-right tabular-nums">{brl(d.totalPorFaixa[f])}</span>
                    </button>
                  ))}
                </div>
              </div>

              {d.empresas.length > 0 && (
                <Tabela cabecalho={<><Th>Empresa</Th><Th num>Cobranças</Th><Th num>Maior atraso</Th><Th num>Saldo devedor</Th></>}>
                  {d.empresas.map((e) => (
                    <tr key={e.clienteId} onClick={() => setEmpresa(e.clienteId)} className={`cursor-pointer ${linha}`}>
                      <Td><span className="font-medium text-titulo">{empresas.nome(e.clienteId)}</span></Td>
                      <Td num>{e.cobrancas}</Td><Td num>{e.maiorAtraso} dias</Td><Td num>{brl(e.saldoDevedor)}</Td>
                    </tr>
                  ))}
                </Tabela>
              )}

              {d.itens.length === 0 ? (
                <p className="flex items-center gap-2 text-sm text-texto-3"><PartyPopper aria-hidden className="size-4" /> Nenhuma cobrança vencida{filtro.faixa && ' nesta faixa'}.</p>
              ) : (
                <>
                  <Tabela atualizando={painel.atualizando} cabecalho={<>
                    <Th>Empresa</Th><Th>Cobrança</Th><Th>Vencimento</Th><Th num>Dias</Th><Th>Faixa</Th><Th num>Saldo devedor</Th><Th>Status</Th>
                  </>}>
                    {d.itens.map((i) => (
                      <tr key={i.cobrancaId} onClick={() => navegar(`/cobrancas/${i.cobrancaId}`)} className={`cursor-pointer ${linha}`}>
                        <Td>{empresas.nome(i.clienteId)}</Td>
                        <Td>{i.descricao ?? '—'} <span className="font-mono text-xs text-texto-3">#{curto(i.cobrancaId)}</span></Td>
                        <Td>{data(i.vencimento)}</Td><Td num>{i.diasAtraso}</Td><Td>{i.faixa}</Td><Td num>{brl(i.saldoDevedor)}</Td>
                        <Td><Status status={i.status} /></Td>
                      </tr>
                    ))}
                  </Tabela>
                  <Pagination pagina={d.pagina} tamanho={d.tamanho} total={d.totalItens} onChange={(n) => setFiltro({ ...filtro, pagina: n })} />
                </>
              )}
            </>
          )}
        </Conteudo>
      </div>

      <Modal open={empresa !== null} onClose={() => setEmpresa(null)} size="xl" noPad title="Histórico da empresa">
        {empresa && <HistoricoDaEmpresa id={empresa} nome={empresas.nome(empresa)} aoFechar={() => setEmpresa(null)} />}
      </Modal>
    </>
  )
}

function HistoricoDaEmpresa({ id, nome, aoFechar }: { id: string; nome: string; aoFechar: () => void }) {
  const h = useDados<Historico>(`/inadimplencia/empresas/${id}`)
  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
      <CabecalhoDeDetalhe titulo={nome} subtitulo={`Empresa ${id}`} acoes={<Button variant="secondary" size="sm" onClick={aoFechar}>Fechar</Button>} />
      <div className="min-h-0 flex-1 overflow-y-auto p-6">
        {!h.dados ? <Spinner /> : (
          <>
            <DetailSection number={1} title="Cobranças">
              {h.dados.cobrancas.map((c) => (
                <DetailField key={c.id} label={`${data(c.vencimento)} · ${c.descricao ?? '#' + curto(c.id)}`}>
                  {brl(c.valor)} · saldo {brl(c.saldo)} <Status status={c.status} />
                </DetailField>
              ))}
            </DetailSection>
            <DetailSection number={2} title="Pagamentos">
              {h.dados.pagamentos.length === 0 ? <DetailField label="Nenhum pagamento">{null}</DetailField> : h.dados.pagamentos.map((p) => (
                <DetailField key={p.pagamentoId} label={dataHora(p.confirmadoEm ?? p.createdAt)}>{FORMAS[p.meio] ?? p.meio} · {brl(p.valor)} <Status status={p.status} /></DetailField>
              ))}
            </DetailSection>
            <DetailSection number={3} title="Avisos da régua">
              {h.dados.disparos.length === 0 ? <DetailField label="Nenhum aviso disparado">{null}</DetailField> : h.dados.disparos.map((d) => (
                <DetailField key={d.id} label={dataHora(d.disparadoEm)}>{etapa(d.diasOffset)} · {CANAIS[d.canal] ?? d.canal} <Status status={d.situacao} /></DetailField>
              ))}
            </DetailSection>
            {h.dados.promessas.length > 0 && (
              <DetailSection number={4} title="Promessas">
                {h.dados.promessas.map((p) => <DetailField key={p.id} label={data(p.dataPrometida)}>{brl(p.valor)} <Status status={p.status} /></DetailField>)}
              </DetailSection>
            )}
          </>
        )}
      </div>
    </div>
  )
}
