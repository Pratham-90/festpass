import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MotionConfig } from 'framer-motion'
import './styles/index.css'
import App from './App.jsx'
import { ToastProvider } from './components/Toasts.jsx'
import { syncServerClock } from './lib/serverClock.js'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      // Retry network blips and server errors, never 4xx answers like 404 or 401.
      retry: (failures, error) => (error?.status == null || error.status >= 500) && failures < 2,
    },
  },
})

syncServerClock()

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        {/* "user" turns animations off for people who ask for reduced motion. */}
        <MotionConfig reducedMotion="user">
          <ToastProvider>
            <App />
          </ToastProvider>
        </MotionConfig>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
