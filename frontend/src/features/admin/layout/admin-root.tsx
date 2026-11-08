import { Suspense, useEffect, useRef, useState, type ReactNode, type RefObject } from 'react'
import { Link, NavLink, Outlet, ScrollRestoration, useLocation } from 'react-router'
import { MenuIcon, ShieldAlertIcon, XIcon } from 'lucide-react'
import { Logo } from '@/components/common/logo'
import { Button } from '@/components/ui/button'
import { Sheet, SheetClose, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { Toaster } from '@/components/ui/sonner'
import { formatNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import { RequireSession } from '@/features/auth/components/require-session'
import { HeaderAccount } from '@/features/auth/components/account-menu'
import { useSession } from '@/features/auth/hooks'
import { ListSkeleton } from '@/features/portal/components/list-skeleton'
import { usePendingApplications } from '../hooks'
import { ADMIN_SECTION_GROUPS } from '../lib/sections'

function AdminLoading() {
  return (
    <div className="mx-auto w-full max-w-5xl px-4 py-16">
      <ListSkeleton rows={4} label="Cargando administración" />
    </div>
  )
}

/** Logo real y etiqueta de la consola sobre grafito (cabecera y menú móvil), igual que el portal. */
function AdminBrand({ className }: { className?: string }) {
  return (
    <div className={cn('flex min-w-0 items-center gap-2.5 sm:gap-3', className)}>
      <Logo tone="dark" to="/admin" label="Autopartco, administración" />
      <span aria-hidden className="h-5 w-px shrink-0 bg-ap-line-on-dark" />
      <span className="ap-index truncate">
        <span className="sm:hidden">Admin</span>
        <span className="hidden sm:inline">Administración</span>
      </span>
    </div>
  )
}

/**
 * Menú de la consola sobre grafito (§7.3/§7.4): grupos con índice mono; el ítem activo lleva fondo
 * tenue, texto blanco y la línea `ap-red-on-dark` (4.6:1 sobre grafito), además de `aria-current`.
 * «Solicitudes» muestra cuántas esperan revisión (dato del servidor, en texto).
 */
function AdminNav({ onNavigate }: { onNavigate?: () => void }) {
  const pending = usePendingApplications().data?.length ?? 0
  return (
    <nav aria-label="Administración" className="flex flex-col gap-5 px-3">
      {ADMIN_SECTION_GROUPS.map((group) => {
        const headingId = `admin-nav-${group.label}`
        return (
          <div key={group.label} className="flex flex-col gap-1">
            <p id={headingId} className="ap-index px-3 pb-1">
              {group.label}
            </p>
            <ul aria-labelledby={headingId} className="flex flex-col">
              {group.items.map((item) => {
                const count = item.to === 'solicitudes' ? pending : 0
                return (
                  <li key={item.to}>
                    <NavLink
                      to={`/admin/${item.to}`}
                      onClick={onNavigate}
                      className={({ isActive }) =>
                        cn(
                          'relative flex min-h-11 items-center gap-3 rounded-md px-3 text-sm font-medium transition-colors duration-150 hover:bg-sidebar-accent hover:text-ap-white lg:min-h-10',
                          'before:absolute before:inset-y-1.5 before:left-0 before:w-0.5 before:bg-ap-red-on-dark before:opacity-0',
                          isActive ? 'bg-sidebar-accent text-ap-white before:opacity-100' : 'text-ap-on-dark-muted',
                        )
                      }
                    >
                      <item.icon className="size-4 shrink-0" aria-hidden />
                      <span className="flex-1">{item.label}</span>
                      {count > 0 ? (
                        <span className="ap-ref text-ap-white">
                          <span className="sr-only">, por revisar: </span>
                          {formatNumber(count)}
                        </span>
                      ) : null}
                    </NavLink>
                  </li>
                )
              })}
            </ul>
          </div>
        )
      })}
    </nav>
  )
}

function SidebarContent({ onNavigate, withBrand = false }: { onNavigate?: () => void; withBrand?: boolean }) {
  return (
    <div className="ap-surface-dark flex h-full flex-col gap-5 py-4">
      {withBrand ? <AdminBrand className="h-11 pr-14 pl-5" /> : null}
      <div className="flex-1 overflow-y-auto">
        <AdminNav onNavigate={onNavigate} />
      </div>
    </div>
  )
}

function MobileMenu() {
  const [open, setOpen] = useState(false)
  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger
        render={
          <Button
            variant="ghost"
            className="size-11 shrink-0 text-ap-white hover:bg-sidebar-accent hover:text-ap-white aria-expanded:bg-sidebar-accent aria-expanded:text-ap-white lg:hidden"
            aria-label="Abrir menú de administración"
          />
        }
      >
        <MenuIcon className="size-5" aria-hidden />
      </SheetTrigger>
      <SheetContent side="left" showCloseButton={false} className="w-[min(16rem,85vw)] gap-0 border-ap-line-on-dark p-0 sm:max-w-64">
        <SheetHeader className="sr-only">
          <SheetTitle>Menú de administración</SheetTitle>
          <SheetDescription>Secciones de la consola de administración</SheetDescription>
        </SheetHeader>
        <SidebarContent withBrand onNavigate={() => setOpen(false)} />
        <SheetClose
          aria-label="Cerrar menú"
          render={<Button variant="ghost" className="absolute top-2 right-2 size-11 text-ap-white hover:bg-sidebar-accent hover:text-ap-white" />}
        >
          <XIcon aria-hidden />
        </SheetClose>
      </SheetContent>
    </Sheet>
  )
}

/** Índice de la sección abierta (`PLATAFORMA / SOLICITUDES`): orienta sin repetir el título de la página. */
function SectionLabel() {
  const { pathname } = useLocation()
  const segment = pathname.split('/')[2] ?? ''
  const group = ADMIN_SECTION_GROUPS.find((candidate) => candidate.items.some((item) => item.to === segment))
  const item = group?.items.find((candidate) => candidate.to === segment)
  if (!group || !item) return null
  return (
    <p className="ap-index min-w-0 truncate">
      <span className="sr-only">Sección: </span>
      {group.label} / {item.label}
    </p>
  )
}

/** Mueve el foco al contenido al cambiar de pantalla, para lectores de pantalla y teclado. */
function useFocusMainOnNavigate(mainRef: RefObject<HTMLElement | null>) {
  const { pathname } = useLocation()
  const first = useRef(true)
  useEffect(() => {
    if (first.current) {
      first.current = false
      return
    }
    mainRef.current?.focus({ preventScroll: true })
  }, [pathname, mainRef])
}

/** Quien no es ROOT ve esto: la interfaz solo oculta; el servidor responde 403 igualmente. */
function NoAccess() {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-col items-start gap-4 px-4 py-16">
      <div className="flex items-center gap-2 text-ap-danger">
        <ShieldAlertIcon className="size-5" aria-hidden />
        <h1 className="text-block-title">Esta sección es solo para administración</h1>
      </div>
      <p className="text-ap-muted">
        Tu cuenta no tiene el rol necesario para administrar la plataforma. Si crees que es un error, ingresa con otra cuenta.
      </p>
      <Button className="h-11 px-5" nativeButton={false} render={<Link to="/" />}>
        Volver al marketplace
      </Button>
    </main>
  )
}

