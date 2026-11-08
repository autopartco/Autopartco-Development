import type { ReactNode } from 'react'
import { FlaskConicalIcon } from 'lucide-react'
import { Container, PageIntro, type Crumb } from '@/components/common/page-header'
import { DATA_SOURCE } from '@/lib/api'
import { cn } from '@/lib/utils'

interface AuthShellProps {
  crumbs: Crumb[]
  label?: ReactNode
  title: ReactNode
  description?: ReactNode
  children: ReactNode
  /** Ancho de la tarjeta: `narrow` para ingresar o recuperar, `wide` para registros largos. */
  width?: 'narrow' | 'wide'
}

/** Aviso honesto en modo `mock`: no hay cuentas reales ni se envían datos. */
export function SimulatedNotice({ className }: { className?: string }) {
  if (DATA_SOURCE !== 'mock') return null
  return (
    <p className={cn('flex items-start gap-2 rounded-md bg-ap-paper px-3 py-2.5 text-xs text-ap-muted', className)}>
      <FlaskConicalIcon className="mt-0.5 size-3.5 shrink-0" aria-hidden />
      <span>
        Modo de prueba: no se crea ninguna cuenta real ni se envían datos ni archivos a Autopartco.
      </span>
    </p>
  )
}

/**
 * Página de cuenta: encabezado común y el formulario en un único panel con borde fino
 * (§6.3: sin sombra, sin ilustraciones ni fondos decorativos; nada de paneles anidados).
 */
export function AuthShell({ crumbs, label, title, description, children, width = 'narrow' }: AuthShellProps) {
  return (
    <Container className="py-6 sm:py-8">
      <div className={cn('mx-auto w-full', width === 'narrow' ? 'max-w-lg' : 'max-w-3xl')}>
        <PageIntro crumbs={crumbs} label={label} title={title} description={description} className="mb-6 sm:mb-8" />
        <div className="rounded-xl border border-ap-line bg-ap-white p-5 sm:p-8">
          {children}
        </div>
      </div>
    </Container>
  )
}
