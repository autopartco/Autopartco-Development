import { Suspense, useEffect, useRef, type ReactNode } from 'react'
import { Skeleton } from '@/components/ui/skeleton'
import { NavLink, Outlet, useLocation } from 'react-router'
import { Container, PageIntro } from '@/components/common/page-header'
import { cn } from '@/lib/utils'
import { ACCOUNT_LINKS, ACCOUNT_SHORTCUTS } from './links'

const TITLES: Record<string, string> = {
  '/cuenta': 'Mi cuenta',
  '/cuenta/perfil': 'Datos personales',
  '/cuenta/seguridad': 'Seguridad',
  '/cuenta/direcciones': 'Direcciones',
  '/cuenta/preferencias': 'Preferencias',
  '/cuenta/privacidad': 'Privacidad y datos',
}

function linkClass({ isActive }: { isActive: boolean }) {
  return cn(
    'flex min-h-11 items-center gap-2.5 rounded-md px-3 text-sm font-medium whitespace-nowrap transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50',
    isActive ? 'bg-ap-graphite text-ap-white' : 'border border-ap-line text-ap-ink hover:bg-ap-paper lg:border-transparent',
  )
}

/** Navegación lateral (en móvil, lista arriba) y el contenido de la sección. */
export function AccountLayout() {
  const { pathname } = useLocation()
  const navRef = useRef<HTMLElement>(null)
  // En la fila móvil, deja a la vista el apartado abierto.
  useEffect(() => {
    navRef.current?.querySelector('a[aria-current="page"]')?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
  }, [pathname])
  const path = pathname.replace(/\/+$/, '') || '/cuenta'
  const title = TITLES[path] ?? 'Mi cuenta'
  const crumbs = path === '/cuenta' ? [{ label: 'Inicio', to: '/' }, { label: 'Mi cuenta' }] : [{ label: 'Inicio', to: '/' }, { label: 'Mi cuenta', to: '/cuenta' }, { label: title }]
  return (
    <Container className="py-6 sm:py-8">
      <title>{`${title} | Autopartco`}</title>
      <PageIntro crumbs={crumbs} label="Tu cuenta" title={title} />
      <div className="grid gap-8 lg:grid-cols-[15rem_minmax(0,1fr)]">
        <nav ref={navRef} aria-label="Secciones de tu cuenta" className="-mx-4 flex min-w-0 flex-col gap-4 sm:-mx-6 lg:mx-0">
          {/* Móvil: fila desplazable para no empujar el contenido; escritorio: columna. */}
          <ul className="flex gap-1 overflow-x-auto px-4 pb-1 sm:px-6 lg:flex-col lg:gap-0.5 lg:overflow-visible lg:px-0 lg:pb-0">
            {ACCOUNT_LINKS.map((link) => (
              <li key={link.to} className="shrink-0">
                <NavLink to={link.to} end={link.end} className={linkClass}>
                  <link.icon className="size-4 shrink-0" aria-hidden />
                  {link.label}
                </NavLink>
              </li>
            ))}
          </ul>
          <ul className="hidden flex-col gap-0.5 border-t border-ap-line pt-3 lg:flex">
            {ACCOUNT_SHORTCUTS.map((link) => (
              <li key={link.to}>
                <NavLink to={link.to} className={linkClass}>
                  <link.icon className="size-4 shrink-0" aria-hidden />
                  {link.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <div className="min-w-0">
          <Suspense fallback={<Skeleton className="h-64 max-w-2xl" />}>
            <Outlet />
          </Suspense>
        </div>
      </div>
    </Container>
  )
}

/** Bloque de una sección: línea superior y título, sin tarjeta (§6.3, §8.3). */
export function AccountSection({ id, title, description, children, aside }: { id: string; title: string; description?: ReactNode; children: ReactNode; aside?: ReactNode }) {
  return (
    <section aria-labelledby={id} className="flex flex-col gap-4 border-t border-ap-line-strong pt-4 pb-8 first:border-t-0 first:pt-0">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h2 id={id} className="text-base font-semibold text-ap-ink">
            {title}
          </h2>
          {description ? <div className="max-w-prose text-sm text-ap-muted">{description}</div> : null}
        </div>
        {aside}
      </div>
      {children}
    </section>
  )
}
