import { useEffect, useState, type FormEvent } from 'react'
import { LogOut } from 'lucide-react'
import { Button, cx, FormField, Input, Textarea } from '@/components/ui'
import { Avisos } from '@/lib/avisos'
import { SessaoCtx } from '@/lib/hooks'
import { api, mensagemDe } from '@/plataforma/api'
import { navegar, useRota } from '@/plataforma/rotas'
import { aoMudarSessao, definirSessaoDireta, encerrarSessaoDireta, estaEmbutido, permissoesDoToken, sessaoAtual, type Sessao } from '@/plataforma/sessao'
import { Auditoria } from '@/telas/Auditoria'
import { Cobrancas } from '@/telas/Cobrancas'
import { ContasPagar } from '@/telas/ContasPagar'
import { Dashboard } from '@/telas/Dashboard'
import { Inadimplencia } from '@/telas/Inadimplencia'
import { Recorrencias } from '@/telas/Recorrencias'
import { Regua } from '@/telas/Regua'

/** As mesmas rotas e permissões do submenu em modulos/financeiro.json (infra-integrador-2026). */
const TELAS = [
  { rota: '/dashboard', nome: 'Dashboard', permissao: 'financeiro.dashboard.ver', Tela: Dashboard },
  { rota: '/cobrancas', nome: 'Contas a receber', permissao: 'financeiro.contareceber.ver', Tela: Cobrancas },
  { rota: '/contas-pagar', nome: 'Contas a pagar', permissao: 'financeiro.contapagar.ver', Tela: ContasPagar },
  { rota: '/recorrencias', nome: 'Recorrência', permissao: 'financeiro.recorrencia.ver', Tela: Recorrencias },
  { rota: '/regua', nome: 'Régua de cobrança', permissao: 'financeiro.regua.ver', Tela: Regua },
  { rota: '/inadimplencia', nome: 'Inadimplência', permissao: 'financeiro.inadimplencia.ver', Tela: Inadimplencia },
  { rota: '/auditoria', nome: 'Auditoria', permissao: 'financeiro.auditoria.ver', Tela: Auditoria },
]

export function App() {
  const [sessao, setSessao] = useState<Sessao | null>(sessaoAtual())
  useEffect(() => {
    const parar = aoMudarSessao(setSessao)
    // A sessão pode ter chegado entre o primeiro render e esta inscrição (Contrato §12.2, v0.7)
    setSessao(sessaoAtual())
    return parar
  }, [])

  return (
    <>
      {!sessao ? (estaEmbutido() ? <Aguardando /> : <EntradaDeTeste />) : (
        <SessaoCtx.Provider value={sessao}>
          <Modulo sessao={sessao} />
        </SessaoCtx.Provider>
      )}
      <Avisos />
    </>
  )
}

function Modulo({ sessao }: { sessao: Sessao }) {
  const rota = useRota()
  const permitidas = TELAS.filter((t) => sessao.permissoes.includes(t.permissao))
  // "/cobrancas/{id}" (rota da busca global) abre a tela de cobranças com o detalhe aberto
  const tela = TELAS.find((t) => rota === t.rota || rota.startsWith(t.rota + '/'))
  const detalhe = tela && rota.length > tela.rota.length ? rota.slice(tela.rota.length + 1) : undefined

  useEffect(() => {
    if ((rota === '/' || !tela) && permitidas.length > 0) navegar(permitidas[0].rota)
  }, [rota, tela, permitidas])

  if (permitidas.length === 0) {
    return <Aguardando texto="Seu perfil não tem telas do Financeiro liberadas." />
  }
  if (!tela) return null
  if (!sessao.permissoes.includes(tela.permissao)) return <Aguardando texto="Seu perfil não tem acesso a esta tela." />

  return (
    <div className="flex min-h-dvh flex-col bg-fundo text-texto">
      {/* Embutido, o menu é o da casca (submenu do registro do módulo). Direto, uma barra simples. */}
      {!estaEmbutido() && (
        <nav className="flex flex-wrap items-center gap-1 border-b border-borda bg-superficie px-3 py-2">
          <span className="mr-3 text-sm font-bold text-titulo">Financeiro</span>
          {permitidas.map((t) => (
            <button key={t.rota} type="button" onClick={() => navegar(t.rota)}
              className={cx('rounded-lg px-3 py-1.5 text-xs font-semibold', t === tela ? 'bg-brand-700 text-white' : 'text-texto-2 hover:bg-superficie-2')}>
              {t.nome}
            </button>
          ))}
          <span className="ml-auto text-xs text-texto-3">{sessao.usuario.nome} · tenant {sessao.tenantId.slice(0, 8)}</span>
          <Button variant="ghost" size="sm" onClick={encerrarSessaoDireta}><LogOut aria-hidden className="size-3.5" /> Sair</Button>
        </nav>
      )}
      <tela.Tela key={tela.rota} detalhe={detalhe} />
    </div>
  )
}

