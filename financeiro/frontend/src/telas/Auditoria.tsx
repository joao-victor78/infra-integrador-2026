import { Fragment, useState } from 'react'
import { ScrollText } from 'lucide-react'
import { Button, FilterBar, Input, PageHeader, Pagination, Select } from '@/components/ui'
import { Conteudo, linha, Tabela, Td, Th } from '@/components/modulo'
import { useDados } from '@/lib/hooks'
import { curto, dataHora } from '@/lib/formato'
import type { Pagina } from '@/plataforma/api'

interface Registro {
  id: string
  usuarioId: string | null
  servico: string | null
  ip: string | null
  dataHora: string
  acao: string
  entidade: string
  registroId: string
  valorAnterior: unknown
  valorNovo: unknown
}

const ENTIDADES = ['cobranca', 'pagamento', 'conta_pagar', 'recorrencia', 'regua', 'promessa_pagamento', 'webhook_evento']

/** RF-13 / RN-68: quem alterou o quê. Só leitura: o banco recusa alterar ou apagar (RN-67). */
export function Auditoria(_: { detalhe?: string }) {
  const [filtro, setFiltro] = useState({ entidade: '', registroId: '', pagina: 0 })
  const lista = useDados<Pagina<Registro>>('/auditoria', { ...filtro, tamanho: 30 })
  const [aberto, setAberto] = useState<string | null>(null)

  return (
    <>
      <PageHeader title="Auditoria" subtitle="Operações que mexem em dinheiro, em regra ou em acesso · somente inserção" />
      <FilterBar>
        <Select aria-label="Entidade" className="w-auto" value={filtro.entidade} onChange={(e) => setFiltro({ ...filtro, entidade: e.target.value, pagina: 0 })}>
          <option value="">Todas as entidades</option>
          {ENTIDADES.map((e) => <option key={e}>{e}</option>)}
        </Select>
        <Input aria-label="Id do registro" placeholder="Id do registro" className="w-80" value={filtro.registroId}
          onChange={(e) => setFiltro({ ...filtro, registroId: e.target.value.trim(), pagina: 0 })} />
      </FilterBar>
      <div className="flex-1 p-5">
        <Conteudo carga={lista.carga} recarregar={lista.recarregar} vazio={(p) => p.itens.length === 0} icone={<ScrollText aria-hidden />} textoVazio="Nenhum registro">
          {(p) => (
            <>
              <Tabela atualizando={lista.atualizando} cabecalho={<><Th>Quando</Th><Th>Quem</Th><Th className="hidden md:table-cell">IP</Th><Th>Ação</Th><Th>Registro</Th><Th /></>}>
                {p.itens.map((r) => (
                  <Fragment key={r.id}>
                    <tr className={linha}>
                      <Td className="whitespace-nowrap">{dataHora(r.dataHora)}</Td>
                      <Td className="font-mono text-xs">{r.servico ?? (r.usuarioId ? curto(r.usuarioId) : 'rotina automática')}</Td>
                      <Td className="hidden md:table-cell">{r.ip ?? '—'}</Td>
                      <Td><span className="font-semibold text-titulo">{r.acao}</span> <span className="text-xs text-texto-3">{r.entidade}</span></Td>
                      <Td className="font-mono text-xs">{curto(r.registroId)}</Td>
                      <Td className="text-right"><Button variant="ghost" size="sm" onClick={() => setAberto(aberto === r.id ? null : r.id)}>{aberto === r.id ? 'Fechar' : 'Valores'}</Button></Td>
                    </tr>
                    {aberto === r.id && (
                      <tr className="border-b border-borda bg-superficie-2">
                        <td colSpan={6} className="px-4 py-3">
                          <div className="grid gap-3 md:grid-cols-2">
                            <div><p className="mb-1 text-xs font-semibold text-texto-3">Antes</p><pre className="overflow-x-auto font-mono text-xs whitespace-pre-wrap">{JSON.stringify(r.valorAnterior, null, 2) ?? '—'}</pre></div>
                            <div><p className="mb-1 text-xs font-semibold text-texto-3">Depois</p><pre className="overflow-x-auto font-mono text-xs whitespace-pre-wrap">{JSON.stringify(r.valorNovo, null, 2) ?? '—'}</pre></div>
                          </div>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </Tabela>
              <Pagination pagina={p.pagina} tamanho={p.tamanho} total={p.total} onChange={(n) => setFiltro({ ...filtro, pagina: n })} />
            </>
          )}
        </Conteudo>
      </div>
    </>
  )
}
