import type { CSSProperties, ElementType, ReactNode } from 'react'
import { cn } from '@/lib/utils'
import { RevealContext, STAGGER_MS, useCountUp, useRevealOnce, useRevealed } from '../lib/motion'

interface BentoTileProps {
  /** Posición en la rejilla: define el retraso del escalonado (60–90 ms por mosaico). */
  index?: number
  title: ReactNode
  /** Texto pequeño a la derecha del título (periodo, estado en vivo). */
  aside?: ReactNode
  className?: string
  children: ReactNode
}

/**
 * Mosaico de la rejilla «bento». Entra una sola vez al aparecer en pantalla (opacity + transform)
 * con el escalonado por posición; con `prefers-reduced-motion` aparece ya en su estado final.
 * Los hijos pueden leer `useRevealed()` para animar sus propias barras o contadores.
 */
export function BentoTile({ index = 0, title, aside, className, children }: BentoTileProps) {
  const [ref, revealed] = useRevealOnce<HTMLElement>()
  const style: CSSProperties = { transitionDelay: `${index * STAGGER_MS}ms` }
  return (
    <RevealContext.Provider value={revealed}>
      <section
        ref={ref}
        style={style}
        data-revealed={revealed}
        className={cn(
          'flex min-w-0 flex-col gap-3 rounded-xl bg-card p-4 ring-1 ring-foreground/10 sm:p-5',
          'translate-y-3 opacity-0 transition-[opacity,transform] duration-700 ease-out-thread data-[revealed=true]:translate-y-0 data-[revealed=true]:opacity-100 motion-reduce:translate-y-0 motion-reduce:opacity-100 motion-reduce:transition-none',
          className,
        )}
      >
        <header className="flex items-start justify-between gap-2">
          <h2 className="text-sm font-semibold text-muted-foreground">{title}</h2>
          {aside ? <div className="text-xs text-muted-foreground">{aside}</div> : null}
        </header>
        {children}
      </section>
    </RevealContext.Provider>
  )
}

/** Agrupa contenido que anima al entrar en pantalla (contadores, barras) sin ser un mosaico. */
export function RevealGroup({ className, children }: { className?: string; children: ReactNode }) {
  const [ref, revealed] = useRevealOnce<HTMLDivElement>()
  return (
    <RevealContext.Provider value={revealed}>
      <div ref={ref} className={className}>
        {children}
      </div>
    </RevealContext.Provider>
  )
}

/** Entrada escalonada de un elemento dentro de un mosaico (fila del mapa, barra del embudo). */
export function Reveal({
  index = 0,
  step = STAGGER_MS,
  as: Tag = 'div',
  className,
  children,
  ...rest
}: {
  index?: number
  step?: number
  as?: ElementType
  className?: string
  children?: ReactNode
} & Record<string, unknown>) {
  const revealed = useRevealed()
  return (
    <Tag
      {...rest}
      style={{ transitionDelay: `${index * step}ms` }}
      data-revealed={revealed}
      className={cn(
        'translate-y-2 opacity-0 transition-[opacity,transform] duration-500 ease-out-thread data-[revealed=true]:translate-y-0 data-[revealed=true]:opacity-100 motion-reduce:translate-y-0 motion-reduce:opacity-100 motion-reduce:transition-none',
        className,
      )}
    >
      {children}
    </Tag>
  )
}

/**
 * Cifra que rueda de 0 al valor en 1,2 s cuando su mosaico entra en pantalla. Lectores de
 * pantalla reciben solo el valor final (el texto que rueda va con `aria-hidden`).
 */
export function AnimatedNumber({ value, format, className }: { value: number; format: (value: number) => string; className?: string }) {
  const revealed = useRevealed()
  const shown = useCountUp(value, revealed, 1200)
  return (
    <span className={cn('tabular-nums', className)}>
      <span aria-hidden>{format(Math.round(shown))}</span>
      <span className="sr-only">{format(value)}</span>
    </span>
  )
}

/** Variación frente al periodo anterior, con texto (no solo color). */
export function Delta({ current, previous }: { current: number; previous: number }) {
  if (previous === 0) return <span className="text-xs text-muted-foreground">Sin periodo anterior para comparar</span>
  const change = ((current - previous) / previous) * 100
  const rounded = Math.round(Math.abs(change))
  if (rounded === 0) return <span className="text-xs text-muted-foreground">Igual que el periodo anterior</span>
  const up = change > 0
  return (
    <span className={cn('text-xs font-semibold', up ? 'text-stock' : 'text-destructive')}>
      {up ? '▲' : '▼'} {rounded} % {up ? 'más' : 'menos'} que el periodo anterior
    </span>
  )
}
