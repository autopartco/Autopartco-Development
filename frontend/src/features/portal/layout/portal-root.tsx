import { Suspense } from 'react'
import { Outlet, ScrollRestoration } from 'react-router'
import { Toaster } from '@/components/ui/sonner'
import { RequireSession } from '@/features/auth/components/require-session'
import { ListSkeleton } from '../components/list-skeleton'

function PortalLoading() {
  return (
    <div className="mx-auto w-full max-w-3xl px-4 py-16">
      <ListSkeleton rows={3} label="Cargando el portal" />
    </div>
  )
}

/**
 * Raíz de `/portal/*`: exige sesión (si falta, lleva a `/ingresar` conservando el destino) y
 * monta los avisos emergentes. No incluye la cabecera ni el pie del marketplace.
 */
export function PortalRoot() {
  return (
    <>
      <RequireSession fallback={<PortalLoading />}>
        <Suspense fallback={<PortalLoading />}>
          <Outlet />
        </Suspense>
      </RequireSession>
      <Toaster position="bottom-right" closeButton />
      <ScrollRestoration />
    </>
  )
}