function AdminHeader({ start }: { start?: ReactNode }) {
  return (
    <header className="ap-surface-dark sticky top-0 z-40 border-b border-ap-line-on-dark">
      <div className="flex h-14 items-center gap-1 px-3 sm:gap-2 sm:px-6 lg:px-5">
        {start}
        <AdminBrand className="flex-1" />
        <HeaderAccount area="portal" />
      </div>
    </header>
  )
}

function AdminShell({ email }: { email: string }) {
  const mainRef = useRef<HTMLElement>(null)
  useFocusMainOnNavigate(mainRef)
  return (
    <div className="flex min-h-svh flex-col bg-ap-white">
      <a
        href="#contenido"
        className="sr-only z-[60] rounded-md bg-ap-white px-4 py-2.5 font-semibold text-ap-ink shadow-overlay focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        Saltar al contenido
      </a>
      <AdminHeader start={<MobileMenu />} />
      <div className="flex-1 lg:grid lg:grid-cols-[14rem_minmax(0,1fr)]">
        <aside className="hidden bg-ap-graphite lg:block">
          <div className="sticky top-14 h-[calc(100svh-3.5rem)]">
            <SidebarContent />
          </div>
        </aside>
        <div className="flex min-w-0 flex-col">
          {/* Barra de contexto clara (§7.3): sección abierta y quién administra, con su rol. */}
          <div className="sticky top-14 z-30 flex min-h-14 items-center gap-3 border-b border-ap-line bg-ap-white px-4 py-1.5 sm:px-6 lg:px-8">
            <div className="flex min-w-0 flex-1 items-center gap-3">
              <SectionLabel />
            </div>
            <p className="flex min-w-0 items-center gap-2 text-sm">
              <span className="sr-only">Sesión de </span>
              <span className="truncate font-medium text-ap-ink">{email}</span>
              <span className="ap-index shrink-0 rounded-sm border border-ap-line-strong px-1.5 py-0.5 text-ap-ink">Root</span>
            </p>
          </div>
          <main id="contenido" ref={mainRef} tabIndex={-1} className="w-full flex-1 outline-none">
            <Suspense fallback={<AdminLoading />}>
              <Outlet />
            </Suspense>
          </main>
        </div>
      </div>
    </div>
  )
}

/** Raíz de `/admin`: exige sesión, muestra la carcasa de la consola y (solo en pantalla) limita el contenido al rol ROOT. */
export function AdminRoot() {
  const session = useSession()
  const isRoot = session?.roles.includes('ROLE_ROOT') ?? false
  return (
    <>
      <RequireSession fallback={<AdminLoading />}>
        {isRoot && session ? (
          <AdminShell email={session.user.email} />
        ) : (
          <>
            <AdminHeader />
            <NoAccess />
          </>
        )}
      </RequireSession>
      <Toaster position="bottom-right" closeButton />
      <ScrollRestoration />
    </>
  )
}
