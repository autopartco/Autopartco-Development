import { useId, type ReactNode } from 'react'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

interface ChartSectionProps {
  /** Pregunta que responde el gráfico («¿Cuánto vendiste cada semana?»). */
  title: ReactNode
  /** Contexto: período analizado y unidad («1 – 30 nov · COP»). */
  meta?: ReactNode
  /** Leyenda de las series, cuando hay más de una. */
  legend?: ReactNode
  /** Fuente de los datos y notas de lectura (estimaciones, exclusiones). */
  source?: ReactNode
  /** Cifra o hallazgo principal, arriba del gráfico. */
  lead?: ReactNode
  className?: string
  children: ReactNode
}

/**
 * Bloque de una visualización (§20.7): sección abierta con línea de estructura superior, título
 * que responde una pregunta, período y unidad, leyenda y fuente. Sin tarjeta ni sombra (§20.14).
 */
export function ChartSection({ title, meta, legend, source, lead, className, children }: ChartSectionProps) {
  const titleId = useId()
  return (
    <section aria-labelledby={titleId} className={cn('flex min-w-0 flex-col gap-3 border-t border-ap-line-strong pt-4', className)}>
      <header className="flex flex-col gap-1">
        <h2 id={titleId} className="text-base font-semibold text-ap-ink">
          {title}
        </h2>
        {meta ? <p className="ap-ref text-ap-muted">{meta}</p> : null}
      </header>
      {lead}
      {legend ? <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-ap-muted">{legend}</div> : null}
      {children}
      {source ? <p className="text-xs text-ap-muted">{source}</p> : null}
    </section>
  )
}

/** Muestra de una serie para la leyenda: línea continua, discontinua o área plana. */
export function LegendItem({ kind, color, children }: { kind: 'line' | 'dashed' | 'area' | 'bar'; color: string; children: ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      {kind === 'area' || kind === 'bar' ? (
        <span aria-hidden className="size-3 rounded-[2px]" style={{ backgroundColor: color, opacity: kind === 'area' ? 0.6 : 1 }} />
      ) : (
        <span aria-hidden className="w-5 border-t-2" style={{ borderColor: color, borderStyle: kind === 'dashed' ? 'dashed' : 'solid' }} />
      )}
      {children}
    </span>
  )
}

const BAR_HEIGHTS = [42, 58, 35, 66, 50, 72, 46, 62, 38, 70, 54, 60]

/** Carga de un gráfico con la forma del gráfico que vendrá (§20.9): barras, línea, matriz o lista. */
export function ChartSkeleton({ kind = 'bars', label, className }: { kind?: 'bars' | 'line' | 'matrix' | 'list'; label: string; className?: string }) {
  return (
    <div role="status" className={cn('flex flex-col gap-3 border-t border-ap-line pt-4', className)}>
      <span className="sr-only">{label}…</span>
      <div aria-hidden className="flex flex-col gap-3">
        <Skeleton className="h-4 w-56" />
        {kind === 'bars' ? (
          <div className="flex h-56 items-end gap-1.5 border-b border-ap-line">
            {BAR_HEIGHTS.map((height, index) => (
              <Skeleton key={index} className="flex-1 rounded-b-none" style={{ height: `${height}%` }} />
            ))}
          </div>
        ) : kind === 'line' ? (
          <Skeleton className="h-56 w-full" />
        ) : kind === 'matrix' ? (
          <div className="grid grid-cols-12 gap-0.5">
            {Array.from({ length: 84 }, (_, index) => (
              <Skeleton key={index} className="h-5 rounded-[2px]" />
            ))}
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            {Array.from({ length: 5 }, (_, index) => (
              <Skeleton key={index} className="h-8 w-full" />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

/** Gráfico sin datos: explica qué falta para que no parezca un error (§20.9). */
export function ChartEmpty({ children, className }: { children: ReactNode; className?: string }) {
  return <p className={cn('border-l-2 border-ap-line-strong py-1 pl-3 text-sm text-ap-muted', className)}>{children}</p>
}
