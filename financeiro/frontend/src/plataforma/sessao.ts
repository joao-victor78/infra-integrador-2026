/**
 * Conversa com a casca por postMessage (Contrato §12.1 e §12.2).
 *
 * Embutido no iframe: a sessão chega da casca e o token vive só em memória — nunca em localStorage.
 * Aberto direto no navegador (desenvolvimento): o login é feito aqui, com um usuário de teste.
 * O ouvinte é registrado antes de avisar modulo:pronto e a sessão fica fora dos componentes (§12.2, v0.7).
 */

export type Tema = 'claro' | 'escuro'

export interface Usuario {
  id: string
  nome: string
  email?: string
  tenantId?: string
}

export interface Sessao {
  token: string
  tenantId: string
  usuario: Usuario
  tema: Tema
  permissoes: string[]
}

type MensagemDaCasca =
  | { tipo: 'plataforma:sessao'; token: string; tenantId: string; usuario: Usuario; tema: Tema }
  | { tipo: 'plataforma:token'; token: string }
  | { tipo: 'plataforma:tema'; tema: Tema }

type MensagemParaCasca =
  | { tipo: 'modulo:pronto' }
  | { tipo: 'modulo:altura'; altura: number }
  | { tipo: 'modulo:navegar'; rota: string }
  | { tipo: 'modulo:token-expirado' }
  | { tipo: 'modulo:notificar'; nivel: 'sucesso' | 'erro' | 'info'; texto: string }

const embutido = window.parent !== window
let sessao: Sessao | null = null
const ouvintes = new Set<(s: Sessao | null) => void>()

/** Permissões do claim "perms". Só para mostrar ou esconder botões: quem garante é a API (RNF-05). */
export function permissoesDoToken(token: string): string[] {
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const json = new TextDecoder().decode(Uint8Array.from(atob(base64), (c) => c.charCodeAt(0)))
    const p = JSON.parse(json)
    return Array.isArray(p.perms) ? p.perms : []
  } catch {
    return []
  }
}

function aplicarTema(tema: Tema) {
  document.documentElement.dataset.tema = tema
}

function atualizar(nova: Sessao | null) {
  sessao = nova
  if (nova) aplicarTema(nova.tema)
  ouvintes.forEach((o) => o(sessao))
}

function paraCasca(mensagem: MensagemParaCasca) {
  // Mesma origem: casca e módulos são servidos pelo mesmo gateway (§12.8)
  if (embutido) window.parent.postMessage(mensagem, window.location.origin)
}

export function iniciarSessao() {
  if (!embutido) {
    aplicarTema(matchMedia('(prefers-color-scheme: dark)').matches ? 'escuro' : 'claro')
    return
  }
  window.addEventListener('message', (evento: MessageEvent<MensagemDaCasca>) => {
    // Só aceita mensagem da própria plataforma, vinda da janela que embute o módulo
    if (evento.origin !== window.location.origin || evento.source !== window.parent) return
    const m = evento.data
    switch (m?.tipo) {
      case 'plataforma:sessao':
        atualizar({ token: m.token, tenantId: m.tenantId, usuario: m.usuario, tema: m.tema, permissoes: permissoesDoToken(m.token) })
        break
      case 'plataforma:token':
        if (sessao) atualizar({ ...sessao, token: m.token, permissoes: permissoesDoToken(m.token) })
        break
      case 'plataforma:tema':
        if (sessao) atualizar({ ...sessao, tema: m.tema })
        else aplicarTema(m.tema)
        break
    }
  })
  // A casca ajusta a altura do iframe para não haver rolagem dupla
  new ResizeObserver(() => paraCasca({ tipo: 'modulo:altura', altura: document.documentElement.scrollHeight })).observe(document.body)
  paraCasca({ tipo: 'modulo:pronto' })
}

export const estaEmbutido = () => embutido
export const sessaoAtual = () => sessao

export function aoMudarSessao(ouvinte: (s: Sessao | null) => void) {
  ouvintes.add(ouvinte)
  return () => {
    ouvintes.delete(ouvinte)
  }
}

/** Modo direto: sessão do login de desenvolvimento, só em memória. */
export function definirSessaoDireta(token: string, usuario: Usuario, tenantId: string) {
  atualizar({ token, usuario, tenantId, tema: (document.documentElement.dataset.tema as Tema) ?? 'claro', permissoes: permissoesDoToken(token) })
}

export function encerrarSessaoDireta() {
  atualizar(null)
}

export function avisarTokenExpirado() {
  if (embutido) paraCasca({ tipo: 'modulo:token-expirado' })
  else encerrarSessaoDireta()
}

/** Rota relativa ao módulo, sem o código (§12.2, v0.7): "/cobrancas" vira "/app/financeiro/cobrancas" na casca. */
export function avisarNavegacao(rota: string) {
  paraCasca({ tipo: 'modulo:navegar', rota })
}

export function notificarCasca(nivel: 'sucesso' | 'erro' | 'info', texto: string) {
  paraCasca({ tipo: 'modulo:notificar', nivel, texto })
}
