import { Suspense } from 'react'
import { Link, Outlet, ScrollRestoration } from 'react-router'
import { ShieldAlertIcon } from 'lucide-react'
import { Logo } from '@/components/common/logo'
import { Button } from '@/components/ui/button'
import { Toaster } from '@/components/ui/sonner'
import { RequireSession } from '@/features/auth/components/require-session'
import { HeaderAccount } from '@/features/auth/components/account-menu'
import { useSession } from '@/features/auth/hooks'
import { ListSkeleton } from '@/features/portal/components/list-skeleton'

function AdminLoading() {
  return (
    <div className="mx-auto w-full max-w-5xl px-4 py-16">
      <ListSkeleton rows={4} label="Cargando administración" />
    </div>
  )
}

function AdminHeader() {
  return (
    <header className="surface-asphalt sticky top-0 z-40 bg-header">
      <div className="mx-auto flex h-14 w-full max-w-7xl items-center gap-2 px-3 sm:gap-3 sm:px-6 lg:h-16 lg:px-8">
        <div className="flex min-w-0 flex-1 items-center gap-2.5 sm:gap-3">
          <Logo tone="dark" to="/admin" label="Autopartco, administración" />
          <span aria-hidden className="h-5 w-px shrink-0 bg-white/15" />
          <span className="label-mono truncate text-asphalt-muted">Administración</span>
        </div>
        <HeaderAccount area="portal" />
      </div>
    </header>
  )
}

/** Quien no es ROOT ve esto: la interfaz solo oculta; el servidor responde 403 igualmente. */
function NoAccess() {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-col items-start gap-4 px-4 py-16">
      <div className="flex items-center gap-2 font-semibold text-destructive">
        <ShieldAlertIcon className="size-5" aria-hidden />
        <h1 className="text-xl tracking-tight">Esta sección es solo para administración</h1>
      </div>
      <p className="text-muted-foreground">
        Tu cuenta no tiene el rol necesario para revisar solicitudes de almacenes. Si crees que es un error, ingresa con otra cuenta.
      </p>
      <Button className="h-11 px-5" nativeButton={false} render={<Link to="/" />}>
        Volver al marketplace
      </Button>
    </main>
  )
}

/** Raíz de `/admin`: exige sesión, muestra la carcasa y (solo en pantalla) limita el contenido al rol ROOT. */
export function AdminRoot() {
  const session = useSession()
  const isRoot = session?.roles.includes('ROLE_ROOT') ?? false
  return (
    <>
      <RequireSession fallback={<AdminLoading />}>
        <AdminHeader />
        {isRoot ? (
          <Suspense fallback={<AdminLoading />}>
            <Outlet />
          </Suspense>
        ) : (
          <NoAccess />
        )}
      </RequireSession>
      <Toaster position="bottom-right" closeButton />
      <ScrollRestoration />
    </>
  )
}
