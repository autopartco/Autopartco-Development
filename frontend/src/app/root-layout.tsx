import { Suspense, useEffect, useRef } from 'react'
import { Outlet, ScrollRestoration, useLocation } from 'react-router'
import { SiteFooter } from './site-footer'
import { SiteHeader } from './site-header'
import { PageFallback } from './page-fallback'

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

export function RootLayout() {
  useFocusMainOnNavigate()
  return (
    // `overflow-x-clip` es una red de seguridad contra el scroll horizontal; a diferencia
    // de `hidden`, no crea un contenedor de scroll y no rompe la cabecera fija (sticky).
    <div className="flex min-h-svh flex-col overflow-x-clip">
      <a
        href="#contenido"
        // Aparece sobre la cabecera carbón: fondo blanco para que se distinga.
        className="sr-only z-50 rounded-md bg-white px-4 py-2.5 font-semibold text-foreground shadow-lg focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
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
      <ScrollRestoration />
    </div>
  )
}
