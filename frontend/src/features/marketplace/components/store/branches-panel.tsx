import { useState } from 'react'
import { ClockIcon, MapPinIcon, PhoneIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { pluralize } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { StoreBranch } from '../../api/types'
import { DAY_LABELS, daySlots, hasWeeklyHours, todayInBogota, WEEK_DAYS } from '../../lib/store-links'
import { BranchMap } from './branch-map'
import { DirectionsMenu } from './directions-menu'
import { WhatsappButton } from './whatsapp-button'

function OpenBadge({ open }: { open: boolean | undefined }) {
  if (open === undefined) return null
  return <Badge variant={open ? 'success' : 'neutral'}>{open ? 'Abierto ahora' : 'Cerrado ahora'}</Badge>
}

/** Horario de hoy y, al abrir, el de toda la semana (`details` nativo: teclado y lector de pantalla gratis). */
function BranchHours({ branch }: { branch: StoreBranch }) {
  const hours = branch.openingHours
  if (!hours) return null
  const today = todayInBogota()
  const todayText = daySlots(hours, today)
  return (
    <details className="group text-sm">
      <summary className="flex min-h-11 w-fit cursor-pointer list-none items-center gap-2 text-ap-ink marker:hidden [&::-webkit-details-marker]:hidden">
        <ClockIcon aria-hidden className="size-4 shrink-0 text-ap-muted" />
        <span>{hasWeeklyHours(hours) ? `Hoy: ${todayText ?? 'sin horario publicado'}` : 'Horario'}</span>
        <span className="text-ap-red underline-offset-4 group-hover:underline">
          <span className="group-open:hidden">Ver horario</span>
          <span className="hidden group-open:inline">Ocultar</span>
        </span>
      </summary>
      <dl className="mt-1 grid grid-cols-[6rem_minmax(0,1fr)] gap-x-3 gap-y-1 pb-2 pl-6">
        {WEEK_DAYS.map((day) => {
          const text = daySlots(hours, day)
          if (!text) return null
          return (
            <div key={day} className="contents">
              <dt className={cn('text-ap-muted', day === today && 'font-semibold text-ap-ink')}>{DAY_LABELS[day]}</dt>
              <dd className={cn('tabular-nums', day === today ? 'font-semibold text-ap-ink' : 'text-ap-ink')}>{text}</dd>
            </div>
          )
        })}
        {hours.notes ? <p className="col-span-2 pt-1 text-ap-muted">{hours.notes}</p> : null}
      </dl>
    </details>
  )
}

interface BranchRowProps {
  storeId: string
  storeName: string
  storePath: string
  branch: StoreBranch
  active: boolean
  whatsappMessage?: string
  whatsappEnabled: boolean
  onShowOnMap?: () => void
}

function BranchRow({ storeId, storeName, storePath, branch, active, whatsappMessage, whatsappEnabled, onShowOnMap }: BranchRowProps) {
  const place = [branch.address, branch.city].filter(Boolean).join(', ')
  return (
    <li id={`sede-${branch.id}`} className={cn('flex flex-col gap-2 px-4 py-4 transition-colors', active && 'bg-ap-paper')}>
      <div className="flex flex-wrap items-start justify-between gap-x-3 gap-y-1">
        <h3 className="text-base font-semibold text-ap-ink">{branch.name}</h3>
        <OpenBadge open={branch.openNow} />
      </div>
      {place ? (
        <p className="flex items-start gap-2 text-sm text-ap-ink">
          <MapPinIcon aria-hidden className="mt-0.5 size-4 shrink-0 text-ap-muted" />
          <span className="min-w-0 break-words">{place}</span>
        </p>
      ) : null}
      <BranchHours branch={branch} />
      <div className="flex flex-wrap gap-2 pt-1">
        {whatsappEnabled && branch.whatsapp ? (
          <WhatsappButton
            storeId={storeId}
            targets={[{ branchId: branch.id, name: branch.name, phone: branch.whatsapp }]}
            template={whatsappMessage}
            subject={storeName}
            path={storePath}
            label="Escribir"
            size="sm"
          />
        ) : null}
        <DirectionsMenu storeId={storeId} branches={[branch]} size="sm" />
        {branch.phone ? (
          <a
            href={`tel:${branch.phone}`}
            className="inline-flex h-9 items-center gap-1.5 rounded-md px-3 text-[0.8125rem] font-semibold text-ap-graphite hover:bg-ap-paper"
          >
            <PhoneIcon aria-hidden className="size-3.5" />
            Llamar
            <span className="sr-only"> a {branch.name}</span>
          </a>
        ) : null}
        {onShowOnMap && branch.point ? (
          <Button variant="ghost" size="sm" onClick={onShowOnMap} aria-pressed={active}>
            Ver en el mapa
          </Button>
        ) : null}
      </div>
    </li>
  )
}

interface BranchesPanelProps {
  storeId: string
  storeName: string
  storePath: string
  branches: StoreBranch[]
  whatsappMessage?: string
  whatsappEnabled: boolean
  /** Sin otra columna a su lado: mapa más ancho y lista en dos columnas. */
  wide?: boolean
}

/**
 * Mapa y lista de sedes. El mapa es un complemento: la lista tiene toda la información y las acciones, y sigue
 * disponible si el mapa no carga o no hay coordenadas.
 */
export function BranchesPanel({ storeId, storeName, storePath, branches, whatsappMessage, whatsappEnabled, wide }: BranchesPanelProps) {
  const [activeId, setActiveId] = useState<string>()
  const [mapFailed, setMapFailed] = useState(false)
  const hasMap = !mapFailed && branches.some((branch) => branch.point)

  const behavior = () => (window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth')

  /** Desde el marcador del mapa: resalta la sede y la acerca en la lista. */
  function select(id: string) {
    setActiveId(id)
    document.getElementById(`sede-${id}`)?.scrollIntoView({ block: 'nearest', behavior: behavior() })
  }

  /** Desde la lista: centra el mapa en la sede y lo trae a la vista (en móvil queda sobre la lista). */
  function showOnMap(id: string) {
    setActiveId(id)
    document.getElementById('mapa-sedes')?.scrollIntoView({ block: 'nearest', behavior: behavior() })
  }

  return (
    <section
      id="sedes"
      aria-labelledby="sedes-titulo"
      className={cn(
        'flex min-w-0 scroll-mt-24 flex-col gap-4',
        !wide && 'lg:sticky lg:top-24 lg:max-h-[calc(100dvh-7rem)] lg:self-start',
      )}
    >
      <div className="flex flex-col gap-1">
        <p className="ap-index">Tienda / Ubicación</p>
        <h2 id="sedes-titulo" className="text-block-title text-ap-ink">
          {branches.length === 1 ? 'Dónde encontrarnos' : `Nuestras ${pluralize(branches.length, 'sede', 'sedes')}`}
        </h2>
      </div>
      <div className={cn('flex min-h-0 flex-1 gap-4', wide ? 'flex-col lg:grid lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]' : 'flex-col')}>
        {hasMap ? (
          <BranchMap
            branches={branches}
            activeId={activeId}
            onSelect={select}
            onFail={() => setMapFailed(true)}
            className={wide ? 'lg:aspect-auto lg:min-h-96' : 'aspect-[16/10] sm:aspect-[16/9] lg:aspect-[4/3]'}
          />
        ) : null}
        <ul
          // Región desplazable dentro de la columna fija: debe poder enfocarse con teclado.
          tabIndex={0}
          aria-label="Lista de sedes"
          className="min-h-0 flex-1 divide-y divide-ap-line overflow-y-auto rounded-lg border border-ap-line bg-ap-white"
        >
          {branches.map((branch) => (
            <BranchRow
              key={branch.id}
              storeId={storeId}
              storeName={storeName}
              storePath={storePath}
              branch={branch}
              active={branch.id === activeId}
              whatsappMessage={whatsappMessage}
              whatsappEnabled={whatsappEnabled}
              onShowOnMap={hasMap ? () => showOnMap(branch.id) : undefined}
            />
          ))}
        </ul>
      </div>
    </section>
  )
}
