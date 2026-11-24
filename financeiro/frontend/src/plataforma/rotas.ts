import { useSyncExternalStore } from 'react'
import { avisarNavegacao } from './sessao'

/**
 * Rotas internas do módulo, sem biblioteca. A casca abre o iframe em /modulos/financeiro{rota}
 * (Contrato §12.8) e, quando a tela muda por dentro, recebe modulo:navegar com a rota relativa.
 */
const BASE = import.meta.env.BASE_URL.replace(/\/$/, '') // "/modulos/financeiro"
const EVENTO = 'financeiro:rota'

function ler() {
  const caminho = window.location.pathname.startsWith(BASE) ? window.location.pathname.slice(BASE.length) : window.location.pathname
  return caminho.replace(/\/+$/, '') || '/'
}

let atual = ler()

window.addEventListener('popstate', () => {
  atual = ler()
  window.dispatchEvent(new Event(EVENTO))
})

export function navegar(rota: string) {
  if (rota === atual) return
  window.history.pushState(null, '', BASE + rota)
  atual = rota
  window.dispatchEvent(new Event(EVENTO))
  window.scrollTo(0, 0)
  avisarNavegacao(rota)
}

export function useRota() {
  return useSyncExternalStore(
    (aoMudar) => {
      window.addEventListener(EVENTO, aoMudar)
      return () => window.removeEventListener(EVENTO, aoMudar)
    },
    () => atual,
  )
}