function Aguardando({ texto = 'Aguardando a sessão da plataforma…' }: { texto?: string }) {
  return <main className="grid min-h-40 place-items-center p-6 text-sm text-texto-3">{texto}</main>
}

/**
 * Modo direto, só para desenvolvimento. Com o identity no ar: usuário de teste (docs/usuarios-de-teste.md
 * do infra). Sem ele: token de "mvn -q compile exec:java@token" na API (chave de desenvolvimento).
 */
function EntradaDeTeste() {
  const [email, setEmail] = useState('administrador@empresa-a.dev')
  const [senha, setSenha] = useState('')
  const [token, setToken] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  async function entrar(evento: FormEvent) {
    evento.preventDefault()
    setEnviando(true)
    setErro(null)
    try {
      const r = await api<{ accessToken: string; usuario: { id: string; nome: string; tenantId: string } }>('/api/identity/auth/login', {
        method: 'POST', body: { email, senha },
      })
      definirSessaoDireta(r.data.accessToken, r.data.usuario, r.data.usuario.tenantId)
    } catch (e) {
      setErro(mensagemDe(e))
    } finally {
      setEnviando(false)
    }
  }

  function usarToken(evento: FormEvent) {
    evento.preventDefault()
    try {
      const t = token.trim()
      const claims = JSON.parse(atob(t.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
      if (!claims.tenant_id || permissoesDoToken(t).length === 0) throw new Error()
      definirSessaoDireta(t, { id: claims.sub, nome: claims.nome ?? 'Desenvolvimento' }, claims.tenant_id)
    } catch {
      setErro('Token inválido: use o gerado por mvn -q compile exec:java@token na pasta da API.')
    }
  }

  return (
    <main className="mx-auto max-w-md p-6">
      <p className="font-mono text-xs tracking-widest text-texto-3 uppercase">Modo direto · desenvolvimento</p>
      <h1 className="mt-2 text-xl font-bold text-titulo">Financeiro</h1>
      <p className="mt-1 text-sm text-texto-3">Dentro da plataforma, este passo não existe: a casca entrega a sessão.</p>

      <form onSubmit={entrar} className="mt-6 grid gap-3 rounded-xl border border-borda bg-superficie p-5 shadow-sm">
        <h2 className="text-sm font-semibold text-titulo">Usuário de teste do identity</h2>
        <FormField label="E-mail" required><Input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} /></FormField>
        <FormField label="Senha" required><Input type="password" required value={senha} onChange={(e) => setSenha(e.target.value)} /></FormField>
        <Button type="submit" loading={enviando}>Entrar</Button>
      </form>

      <form onSubmit={usarToken} className="mt-4 grid gap-3 rounded-xl border border-borda bg-superficie p-5 shadow-sm">
        <h2 className="text-sm font-semibold text-titulo">Sem o identity no ar</h2>
        <FormField label="Token de desenvolvimento" hint={<>Na pasta da API: <code className="font-mono">mvn -q compile exec:java@token -Dexec.args="&lt;tenant&gt; ADMINISTRADOR"</code></>}>
          <Textarea required value={token} onChange={(e) => setToken(e.target.value)} className="font-mono text-xs" />
        </FormField>
        <Button type="submit" variant="outline">Usar token</Button>
      </form>
      {erro && <p role="alert" className="mt-3 text-sm text-red-600 dark:text-red-400">{erro}</p>}
    </main>
  )
}
