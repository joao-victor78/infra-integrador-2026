import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import { App } from './App'
import { iniciarSessao } from './plataforma/sessao'

// Antes do primeiro render: a casca responde ao modulo:pronto na hora (Contrato §12.2, v0.7)
iniciarSessao()

createRoot(document.getElementById('raiz')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
