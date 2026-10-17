import { useState } from 'react'
import { Link, NavLink } from 'react-router'
import { MenuIcon, StoreIcon } from 'lucide-react'
import { useSellerCta } from '@/features/seller/hooks'
import { Container } from '@/components/common/page-header'
import { Logo } from '@/components/common/logo'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { cn } from '@/lib/utils'
import { CityPicker } from '@/features/location/components/city-picker'
import { SearchBar } from '@/features/marketplace/components/search-bar'
import { CartIndicator } from '@/features/cart/components/cart-indicator'
import { HeaderAccount, MobileMenuAccount } from '@/features/auth/components/account-menu'

const NAV = [
  { to: '/buscar', label: 'Productos' },
  { to: '/categorias', label: 'Categorías' },
  { to: '/tiendas', label: 'Tiendas' },
]

/** Enlace de la cabecera oscura: la página activa se marca con un tramo del hilo rojo. */
function navClass({ isActive }: { isActive: boolean }) {
  return cn(
    'relative inline-flex h-11 items-center rounded-lg px-3 text-sm font-medium transition-colors lg:h-10',
    'hover:bg-header-hover hover:text-white',
    'after:absolute after:inset-x-3 after:bottom-1 after:h-0.5 after:origin-left after:scale-x-0 after:rounded-full after:bg-thread-on-asphalt after:transition-transform after:duration-200 after:ease-out-snap',
    isActive ? 'text-white after:scale-x-100' : 'text-asphalt-muted',
  )
}

/** Enlace del menú móvil (panel claro). */
function menuLinkClass({ isActive }: { isActive: boolean }) {
  return cn(
    'flex min-h-12 items-center justify-between rounded-lg px-3 text-base font-semibold transition-colors hover:bg-concrete',
    isActive && 'bg-concrete',
  )
}

/** Acceso destacado a almacenes (desde md, con texto): registrar, ver la solicitud o ir al portal según la cuenta. */
function SellerHeaderLink() {
  const cta = useSellerCta()
  if (cta.state === 'loading') return null
  const label =
    cta.state === 'learn' ? 'Vender' : cta.state === 'review' ? 'Mi solicitud' : cta.state === 'register' ? 'Registrar almacén' : cta.label
  return (
    <Link
      to={cta.to}
      aria-label={label}
      title={label}
      className="hidden h-11 min-w-11 items-center justify-center gap-1.5 rounded-lg px-2.5 text-sm font-semibold text-white transition-colors hover:bg-header-hover md:inline-flex lg:h-10"
    >
      <StoreIcon className="size-4 text-brand-on-asphalt" aria-hidden />
      <span className="hidden whitespace-nowrap md:inline">{label}</span>
    </Link>
  )
}

/** Menú de navegación en pantallas estrechas, donde los enlaces no caben junto al logo y la ciudad. */
function MobileMenu({ className }: { className?: string }) {
  const [open, setOpen] = useState(false)
  const cta = useSellerCta()
  const close = () => setOpen(false)
  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger
        render={
          <Button
            variant="ghost"
            className={cn('size-11 text-white hover:bg-header-hover hover:text-white aria-expanded:bg-header-hover aria-expanded:text-white', className)}
            aria-label="Abrir menú"
          />
        }
      >
        <MenuIcon className="size-5" aria-hidden />
      </SheetTrigger>
      <SheetContent side="right" className="gap-0 p-0">
        <SheetHeader className="border-b border-border px-5 py-4 pr-16">
          <SheetTitle className="text-lg font-extrabold tracking-tight">Menú</SheetTitle>
          <SheetDescription className="sr-only">Secciones de Autopartco</SheetDescription>
        </SheetHeader>
        <nav aria-label="Principal" className="flex flex-col gap-1 px-3 py-4">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} className={menuLinkClass} onClick={close}>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <section aria-labelledby="menu-cuenta" className="mx-5 mt-2 flex flex-col gap-3 border-t border-border pt-5">
          <h2 id="menu-cuenta" className="label-mono text-muted-foreground">
            Tu cuenta
          </h2>
          <MobileMenuAccount onNavigate={close} />
        </section>
        <div className="mx-5 mt-5 flex flex-col items-start gap-1 border-t border-border pt-5">
          <p className="label-mono text-muted-foreground">Para almacenes</p>
          <Link
            to={cta.state === 'loading' ? '/vender/registro' : cta.to}
            onClick={close}
            className="inline-flex min-h-11 items-center gap-2 font-semibold text-brand underline-offset-4 hover:underline"
          >
            <StoreIcon className="size-5" aria-hidden />
            {cta.state === 'loading' ? 'Registra tu almacén' : cta.label}
          </Link>
        </div>
      </SheetContent>
    </Sheet>
  )
}

/**
 * Cabecera carbón. En móvil y tablet son dos filas (logo y acciones: ciudad, cuenta y menú; buscador a
 * todo el ancho) y desde 2xl una sola. El buscador va justo después del logo en
 * el orden del documento: es lo primero que se usa.
 */
export function SiteHeader() {
  return (
    <header className="surface-asphalt sticky top-0 z-40 bg-header">
      <Container className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-2 [grid-template-areas:'logo_actions'_'search_search'] 2xl:h-16 2xl:grid-cols-[auto_minmax(0,1fr)_auto] 2xl:gap-x-8 2xl:[grid-template-areas:'logo_search_actions']">
        <div className="flex h-14 min-w-0 items-center [grid-area:logo] 2xl:h-auto">
          <Logo tone="dark" />
        </div>
        <SearchBar tone="dark" shortcut className="pb-3 [grid-area:search] 2xl:max-w-2xl 2xl:pb-0" />
        <div className="flex items-center gap-0.5 [grid-area:actions]">
          {/* Enlaces desde lg; por debajo van en el menú para dejar sitio a la ciudad y a la cuenta. */}
          <nav aria-label="Principal" className="hidden items-center gap-0.5 lg:flex">
            {NAV.map((item) => (
              <NavLink key={item.to} to={item.to} className={navClass}>
                {item.label}
              </NavLink>
            ))}
          </nav>
          <SellerHeaderLink />
          <span className="mx-1.5 hidden h-5 w-px bg-white/15 lg:block" aria-hidden />
          <CityPicker />
          <CartIndicator />
          <HeaderAccount />
          <MobileMenu className="lg:hidden" />
        </div>
      </Container>
    </header>
  )
}
