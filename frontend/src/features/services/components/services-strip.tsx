import { Link } from 'react-router'
import { pluralize } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { ServicesStrip as ServicesStripData } from '../api/types'
import { ServiceCard, ServiceGrid } from './service-card'

/**
 * Franja de hasta 4 servicios de la búsqueda unificada («Todo»). Va arriba de los repuestos si la intención es de servicio
 * y debajo si es de producto. «Ver todos los servicios» abre la pestaña Servicios con la misma búsqueda.
 */
export function ServicesStrip({ strip, allHref, className }: { strip: ServicesStripData; allHref: string; className?: string }) {
  if (strip.items.length === 0) return null
  return (
    <section aria-labelledby="franja-servicios-titulo" className={cn('flex flex-col gap-3 border-y border-ap-graphite py-5', className)}>
      <div className="flex flex-wrap items-end justify-between gap-x-4 gap-y-1">
        <div className="flex flex-col gap-1">
          <p className="ap-index">Servicios / Coincidencias</p>
          <h2 id="franja-servicios-titulo" className="text-block-title text-ap-ink">
            Servicios para tu búsqueda
          </h2>
        </div>
        <Link to={allHref} className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
          Ver todos los servicios ({strip.total})
          <span className="sr-only">: {pluralize(strip.total, 'servicio coincide', 'servicios coinciden')}</span>
        </Link>
      </div>
      <ServiceGrid className="xl:grid-cols-4">
        {strip.items.map((service) => (
          <li key={service.id}>
            <ServiceCard service={service} />
          </li>
        ))}
      </ServiceGrid>
    </section>
  )
}
