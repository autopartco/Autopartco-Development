import { Suspense, useEffect, useRef, useState, type RefObject } from 'react'
import { Link, NavLink, Outlet, ScrollRestoration, useLocation } from 'react-router'
import { MenuIcon, ShieldAlertIcon, ShieldCheckIcon, XIcon } from 'lucide-react'
import { Logo } from '@/components/common/logo'
import { Button } from '@/components/ui/button'
import { Sheet, SheetClose, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { Toaster } from '@/components/ui/sonner'
import { cn } from '@/lib/utils'
import { RequireSession } from '@/features/auth/components/require-session'
import { HeaderAccount } from '@/features/auth/components/account-menu'
import { useSession } from '@/features/auth/hooks'
import { ListSkeleton } from '@/features/portal/components/list-skeleton'
import { ADMIN_SECTION_GROUPS } from '../lib/sections'

function AdminLoading() {
  return (
    <div className="mx-auto w-full max-w-5xl px-4 py-16">
      <ListSkeleton rows={4} label="Cargando administración" />
    </div>
  )
}

/** Logo y etiqueta de la consola, sobre carbón (cabecera y menú móvil). */
function AdminBrand({ className }: { className?: string }) {
  return (
    <div className={cn('flex min-w-0 items-center gap-2.5 sm:gap-3', className)}>
      <Logo tone="dark" to="/admin" label="Autopartco, administración" />
      <span aria-hidden className="h-5 w-px shrink-0 bg-white/15" />
      <span className="label-mono truncate text-asphalt-muted">
        <span className="sm:hidden">Admin</span>
        <span className="hidden sm:inline">Administración root</span>
      </span>
    </div>
  )
}

/** Mismo menú del portal: grupos con etiqueta mono, ítem activo con barra roja de 2px. */
function AdminNav({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <nav aria-label="Administración" className="flex flex-col gap-5 px-3">
      {ADMIN_SECTION_GROUPS.map((group) => {
        const headingId = `admin-nav-${group.label}`
        return (
          <div key={group.label} className="flex flex-col gap-1">
            <p id={headingId} className="label-mono px-3 pb-1 text-asphalt-muted">
              {group.label}
            </p>
            <ul aria-labelledby={headingId} className="flex flex-col gap-0.5">
              {group.items.map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={`/admin/${item.to}`}
                    onClick={onNavigate}
                    className={({ isActive }) =>
                      cn(
                        'relative flex min-h-11 items-center gap-3 rounded-lg px-3 text-sm font-medium transition-colors hover:bg-header-hover hover:text-white lg:min-h-10',
                        'before:absolute before:inset-y-2 before:left-0 before:w-0.5 before:rounded-full before:bg-thread-on-asphalt before:opacity-0 before:transition-opacity',
                        isActive ? 'bg-header-hover text-white before:opacity-100' : 'text-asphalt-muted',
                      )
                    }
                  >
                    <item.icon className="size-[1.125rem] shrink-0" aria-hidden />
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        )
      })}
    </nav>
  )
}

function SidebarContent({ onNavigate, withBrand = false }: { onNavigate?: () => void; withBrand?: boolean }) {
  return (
    <div className="surface-asphalt flex h-full flex-col gap-6 py-5">
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
            className="size-11 shrink-0 text-white hover:bg-header-hover hover:text-white aria-expanded:bg-header-hover aria-expanded:text-white lg:hidden"
            aria-label="Abrir menú de administración"
          />
        }
      >
        <MenuIcon className="size-5" aria-hidden />
      </SheetTrigger>
      <SheetContent side="left" showCloseButton={false} className="w-[min(18rem,85vw)] gap-0 p-0 sm:max-w-72">
        <SheetHeader className="sr-only">
          <SheetTitle>Menú de administración</SheetTitle>
          <SheetDescription>Secciones de la consola root</SheetDescription>
        </SheetHeader>
        <SidebarContent withBrand onNavigate={() => setOpen(false)} />
        <SheetClose
          aria-label="Cerrar menú"
          render={<Button variant="ghost" className="absolute top-3 right-2 size-11 text-white hover:bg-header-hover hover:text-white" />}
        >
          <XIcon aria-hidden />
        </SheetClose>
      </SheetContent>
    </Sheet>
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
      <div className="flex items-center gap-2 font-semibold text-destructive">
        <ShieldAlertIcon className="size-5" aria-hidden />
        <h1 className="text-xl tracking-tight">Esta sección es solo para administración</h1>
      </div>
      <p className="text-muted-foreground">
        Tu cuenta no tiene el rol necesario para administrar la plataforma. Si crees que es un error, ingresa con otra cuenta.
      </p>
      <Button className="h-11 px-5" nativeButton={false} render={<Link to="/" />}>
        Volver al marketplace
      </Button>
    </main>
  )
}

function AdminShell({ email }: { email: string }) {
  const mainRef = useRef<HTMLElement>(null)
  useFocusMainOnNavigate(mainRef)
  return (
    <div className="flex min-h-svh flex-col bg-background">
      <a
        href="#contenido"
        className="sr-only z-[60] rounded-md bg-white px-4 py-2.5 font-semibold text-foreground shadow-lg focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        Saltar al contenido
      </a>
      <header className="surface-asphalt sticky top-0 z-40 bg-header">
        <div className="flex h-14 items-center gap-1 px-3 sm:gap-2 sm:px-6 lg:h-16 lg:px-8">
          <MobileMenu />
          <AdminBrand className="flex-1" />
          <HeaderAccount area="portal" />
        </div>
      </header>
      <div className="flex-1 lg:grid lg:grid-cols-[15rem_minmax(0,1fr)]">
        <aside className="hidden border-t border-asphalt-line bg-asphalt lg:block">
          <div className="sticky top-16 h-[calc(100svh-4rem)]">
            <SidebarContent />
          </div>
        </aside>
        <div className="flex min-w-0 flex-col">
          {/* Barra de contexto clara, como la del portal: quién administra y con qué rol. */}
          <div className="sticky top-14 z-30 flex h-14 items-center gap-3 border-b border-border bg-card px-4 sm:px-6 lg:top-16 lg:px-8">
            <div className="flex min-w-0 flex-1 items-center gap-2">
              <ShieldCheckIcon className="size-4 shrink-0 text-muted-foreground" aria-hidden />
              <span className="truncate text-sm font-semibold">{email}</span>
            </div>
            <span className="label-mono inline-flex h-7 shrink-0 items-center rounded-full bg-asphalt px-3 text-white">Root</span>
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

/** Raíz de `/admin`: exige sesión, muestra la carcasa del portal y (solo en pantalla) limita el contenido al rol ROOT. */
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
            <header className="surface-asphalt sticky top-0 z-40 bg-header">
              <div className="flex h-14 items-center gap-2 px-3 sm:px-6 lg:h-16 lg:px-8">
                <AdminBrand className="flex-1" />
                <HeaderAccount area="portal" />
              </div>
            </header>
            <NoAccess />
          </>
        )}
      </RequireSession>
      <Toaster position="bottom-right" closeButton />
      <ScrollRestoration />
    </>
  )
}
