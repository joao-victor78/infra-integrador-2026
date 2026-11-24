export const brl = (v: number | null | undefined) => Number(v ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })

/** 'AAAA-MM-DD' → 'DD/MM/AAAA' sem passar por Date (evita deslocamento de fuso). */
export const data = (s: string | null | undefined) => {
  if (!s) return '—'
  const [a, m, d] = s.slice(0, 10).split('-')
  return `${d}/${m}/${a}`
}

export const dataHora = (s: string | null | undefined) => (s ? new Date(s).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '—')

/** '2026-09-01' ou '2026-09' → '09/2026'. */
export const competencia = (s: string | null | undefined) => {
  if (!s) return '—'
  const [a, m] = s.split('-')
  return `${m}/${a}`
}

export const hoje = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export const mesAtual = () => hoje().slice(0, 7)

export const curto = (id: string | null | undefined) => (id ? id.slice(0, 8) : '—')

export const etapa = (d: number) => (d === 0 ? 'D0' : d > 0 ? `D+${d}` : `D${d}`)

export const ROTULOS: Record<string, string> = {
  ABERTO: 'Aberto',
  AGENDADO: 'Agendado',
  PAGO: 'Pago',
  ATRASADO: 'Atrasado',
  CANCELADO: 'Cancelado',
  ESTORNADO: 'Estornado',
  PARCIAL: 'Parcial',
  PENDENTE: 'Pendente',
  PROCESSANDO: 'Processando',
  CONFIRMADO: 'Confirmado',
  FALHOU: 'Falhou',
  EXPIRADO: 'Expirado',
  APROVADA: 'Aprovada',
  PAGA: 'Paga',
  CANCELADA: 'Cancelada',
  CUMPRIDA: 'Cumprida',
  QUEBRADA: 'Quebrada',
  SOLICITADO: 'Solicitado',
  SEM_DESTINATARIO: 'Sem destinatário',
  CANAL_INDISPONIVEL: 'Canal indisponível',
  ativa: 'Ativa',
  inativa: 'Encerrada',
}

export type VarianteDeBadge = 'green' | 'red' | 'blue' | 'indigo' | 'yellow' | 'gray' | 'purple' | 'orange'

/** Cor do badge por status, no espírito do design system §7 (verde = ok, vermelho = problema). */
export function corDoStatus(status: string): VarianteDeBadge {
  if (['PAGO', 'CONFIRMADO', 'APROVADA', 'PAGA', 'CUMPRIDA', 'SOLICITADO', 'ativa'].includes(status)) return 'green'
  if (['ATRASADO', 'FALHOU', 'QUEBRADA', 'ESTORNADO'].includes(status)) return 'red'
  if (['PARCIAL', 'SEM_DESTINATARIO', 'CANAL_INDISPONIVEL'].includes(status)) return 'yellow'
  if (['ABERTO', 'PROCESSANDO'].includes(status)) return 'blue'
  if (['AGENDADO', 'PENDENTE'].includes(status)) return 'indigo'
  return 'gray'
}

export const FORMAS: Record<string, string> = { PIX: 'PIX', BOLETO: 'Boleto', CARTAO: 'Cartão (link)', TRANSFERENCIA: 'Transferência' }
export const PERIODICIDADES: Record<string, string> = { MENSAL: 'Mensal', TRIMESTRAL: 'Trimestral', ANUAL: 'Anual' }
export const CANAIS: Record<string, string> = { EMAIL: 'E-mail', NOTIFICACAO_INTERNA: 'Notificação interna', WHATSAPP: 'WhatsApp (sem canal nesta fase)' }
export const FAIXAS = ['1-5', '6-15', '16-30', '31-60', '61-90', '90+']
