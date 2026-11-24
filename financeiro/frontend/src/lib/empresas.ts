import { useEffect, useState } from 'react'
import { api } from '@/plataforma/api'

/**
 * Razão social das empresas, lida do CRM em lote (Contrato §9.3, §9.4) com o mesmo token do usuário.
 * O Financeiro guarda só o UUID (RN-03). Timeout de 3 s (RNF-09): sem o CRM, a tela mostra o id curto
 * e avisa que os dados da empresa estão indisponíveis, sem quebrar.
 */
const cache = new Map<string, string>()

export function useEmpresas(ids: (string | null | undefined)[]) {
  const [, setVersao] = useState(0)
  const [indisponivel, setIndisponivel] = useState(false)
  const faltando = [...new Set(ids.filter((i): i is string => !!i))].filter((i) => !cache.has(i)).sort()
  const chave = faltando.join(',')

  useEffect(() => {
    if (faltando.length === 0) return
    let vivo = true
    const lotes: string[][] = []
    for (let i = 0; i < faltando.length; i += 100) lotes.push(faltando.slice(i, i + 100))
    Promise.all(
      lotes.map((lote) =>
        api<{ id: string; razaoSocial: string }[]>('/api/crm/empresas/resumo', { query: { ids: lote.join(',') }, timeoutMs: 3000 })
          .then((r) => (r.data ?? []).forEach((e) => cache.set(e.id, e.razaoSocial)))
          .catch(() => vivo && setIndisponivel(true)),
      ),
    ).then(() => vivo && setVersao((v) => v + 1))
    return () => {
      vivo = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chave])

  return { nome: (id: string | null | undefined) => (id ? cache.get(id) ?? `Empresa ${id.slice(0, 8)}` : '—'), indisponivel }
}
