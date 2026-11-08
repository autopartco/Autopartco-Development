import { useId, useState } from 'react'
import { CheckCircle2Icon, ChevronDownIcon, CircleDashedIcon, TriangleAlertIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useMediaQuery } from '@/lib/use-media-query'
import { cn } from '@/lib/utils'
import { checklistSummary, missingCount, type ChecklistItem, type ChecklistState, type StepId } from '../lib/publish-checklist'

const STATE_TEXT: Record<ChecklistState, string> = { ready: 'Listo', missing: 'Falta', warn: 'Aviso' }

function StateIcon({ state }: { state: ChecklistState }) {
  if (state === 'ready') return <CheckCircle2Icon className="mt-0.5 size-5 shrink-0 text-ap-success" aria-hidden />
  if (state === 'warn') return <TriangleAlertIcon className="mt-0.5 size-5 shrink-0 text-ap-warning" aria-hidden />
  return <CircleDashedIcon className="mt-0.5 size-5 shrink-0 text-ap-danger" aria-hidden />
}

function ItemList({ items, onGoTo }: { items: ChecklistItem[]; onGoTo: (step: StepId) => void }) {
  return (
    <ul className="flex flex-col gap-2.5">
      {items.map((item) => (
        <li key={item.id} className="flex items-start gap-2.5">
          <StateIcon state={item.state} />
          <div className="flex min-w-0 flex-1 flex-col text-sm">
            <span className={cn('font-medium text-ap-ink', item.state === 'ready' && 'text-ap-muted')}>
              <span className="sr-only">{STATE_TEXT[item.state]}: </span>
              {item.label}
            </span>
            {item.detail ? <span className="text-xs text-ap-muted">{item.detail}</span> : null}
          </div>
          {item.state !== 'ready' ? (
            <Button type="button" variant="link" size="sm" className="h-8 shrink-0 px-1 pointer-coarse:h-11" onClick={() => onGoTo(item.step)}>
              Ir al paso
            </Button>
          ) : null}
        </li>
      ))}
    </ul>
  )
}

/**
 * Panel «Qué falta para publicar». En pantallas grandes es una columna fija; en móvil se colapsa en una
 * barra con el resumen. El resumen es un `role="status"` (aviso cortés) para lectores de pantalla.
 */
export function PublishChecklist({ items, onGoTo, className }: { items: ChecklistItem[]; onGoTo: (step: StepId) => void; className?: string }) {
  const wide = useMediaQuery('(min-width: 1024px)')
  const [expanded, setExpanded] = useState(false)
  const bodyId = useId()
  const open = wide || expanded
  const ok = missingCount(items) === 0 && !items.some((item) => item.id === 'subscription')
  return (
    <aside aria-label="Qué falta para publicar" className={cn('border-l-2 border-ap-graphite bg-ap-white', className)}>
      <div className="flex items-center gap-2 py-2 pr-1 pl-3">
        <p role="status" className={cn('flex-1 text-sm font-semibold', ok ? 'text-ap-success' : 'text-ap-ink')}>
          {checklistSummary(items)}
        </p>
        {wide ? null : (
          <Button type="button" variant="ghost" size="sm" className="h-11 gap-1 px-2" aria-expanded={expanded} aria-controls={bodyId} onClick={() => setExpanded((value) => !value)}>
            {expanded ? 'Ocultar' : 'Ver detalle'}
            <ChevronDownIcon className={cn('transition-transform', expanded && 'rotate-180')} aria-hidden />
          </Button>
        )}
      </div>
      <div id={bodyId} hidden={!open} className="border-t border-ap-line py-3 pl-3">
        <ItemList items={items} onGoTo={onGoTo} />
        <p className="mt-3 text-xs text-ap-muted">Puedes guardar un borrador aunque falten cosas; solo publicar las exige.</p>
      </div>
    </aside>
  )
}

/** Versión extensa para el paso de revisión (sin colapsar). */
export function ChecklistDetail({ items, onGoTo }: { items: ChecklistItem[]; onGoTo: (step: StepId) => void }) {
  return (
    <section aria-label="Requisitos para publicar" className="flex flex-col gap-3 border-l-2 border-ap-graphite py-1 pl-4">
      <p role="status" className="text-sm font-semibold text-ap-ink">
        {checklistSummary(items)}
      </p>
      <ItemList items={items} onGoTo={onGoTo} />
    </section>
  )
}
