import { useState, type ReactNode } from 'react'
import { Check, Copy, RotateCw, SearchX } from 'lucide-react'
import { Badge, Button, cx, EmptyState, Spinner } from '@/components/ui'
import type { Carga } from '@/lib/hooks'
import { corDoStatus, ROTULOS } from '@/lib/formato'

/** Componentes do Financeiro montados sobre os do design system (components/ui, copiado da casca). */

export function Status({ status, texto }: { status: string; texto?: string }) {
  return <Badge variant={corDoStatus(status)}>{texto ?? ROTULOS[status] ?? status}</Badge>
}

/** Estados de interface (RNF-08): carregando, erro com "tentar de novo" e vazio. */
export function Conteudo<T>({ carga, vazio, textoVazio, icone, recarregar, children }: {
  carga: Carga<T>
  vazio?: (dados: T) => boolean
  textoVazio?: string
  icone?: ReactNode
  recarregar?: () => void
  children: (dados: T) => ReactNode
}) {
  if (carga.tipo === 'carregando') return <Spinner />
  if (carga.tipo === 'erro') {
    return (
      <EmptyState
        title={carga.status === 403 ? 'Sem permissão para ver esta tela' : 'Não foi possível carregar'}
        action={recarregar && carga.status !== 403 && <Button variant="outline" onClick={recarregar}><RotateCw aria-hidden className="size-4" /> Tentar de novo</Button>}
      >
        {carga.mensagem}
      </EmptyState>
    )
  }
  if (vazio?.(carga.dados)) return <EmptyState icon={icone ?? <SearchX aria-hidden />} title={textoVazio ?? 'Nenhum registro encontrado'} />
  return <>{children(carga.dados)}</>
}

/** Tabela no padrão da casca: cartão com borda, cabeçalho em caixa alta e linhas com hover. */
export function Tabela({ cabecalho, children, atualizando }: { cabecalho: ReactNode; children: ReactNode; atualizando?: boolean }) {
  return (
    <div className={cx('overflow-hidden rounded-xl border border-borda bg-superficie shadow-sm transition-opacity', atualizando && 'opacity-60')}>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="border-b border-borda bg-superficie-2 text-left text-xs font-semibold tracking-wide text-texto-3 uppercase">
            <tr>{cabecalho}</tr>
          </thead>
          <tbody>{children}</tbody>
        </table>
      </div>
    </div>
  )
}

export const Th = ({ children, num, className }: { children?: ReactNode; num?: boolean; className?: string }) => (
  <th scope="col" className={cx('px-4 py-2.5 whitespace-nowrap', num && 'text-right', className)}>{children}</th>
)

export const Td = ({ children, num, className }: { children?: ReactNode; num?: boolean; className?: string }) => (
  <td className={cx('px-4 py-3 align-middle', num && 'text-right tabular-nums whitespace-nowrap', className)}>{children}</td>
)

export const linha = 'border-b border-borda last:border-0 hover:bg-brand-50/60 dark:hover:bg-superficie-2'

/** Cartão de indicador (home do financeiro). */
export function Indicador({ rotulo, valor, detalhe, tom }: { rotulo: string; valor: ReactNode; detalhe?: ReactNode; tom?: 'ok' | 'erro' }) {
  return (
    <div className="rounded-xl border border-borda bg-superficie p-4 shadow-sm">
      <p className="text-xs font-medium text-texto-3">{rotulo}</p>
      <p className={cx('mt-1 text-xl font-bold tabular-nums text-titulo', tom === 'erro' && 'text-red-600 dark:text-red-400', tom === 'ok' && 'text-brand-700 dark:text-brand-400')}>{valor}</p>
      {detalhe && <p className="mt-0.5 text-xs text-texto-3">{detalhe}</p>}
    </div>
  )
}

export function Copiar({ texto }: { texto: string }) {
  const [ok, setOk] = useState(false)
  return (
    <Button variant="outline" size="sm" onClick={async () => {
      await navigator.clipboard.writeText(texto)
      setOk(true)
      setTimeout(() => setOk(false), 1500)
    }}>
      {ok ? <Check aria-hidden className="size-3.5" /> : <Copy aria-hidden className="size-3.5" />} {ok ? 'Copiado' : 'Copiar'}
    </Button>
  )
}

/** Cabeçalho do painel de detalhe (modal noPad, §5.2): fundo brand-950. */
export function CabecalhoDeDetalhe({ titulo, subtitulo, status, acoes }: { titulo: string; subtitulo?: ReactNode; status?: ReactNode; acoes?: ReactNode }) {
  return (
    <div className="shrink-0 bg-brand-950 px-5 pt-5 pb-4 text-white">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="truncate text-lg font-bold">{titulo}</h2>
          {subtitulo && <p className="truncate text-sm text-brand-300">{subtitulo}</p>}
          {status && <div className="mt-1.5">{status}</div>}
        </div>
        {acoes && <div className="flex shrink-0 gap-1.5">{acoes}</div>}
      </div>
    </div>
  )
}
