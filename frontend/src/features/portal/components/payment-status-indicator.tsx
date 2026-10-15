import { cn } from '@/lib/utils'

export type PaymentIndicatorState = 'pending' | 'approved' | 'declined' | 'waiting'

const TONE: Record<PaymentIndicatorState, string> = {
  pending: 'text-primary',
  approved: 'text-stock',
  declined: 'text-destructive',
  waiting: 'text-stock-low',
}

/** Seis destellos alrededor del anillo al aprobarse (radio 52 desde el centro 60,60). */
const BURST = Array.from({ length: 6 }, (_, index) => {
  const angle = (index * Math.PI) / 3 - Math.PI / 2
  return { cx: 60 + 52 * Math.cos(angle), cy: 60 + 52 * Math.sin(angle) }
})

interface PaymentStatusIndicatorProps {
  state: PaymentIndicatorState
  /** Estado en texto para lectores de pantalla (región `role="status"`). */
  label: string
  className?: string
}

/**
 * Indicador del resultado de un pago: arco que orbita mientras se verifica, anillo con check al
 * aprobarse, anillo con X al rechazarse y reloj si la confirmación tarda. Las animaciones viven en
 * `index.css` (`.psi-*`) y solo corren con `prefers-reduced-motion: no-preference`; sin ellas se ve
 * directamente el estado final. El SVG es decorativo: el estado se anuncia en `label`.
 */
export function PaymentStatusIndicator({ state, label, className }: PaymentStatusIndicatorProps) {
  return (
    <div className={cn('flex flex-col items-center', className)}>
      {/* `key` reinicia las animaciones al cambiar de estado. */}
      <svg key={state} viewBox="0 0 120 120" className={cn('size-24 overflow-visible', TONE[state])} fill="none" aria-hidden focusable="false">
        <circle cx="60" cy="60" r="36" fill="currentColor" fillOpacity="0.07" />
        <circle cx="60" cy="60" r="40" stroke="var(--color-border)" strokeWidth="8" />

        {state === 'pending' ? (
          <>
            <circle
              className="psi-orbit"
              cx="60"
              cy="60"
              r="40"
              stroke="currentColor"
              strokeWidth="8"
              strokeLinecap="round"
              pathLength={1}
              strokeDasharray="0.16 0.84"
            />
            <circle className="psi-pulse" cx="60" cy="60" r="7" fill="currentColor" />
          </>
        ) : null}

        {state === 'approved' ? (
          <>
            <circle className="psi-draw psi-ring" cx="60" cy="60" r="40" stroke="currentColor" strokeWidth="8" pathLength={1} transform="rotate(-90 60 60)" />
            <path className="psi-draw psi-mark" d="M43 61 L55 73 L78 48" stroke="currentColor" strokeWidth="8" strokeLinecap="round" strokeLinejoin="round" pathLength={1} />
            <g className="psi-burst">
              {BURST.map((dot) => (
                <circle key={`${dot.cx}-${dot.cy}`} cx={dot.cx} cy={dot.cy} r="3" fill="currentColor" />
              ))}
            </g>
          </>
        ) : null}

        {state === 'declined' ? (
          <>
            <circle className="psi-draw psi-ring psi-ring-fast" cx="60" cy="60" r="40" stroke="currentColor" strokeWidth="8" pathLength={1} transform="rotate(-90 60 60)" />
            <g className="psi-shake" stroke="currentColor" strokeWidth="8" strokeLinecap="round">
              <path className="psi-draw psi-mark" d="M47 47 L73 73" pathLength={1} />
              <path className="psi-draw psi-mark psi-mark-second" d="M73 47 L47 73" pathLength={1} />
            </g>
          </>
        ) : null}

        {state === 'waiting' ? (
          <>
            <circle className="psi-draw psi-ring" cx="60" cy="60" r="40" stroke="currentColor" strokeWidth="8" pathLength={1} transform="rotate(-90 60 60)" />
            <path d="M60 40 V60 L72 68" stroke="currentColor" strokeWidth="7" strokeLinecap="round" strokeLinejoin="round" />
          </>
        ) : null}
      </svg>
      <p role="status" aria-live="polite" className="sr-only">
        {label}
      </p>
    </div>
  )
}
