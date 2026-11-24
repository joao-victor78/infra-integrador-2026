import { useSyncExternalStore } from 'react'
import { CircleAlert, CircleCheck, Info } from 'lucide-react'
import { estaEmbutido, notificarCasca } from '@/plataforma/sessao'

/**
 * Avisos de sucesso e erro. Embutido, quem mostra é a casca (modulo:notificar, §12.2), igual aos
 * outros módulos; aberto direto, aparecem aqui no canto da tela.
 */
type Nivel = 'sucesso' | 'erro' | 'info'
type Aviso = { id: number; nivel: Nivel; texto: string }

let avisos: Aviso[] = []
let seq = 0
const ouvintes = new Set<() => void>()
const emitir = () => ouvintes.forEach((o) => o())

export function avisar(nivel: Nivel, texto: string) {
  if (estaEmbutido()) {
    notificarCasca(nivel, texto)
    return
  }
  const id = ++seq
  avisos = [...avisos, { id, nivel, texto }]
  emitir()
  setTimeout(() => {
    avisos = avisos.filter((a) => a.id !== id)
    emitir()
  }, nivel === 'erro' ? 7000 : 4000)
}

export function Avisos() {
  const lista = useSyncExternalStore(
    (o) => {
      ouvintes.add(o)
      return () => ouvintes.delete(o)
    },
    () => avisos,
  )
  return (
    <div role="status" aria-live="polite" className="fixed right-4 bottom-4 z-[60] flex w-80 flex-col gap-2">
      {lista.map((a) => (
        <div key={a.id} className="flex items-start gap-2 rounded-xl border border-borda bg-superficie p-3 text-sm shadow-lg">
          {a.nivel === 'erro' ? <CircleAlert className="size-4 shrink-0 text-red-600" /> : a.nivel === 'sucesso' ? <CircleCheck className="size-4 shrink-0 text-brand-700" /> : <Info className="size-4 shrink-0 text-blue-600" />}
          <p className="text-texto">{a.texto}</p>
        </div>
      ))}
    </div>
  )
}
