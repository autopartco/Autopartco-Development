import type { ReactNode } from 'react'
import { Logo } from '@/components/common/logo'
import { cn } from '@/lib/utils'
import { PortalAccountMenu } from './portal-account-menu'

/** Logo real y la etiqueta del producto, sobre carbón. Se usa en la cabecera y en el menú móvil. */
export function PortalBrand({ className }: { className?: string }) {
  return (
    <div className={cn('flex min-w-0 items-center gap-2.5 sm:gap-3', className)}>
      <Logo tone="dark" to="/portal" label="Autopartco, portal de almacenes" />
      <span aria-hidden className="h-5 w-px shrink-0 bg-white/15" />
      <span className="label-mono truncate text-asphalt-muted">
        <span className="sm:hidden">Portal</span>
        <span className="hidden sm:inline">Portal de almacenes</span>
      </span>
    </div>
  )
}

interface PortalHeaderProps {
  /** Antes del logo (botón del menú móvil). */
  start?: ReactNode
  /** Clases del contenedor interno (ancho máximo y márgenes de cada pantalla). */
  innerClassName?: string
  /** Herramientas antes del menú de cuenta (buscador de la paleta de comandos). */
  tools?: ReactNode
}

/**
 * Cabecera carbón del portal: la misma barra del marketplace (color, alto, logo y menú de cuenta)
 * con la etiqueta "Portal de almacenes", para que ambos se sientan el mismo producto.
 */
export function PortalHeader({ start, innerClassName, tools }: PortalHeaderProps) {
  return (
    <header className="surface-asphalt sticky top-0 z-40 bg-header">
      <div className={cn('flex h-14 items-center gap-1 px-3 sm:gap-2 sm:px-6 lg:h-16 lg:px-8', innerClassName)}>
        {start}
        <PortalBrand className="flex-1" />
        {tools}
        <PortalAccountMenu />
      </div>
    </header>
  )
}
