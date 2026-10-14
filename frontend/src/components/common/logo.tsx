import { useState } from 'react'
import { Link } from 'react-router'
import { cn } from '@/lib/utils'

interface LogoProps {
  /** Fondo sobre el que va el logo: cambia el color de "Autopart" y el rojo de "co". */
  tone?: 'light' | 'dark'
  className?: string
}

/**
 * Imagotipo de Autopartco: isotipo real (`public/logo.svg`, fondo transparente)
 * directamente sobre el fondo, sin placa ni sombra, y el nombre al lado con
 * "co" en rojo. Es un único enlace al inicio; el nombre visible se oculta a los
 * lectores de pantalla porque ya lo anuncia `aria-label`.
 * Si la imagen no carga, queda solo el nombre.
 */
export function Logo({ tone = 'light', className }: LogoProps) {
  const [failed, setFailed] = useState(false)
  const dark = tone === 'dark'
  return (
    <Link
      to="/"
      aria-label="Autopartco, ir al inicio"
      className={cn('inline-flex shrink-0 items-center gap-1.5 rounded-md lg:gap-2', className)}
    >
      {failed ? null : (
        <img
          src="/logo.svg"
          alt=""
          width={40}
          height={40}
          decoding="async"
          className="size-9 shrink-0 object-contain lg:size-10"
          onError={() => setFailed(true)}
        />
      )}
      <span
        aria-hidden
        className={cn(
          'text-[1.1875rem] leading-none font-extrabold tracking-[-0.035em] lg:text-[1.375rem]',
          dark ? 'text-white' : 'text-foreground',
        )}
      >
        Autopart<span className={dark ? 'text-brand-on-asphalt' : 'text-brand'}>co</span>
      </span>
    </Link>
  )
}
