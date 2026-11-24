import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const CODIGO = 'financeiro'
const PORTA = 3005

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  const gateway = env.VITE_GATEWAY || 'http://localhost:8080'
  return {
    // Servido na mesma origem da casca, sob /modulos/{codigo}/ (Contrato §12.8)
    base: `/modulos/${CODIGO}/`,
    plugins: [
      react(),
      tailwindcss(),
      // Em desenvolvimento, "/" e "/modulos/financeiro" levam ao endereço do módulo
      {
        name: 'redirecionar-para-o-modulo',
        configureServer(server) {
          server.middlewares.use((req, res, next) => {
            if (req.url === '/' || req.url === `/modulos/${CODIGO}`) {
              res.statusCode = 302
              res.setHeader('Location', `/modulos/${CODIGO}/`)
              res.end()
              return
            }
            next()
          })
        },
      },
    ],
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    server: {
      port: PORTA,
      strictPort: true,
      // Modo direto: /api vai ao gateway, como em produção. Sem a plataforma no ar,
      // VITE_API_FINANCEIRO aponta a API do Financeiro direto (http://localhost:8085).
      proxy: {
        '/api/financeiro': { target: env.VITE_API_FINANCEIRO || gateway, changeOrigin: true },
        '/api': { target: gateway, changeOrigin: true },
      },
    },
  }
})
