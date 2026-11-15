import { Suspense, useEffect, useRef } from 'react'
import { Outlet, ScrollRestoration, useLocation } from 'react-router'
import { SiteFooter } from './site-footer'
import { SiteHeader } from './site-header'
import { PageFallback } from './page-fallback'
import { Toaster } from '@/components/ui/sonner'
import { usePageviews } from '@/lib/pageview'
import { markEntryLeft } from '@/lib/reveal'

/** Mueve el foco al contenido al cambiar de ruta, para lectores de pantalla y teclado. */
function useFocusMainOnNavigate() {
  const { pathname } = useLocation()
  const firstRender = useRef(true)
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false
      return
    }
    document.getElementById('contenido')?.focus({ preventScroll: true })
  }, [pathname])
}

/** Recuerda las entradas del historial que se dejan atrás, para no repetir las animaciones de entrada al volver. */
function useRememberLeftEntries() {
  const { key } = useLocation()
  const previous = useRef(key)
  useEffect(() => {
    if (previous.current !== key) markEntryLeft(previous.current)
    previous.current = key
  }, [key])
}

export function RootLayout() {
  useFocusMainOnNavigate()
  useRememberLeftEntries()
  usePageviews()
  return (
    // `overflow-x-clip` es una red de seguridad contra el scroll horizontal; a diferencia
    // de `hidden`, no crea un contenedor de scroll y no rompe la cabecera fija (sticky).
    <div className="flex min-h-svh flex-col overflow-x-clip">
      <a
        href="#contenido"
        // Aparece sobre la cabecera carbón: fondo blanco para que se distinga.
        className="sr-only z-50 rounded-md bg-ap-white px-4 py-2.5 font-semibold text-ap-ink shadow-overlay focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        Saltar al contenido
      </a>
      <SiteHeader />
      <main id="contenido" tabIndex={-1} className="flex-1 outline-none">
        <Suspense fallback={<PageFallback />}>
          <Outlet />
        </Suspense>
      </main>
      <SiteFooter />
      {/* Avisos del marketplace (compartir, carrito, reseñas). */}
      <Toaster position="bottom-center" closeButton />
      <ScrollRestoration />
    </div>
  )
}
