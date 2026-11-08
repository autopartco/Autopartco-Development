import type { ReactNode } from 'react'
import { Logo } from '@/components/common/logo'
import { cn } from '@/lib/utils'
import { PortalAccountMenu } from './portal-account-menu'

/** Logo real y la etiqueta del producto, sobre grafito. Se usa en la cabecera y en el menú móvil. */
export function PortalBrand({ className }: { className?: string }) {
  return (
    <div className={cn('flex min-w-0 items-center gap-2.5 sm:gap-3', className)}>
      <Logo tone="dark" to="/portal" label="Autopartco, portal de almacenes" />
      <span aria-hidden className="hidden h-5 w-px shrink-0 bg-ap-line-on-dark min-[400px]:block" />
      <span className="ap-index hidden truncate min-[400px]:inline">
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
  /** Herramientas antes del menú de cuenta (alertas y buscador de la paleta de comandos). */
  tools?: ReactNode
}

/**
 * Cabecera grafito del portal: la misma barra del marketplace (color, alto, logo y menú de cuenta)
 * con la etiqueta "Portal de almacenes", para que ambos se sientan el mismo producto.
 */
export function PortalHeader({ start, innerClassName, tools }: PortalHeaderProps) {
  return (
    <header className="ap-surface-dark sticky top-0 z-40 border-b border-ap-line-on-dark">
      <div className={cn('flex h-14 items-center gap-1 px-3 sm:gap-2 sm:px-6 lg:px-5', innerClassName)}>
        {start}
        <PortalBrand className="flex-1" />
        {tools}
        <PortalAccountMenu />
      </div>
    </header>
  )
}
