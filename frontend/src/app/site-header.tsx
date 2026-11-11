import { useState } from 'react'
import { Link, NavLink } from 'react-router'
import { MenuIcon, StoreIcon } from 'lucide-react'
import { useSellerCta } from '@/features/seller/hooks'
import { Container } from '@/components/common/page-header'
import { Logo } from '@/components/common/logo'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { cn } from '@/lib/utils'
import { LocationPicker } from '@/features/location/components/location-picker'
import { SearchBar } from '@/features/marketplace/components/search-bar'
import { CartIndicator } from '@/features/cart/components/cart-indicator'
import { HeaderAccount, MobileMenuAccount } from '@/features/auth/components/account-menu'

const NAV = [
  { to: '/buscar', label: 'Productos' },
  { to: '/categorias', label: 'Categorías' },
  { to: '/tiendas', label: 'Tiendas' },
]

/**
 * Enlace de la cabecera grafito (§3.5): la página activa se marca con una línea roja
 * (`ap-red-on-dark`, 4.6:1 sobre grafito) y texto blanco; el resto en gris claro.
 */
function navClass({ isActive }: { isActive: boolean }) {
  return cn(
    'relative inline-flex h-11 shrink-0 items-center whitespace-nowrap rounded-md px-2.5 text-sm font-medium transition-colors duration-150 lg:h-10',
    'hover:bg-sidebar-accent hover:text-ap-white',
    'after:absolute after:inset-x-2.5 after:-bottom-0.5 after:h-0.5 after:bg-ap-red-on-dark after:opacity-0 after:transition-opacity after:duration-150',
    isActive ? 'text-ap-white after:opacity-100' : 'text-ap-on-dark-muted',
  )
}

/** Enlace del menú móvil (panel claro): el activo lleva fondo papel y línea de acento roja. */
function menuLinkClass({ isActive }: { isActive: boolean }) {
  return cn(
    'flex min-h-12 items-center justify-between rounded-md px-3 text-base font-semibold text-ap-ink transition-colors hover:bg-ap-paper',
    isActive && 'bg-ap-paper shadow-[inset_3px_0_0_var(--ap-red)]',
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
      className="hidden h-11 min-w-11 shrink-0 items-center justify-center gap-1.5 rounded-md px-2.5 text-sm font-semibold text-ap-white transition-colors hover:bg-sidebar-accent md:inline-flex lg:h-10"
    >
      <StoreIcon className="size-4 text-ap-red-on-dark" aria-hidden />
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
            className={cn('size-11 text-ap-white hover:bg-sidebar-accent hover:text-ap-white aria-expanded:bg-sidebar-accent aria-expanded:text-ap-white', className)}
            aria-label="Abrir menú"
          />
        }
      >
        <MenuIcon className="size-5" aria-hidden />
      </SheetTrigger>
      <SheetContent side="right" className="gap-0 p-0">
        <SheetHeader className="border-b border-ap-line px-5 py-4 pr-16">
          <SheetTitle>Menú</SheetTitle>
          <SheetDescription className="sr-only">Secciones de Autopartco</SheetDescription>
        </SheetHeader>
        <nav aria-label="Principal" className="flex flex-col gap-1 px-3 py-4">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} className={menuLinkClass} onClick={close}>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <section aria-labelledby="menu-cuenta" className="mx-5 mt-2 flex flex-col gap-3 border-t border-ap-line pt-5">
          <h2 id="menu-cuenta" className="ap-index">
            Tu cuenta
          </h2>
          <MobileMenuAccount onNavigate={close} />
        </section>
        <div className="mx-5 mt-5 flex flex-col items-start gap-1 border-t border-ap-line pt-5">
          <p className="ap-index">Para almacenes</p>
          <Link
            to={cta.state === 'loading' ? '/vender/registro' : cta.to}
            onClick={close}
            className="inline-flex min-h-11 items-center gap-2 font-semibold text-ap-red underline-offset-4 hover:text-ap-red-hover hover:underline"
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
 * Cabecera grafito. En móvil y tablet son dos filas (logo y acciones: ciudad, cuenta y menú; buscador a
 * todo el ancho) y desde xl una sola: logo, buscador, navegación y acciones. El buscador va justo
 * después del logo en el orden del documento: es lo primero que se usa.
 */
export function SiteHeader() {
  return (
    <header className="ap-surface-dark sticky top-0 z-40 bg-ap-graphite">
      <Container className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-2 [grid-template-areas:'logo_actions'_'search_search'] xl:h-16 xl:grid-cols-[auto_minmax(0,1fr)_auto_auto] xl:gap-x-4 xl:[grid-template-areas:'logo_search_nav_actions'] 2xl:gap-x-6">
        <div className="flex h-14 min-w-0 items-center [grid-area:logo] xl:h-auto">
          <Logo tone="dark" />
        </div>
        <SearchBar tone="dark" shortcut className="pb-3 [grid-area:search] xl:min-w-64 xl:max-w-2xl xl:pb-0" />
        {/* Enlaces desde xl, en la misma fila; por debajo van en el menú para dejar sitio a la ciudad y a la cuenta. */}
        <nav aria-label="Principal" className="hidden items-center gap-0 [grid-area:nav] xl:flex">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} className={navClass}>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="flex items-center gap-0.5 [grid-area:actions]">
          <SellerHeaderLink />
          <span className="mx-1.5 hidden h-5 w-px bg-ap-line-on-dark xl:block" aria-hidden />
          <LocationPicker />
          <CartIndicator />
          <HeaderAccount />
          <MobileMenu className="xl:hidden" />
        </div>
      </Container>
    </header>
  )
}
