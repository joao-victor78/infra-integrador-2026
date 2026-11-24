import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { api, ErroDaApi, mensagemDe } from '@/plataforma/api'
import type { Sessao } from '@/plataforma/sessao'
import { avisar } from './avisos'

export const SessaoCtx = createContext<Sessao | null>(null)

/** Permissões do token. Esconder botão é usabilidade; quem garante é a API (RNF-05). */
export function usePermissao() {
  const s = useContext(SessaoCtx)
  const p = s?.permissoes ?? []
  return { sessao: s, tem: (permissao: string) => p.includes(permissao) }
}

export type Carga<T> = { tipo: 'carregando' } | { tipo: 'erro'; mensagem: string; status: number } | { tipo: 'pronto'; dados: T }

/**
 * Carrega um recurso e recarrega quando o caminho, os filtros ou o token mudam. Mantém os dados
 * anteriores enquanto atualiza (atualizando), para a tabela não piscar ao trocar de página.
 */
export function useDados<T>(caminho: string | null, query?: Record<string, unknown>) {
  const sessao = useContext(SessaoCtx)
  const [carga, setCarga] = useState<Carga<T>>({ tipo: 'carregando' })
  const [atualizando, setAtualizando] = useState(false)
  const [versao, setVersao] = useState(0)
  const chave = caminho ? caminho + JSON.stringify(query ?? {}) : null

  useEffect(() => {
    if (!caminho) return
    let vivo = true
    setAtualizando(true)
    api<T>(caminho, { query })
      .then((r) => vivo && setCarga({ tipo: 'pronto', dados: r.data }))
      .catch((e) => vivo && setCarga({ tipo: 'erro', mensagem: mensagemDe(e), status: e instanceof ErroDaApi ? e.status : 0 }))
      .finally(() => vivo && setAtualizando(false))
    return () => {
      vivo = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chave, versao, sessao?.token])

  const recarregar = useCallback(() => setVersao((v) => v + 1), [])
  return { carga, dados: carga.tipo === 'pronto' ? carga.dados : null, atualizando, recarregar }
}

/** Executa uma escrita e avisa o resultado (na casca, quando embutido). */
export function useAcao() {
  const [ocupado, setOcupado] = useState(false)
  const executar = useCallback(async <T,>(fn: () => Promise<{ data: T; message: string | null }>, depois?: (d: T) => void) => {
    setOcupado(true)
    try {
      const r = await fn()
      if (r.message) avisar('sucesso', r.message)
      depois?.(r.data)
      return true
    } catch (e) {
      avisar('erro', mensagemDe(e))
      return false
    } finally {
      setOcupado(false)
    }
  }, [])
  return { executar, ocupado }
}
