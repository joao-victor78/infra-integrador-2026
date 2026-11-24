import { avisarTokenExpirado, sessaoAtual } from './sessao'

/** Envelope de toda resposta (Contrato §8.2). */
export interface Envelope<T> {
  success: boolean
  data: T | null
  message: string | null
  errors: { campo: string | null; codigo: string; detalhe: string }[]
}

/** Listagem paginada (Contrato §8.3): pagina começa em 0, tamanho até 100. */
export interface Pagina<T> {
  itens: T[]
  pagina: number
  tamanho: number
  total: number
}

export class ErroDaApi extends Error {
  readonly status: number
  readonly erros: Envelope<unknown>['errors']

  constructor(status: number, mensagem: string, erros: Envelope<unknown>['errors'] = []) {
    super(mensagem)
    this.status = status
    this.erros = erros
  }

  /** Mensagem e detalhes dos campos, para mostrar ao usuário. */
  get detalhe() {
    const campos = this.erros.filter((e) => e.detalhe && e.detalhe !== this.message).map((e) => (e.campo ? `${e.campo}: ${e.detalhe}` : e.detalhe))
    return [this.message, ...campos].join(' — ')
  }

  /** Erro de um campo do formulário (errors[].detalhe do servidor). */
  doCampo(campo: string) {
    return this.erros.find((e) => e.campo === campo)?.detalhe ?? null
  }
}

export const mensagemDe = (e: unknown) => (e instanceof ErroDaApi ? e.detalhe : e instanceof Error ? e.message : 'Algo deu errado.')

type Opcoes = { method?: string; body?: unknown; query?: Record<string, unknown>; timeoutMs?: number }

/** Remove campos vazios: "" em campo UUID ou data seria recusado pela API. */
export function limpar<T>(obj: T): T {
  if (Array.isArray(obj)) return obj.map(limpar) as T
  if (obj && typeof obj === 'object') {
    return Object.fromEntries(
      Object.entries(obj as Record<string, unknown>)
        .filter(([, v]) => v !== '' && v !== undefined && v !== null)
        .map(([k, v]) => [k, limpar(v)]),
    ) as T
  }
  return obj
}

export function url(caminho: string, query?: Record<string, unknown>) {
  const qs = new URLSearchParams()
  for (const [k, v] of Object.entries(query ?? {})) if (v !== '' && v !== undefined && v !== null) qs.set(k, String(v))
  const s = qs.toString()
  const base = caminho.startsWith('/api/') ? caminho : `/api/financeiro${caminho}`
  return `${base}${s ? `?${s}` : ''}`
}

function cabecalhos(json: boolean) {
  const h = new Headers()
  const s = sessaoAtual()
  if (s) h.set('Authorization', `Bearer ${s.token}`)
  if (json) h.set('Content-Type', 'application/json')
  return h
}

/** Chamada à API pelo caminho relativo, com o token em memória no cabeçalho. Devolve data e message. */
export async function api<T = unknown>(caminho: string, op: Opcoes = {}): Promise<{ data: T; message: string | null }> {
  const init: RequestInit = { method: op.method ?? 'GET', cache: 'no-store', headers: cabecalhos(op.body !== undefined) }
  if (op.body !== undefined) init.body = JSON.stringify(limpar(op.body))
  if (op.timeoutMs) init.signal = AbortSignal.timeout(op.timeoutMs)
  let r: Response
  try {
    r = await fetch(url(caminho, op.query), init)
  } catch {
    throw new ErroDaApi(0, 'Não foi possível falar com o servidor.')
  }
  const corpo = (await r.json().catch(() => null)) as Envelope<T> | null
  if (r.status === 401) avisarTokenExpirado() // a casca renova e reenvia o token
  if (!r.ok || !corpo?.success) throw new ErroDaApi(r.status, corpo?.message ?? `Erro ${r.status}`, corpo?.errors ?? [])
  return { data: corpo.data as T, message: corpo.message }
}

/** Download (exportação): a única resposta sem envelope. Token no cabeçalho, nunca na URL (§8.2). */
export async function baixarArquivo(caminho: string, query: Record<string, unknown>, nome: string) {
  const r = await fetch(url(caminho, query), { headers: cabecalhos(false), cache: 'no-store' })
  if (!r.ok) {
    const corpo = (await r.json().catch(() => null)) as Envelope<unknown> | null
    if (r.status === 401) avisarTokenExpirado()
    throw new ErroDaApi(r.status, corpo?.message ?? `Erro ${r.status}`, corpo?.errors ?? [])
  }
  const a = document.createElement('a')
  a.href = URL.createObjectURL(await r.blob())
  a.download = nome
  a.click()
  setTimeout(() => URL.revokeObjectURL(a.href), 1000)
}
