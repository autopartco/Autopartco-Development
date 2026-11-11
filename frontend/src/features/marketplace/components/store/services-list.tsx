import {
  CarFrontIcon,
  CircleGaugeIcon,
  CogIcon,
  DropletIcon,
  PaintbrushIcon,
  PlugZapIcon,
  ScanSearchIcon,
  SnowflakeIcon,
  WrenchIcon,
  type LucideIcon,
} from 'lucide-react'
import { formatCOP } from '@/lib/format'
import type { ServiceCategory, StoreBranch, StoreService } from '../../api/types'

const CATEGORY: Record<ServiceCategory, { label: string; icon: LucideIcon }> = {
  MECHANICS: { label: 'Mecánica', icon: WrenchIcon },
  ELECTRICAL: { label: 'Electricidad', icon: PlugZapIcon },
  ALIGNMENT_BALANCING: { label: 'Alineación y balanceo', icon: CircleGaugeIcon },
  TIRES: { label: 'Llantas', icon: CarFrontIcon },
  LUBRICATION: { label: 'Lubricación y aceites', icon: DropletIcon },
  BODY_PAINT: { label: 'Latonería y pintura', icon: PaintbrushIcon },
  INSTALLATION: { label: 'Instalación de repuestos', icon: CogIcon },
  DIAGNOSTICS: { label: 'Diagnóstico', icon: ScanSearchIcon },
  AC: { label: 'Aire acondicionado', icon: SnowflakeIcon },
  OTHER: { label: 'Otros servicios', icon: WrenchIcon },
}

function categoryLabel(category: ServiceCategory): string {
  return (CATEGORY[category] ?? CATEGORY.OTHER).label
}

function duration(minutes: number): string {
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  if (hours >= 24) return `${Math.round(hours / 24)} d`
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`
}

function ServiceCard({ service, branches }: { service: StoreService; branches: StoreBranch[] }) {
  const where = branches.filter((branch) => service.branchIds.includes(branch.id)).map((branch) => branch.name)
  return (
    <li className="flex flex-col gap-2 rounded-lg border border-ap-line bg-ap-white p-4">
      <h4 className="text-base font-semibold text-ap-ink">{service.name}</h4>
      {service.description ? <p className="text-sm whitespace-pre-line text-ap-muted">{service.description}</p> : null}
      <p className="mt-auto flex flex-wrap items-baseline gap-x-3 gap-y-1 pt-1 text-sm">
        {service.priceFrom !== undefined ? (
          <span className="text-ap-ink">
            Desde <span className="font-semibold tabular-nums">{formatCOP(service.priceFrom)}</span>
          </span>
        ) : (
          <span className="font-medium text-ap-ink">Precio a cotizar</span>
        )}
        {service.durationMinutes ? <span className="text-ap-muted tabular-nums">Aprox. {duration(service.durationMinutes)}</span> : null}
      </p>
      {where.length > 0 && branches.length > 1 ? <p className="text-xs text-ap-muted">En {where.join(', ')}</p> : null}
    </li>
  )
}

/** Servicios del almacén agrupados por categoría; cada servicio es una unidad autónoma (nombre, precio, duración). */
export function ServicesList({ services, branches, wide }: { services: StoreService[]; branches: StoreBranch[]; wide?: boolean }) {
  const groups = new Map<ServiceCategory, StoreService[]>()
  for (const service of services) groups.set(service.category, [...(groups.get(service.category) ?? []), service])

  return (
    <div className="flex flex-col gap-6">
      {[...groups.entries()].map(([category, items]) => {
        const Icon = (CATEGORY[category] ?? CATEGORY.OTHER).icon
        return (
          <div key={category} className="flex flex-col gap-3">
            <h3 className="flex items-center gap-2 text-sm font-semibold text-ap-ink">
              <Icon aria-hidden className="size-4 text-ap-muted" />
              {categoryLabel(category)}
            </h3>
            <ul className={wide ? 'grid gap-3 sm:grid-cols-2 lg:grid-cols-3' : 'grid gap-3 sm:grid-cols-2'}>
              {items.map((service) => (
                <ServiceCard key={service.id} service={service} branches={branches} />
              ))}
            </ul>
          </div>
        )
      })}
    </div>
  )
}
