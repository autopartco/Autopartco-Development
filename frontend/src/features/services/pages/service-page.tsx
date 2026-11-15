import { useEffect } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { ClockIcon, InfoIcon } from 'lucide-react'
import { Breadcrumbs, Container } from '@/components/common/page-header'
import { ErrorState } from '@/components/common/states'
import { SmartImage } from '@/components/common/smart-image'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { formatKm } from '@/features/location/lib/geo'
import { isNotFoundError } from '@/features/marketplace/api/types'
import { BranchesPanel } from '@/features/marketplace/components/store/branches-panel'
import { useNearMe } from '@/features/marketplace/components/search/use-near-me'
import { storePath } from '@/features/marketplace/lib/paths'
import { NotFoundContent } from '@/features/marketplace/pages/not-found-page'
import { trackView } from '@/lib/visibility'
import type { ServiceDetail } from '../api/types'
import { ServiceActions } from '../components/service-actions'
import { ServiceCard, ServiceGrid, ServicePrice } from '../components/service-card'
import { useService } from '../hooks'
import { CategoryIcon } from '../components/category-icon'
import { serviceCategoryLabel } from '../lib/categories'
import { formatDuration } from '../lib/format'
import { SERVICES_PATH, servicePath, servicesCategoryPath } from '../lib/paths'

function ServiceSkeleton() {
  return (
    <Container className="py-6 sm:py-8">
      <span className="sr-only" role="status">
        Cargando servicio
      </span>
      <div aria-hidden className="flex flex-col gap-6">
        <Skeleton className="h-4 w-64" />
        <div className="flex flex-col gap-3">
          <Skeleton className="h-3 w-40" />
          <Skeleton className="h-9 w-2/3" />
          <Skeleton className="h-4 w-1/3" />
        </div>
        <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_22rem] lg:gap-12">
          <Skeleton className="h-64 w-full lg:col-start-1" />
          <Skeleton className="h-56 w-full lg:col-start-2 lg:row-start-1" />
        </div>
      </div>
    </Container>
  )
}

/** Detalle de un servicio visible; registra `SERVICE_VIEW` al abrirlo. */
function ServiceDetailView({ service }: { service: ServiceDetail }) {
  const [searchParams] = useSearchParams()
  const storeId = service.store?.id
  useEffect(() => {
    if (storeId) trackView('SERVICE_VIEW', storeId, service.id)
  }, [storeId, service.id])

  const categoryLabel = serviceCategoryLabel(service.category)
  const wantsBooking = searchParams.get('agendar') === '1' && service.bookingEnabled
  const store = service.store

  return (
    <Container className="py-6 sm:py-8">
      <title>{`${service.name}${store ? ` en ${store.name}` : ''} | Servicios Autopartco`}</title>
      <Breadcrumbs
        className="mb-4 sm:mb-6"
        items={[
          { label: 'Inicio', to: '/' },
          { label: 'Servicios', to: SERVICES_PATH },
          { label: categoryLabel, to: servicesCategoryPath(service.category) },
          { label: service.name },
        ]}
      />

      <header className="mb-6 flex flex-col gap-2 sm:mb-8">
        <p className="ap-index flex items-center gap-1.5">
          <CategoryIcon category={service.category} className="size-3.5 shrink-0" />
          <span>Servicio / {categoryLabel}</span>
        </p>
        <h1 className="max-w-4xl text-page-title text-ap-ink">{service.name}</h1>
        {store ? (
          <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-base text-ap-muted">
            <span>Lo presta</span>
            <Link to={storePath(store.id)} className="font-semibold text-ap-ink underline-offset-4 hover:text-ap-red hover:underline">
              {store.name}
            </Link>
            {service.distanceKm !== undefined ? <span className="tabular-nums">· sede a {formatKm(service.distanceKm)}</span> : null}
          </p>
        ) : null}
      </header>

      <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_22rem] lg:gap-12">
        {/* En móvil el precio y las acciones van primero: son lo que la persona vino a buscar. */}
        <aside aria-label="Precio y acciones" className="flex flex-col gap-4 lg:col-start-2 lg:row-start-1 lg:sticky lg:top-[calc(var(--header-h)+1.5rem)] lg:self-start">
          <div className="flex flex-col gap-3 border-y border-ap-graphite py-4">
            <ServicePrice service={service} size="lg" />
            {service.durationMinutes ? (
              <p className="flex items-center gap-2 text-sm text-ap-ink tabular-nums">
                <ClockIcon className="size-4 text-ap-muted" aria-hidden />
                Duración aproximada: {formatDuration(service.durationMinutes)}
              </p>
            ) : null}
            <p className="text-xs text-ap-muted">
              Precio de referencia: puede cambiar tras revisar tu vehículo. El taller confirma el valor final antes de empezar.
            </p>
            {service.bookingEnabled ? <Badge variant="info">Agenda en línea disponible</Badge> : null}
          </div>
          {wantsBooking ? (
            <p role="status" className="flex items-start gap-2 border-l-2 border-ap-info bg-ap-info-soft px-4 py-3 text-sm text-ap-ink">
              <InfoIcon className="mt-0.5 size-4 shrink-0 text-ap-info" aria-hidden />
              Estamos habilitando la agenda en línea. Por ahora escribe por WhatsApp o ve a la sede para pedir tu cita.
            </p>
          ) : null}
          <ServiceActions service={service} />
        </aside>

        <div className="flex min-w-0 flex-col gap-10 lg:col-start-1 lg:row-start-1">
          {service.imageUrl ? (
            <SmartImage
              src={service.imageUrl}
              alt={`${service.name}, imagen del servicio`}
              width={960}
              height={540}
              loading="eager"
              frameClassName="aspect-[16/9] w-full max-w-2xl rounded-lg border border-ap-line"
              className="p-2"
            />
          ) : null}

          {service.description ? (
            <section aria-labelledby="descripcion-servicio" className="flex flex-col gap-3">
              <div className="flex flex-col gap-1">
                <p className="ap-index">Servicio / Descripción</p>
                <h2 id="descripcion-servicio" className="text-block-title text-ap-ink">
                  Qué incluye
                </h2>
              </div>
              <p className="max-w-prose whitespace-pre-line text-ap-ink">{service.description}</p>
            </section>
          ) : null}

          {store && service.branches.length > 0 ? (
            <BranchesPanel
              storeId={store.id}
              storeName={store.name}
              storePath={storePath(store.id)}
              branches={service.branches}
              whatsappMessage={service.whatsappMessage}
              whatsappEnabled={service.whatsappEnabled}
              wide
              service={{ id: service.id, name: service.name, path: servicePath(service.id), promotionLabel: service.promotion?.label }}
              indexLabel="Servicio / Ubicación"
              title={service.branches.length === 1 ? 'Dónde se presta' : 'Sedes que lo prestan'}
            />
          ) : null}
        </div>
      </div>

      {service.related.length > 0 ? (
        <section aria-labelledby="relacionados-servicio" className="mt-12 flex flex-col gap-4 border-t border-ap-graphite pt-8">
          <div className="flex flex-col gap-1">
            <p className="ap-index">Servicios / Relacionados</p>
            <h2 id="relacionados-servicio" className="text-block-title text-ap-ink">
              Otros talleres con {categoryLabel.toLowerCase()}
            </h2>
          </div>
          <ServiceGrid className="lg:grid-cols-3">
            {service.related.map((item) => (
              <li key={item.id}>
                <ServiceCard service={item} />
              </li>
            ))}
          </ServiceGrid>
        </section>
      ) : null}
    </Container>
  )
}

/** `/servicio/:id`: imagen, almacén, precio «desde» con promoción, acciones, sedes con mapa y relacionados. */
export default function ServicePage() {
  const { id = '' } = useParams()
  const near = useNearMe().origin
  const result = useService(id, near)

  if (result.status === 'error') {
    if (isNotFoundError(result.error)) {
      return (
        <NotFoundContent
          title="No encontramos este servicio"
          description="Puede que el taller ya no lo ofrezca o que el enlace esté incompleto. Mira los servicios disponibles."
        />
      )
    }
    return (
      <Container className="py-10">
        <ErrorState title="No pudimos cargar este servicio" onRetry={result.retry} />
      </Container>
    )
  }
  // Al cambiar de servicio (relacionados) se muestra el esqueleto en lugar del anterior.
  if (!result.data || (result.isStale && result.data.id !== id)) return <ServiceSkeleton />
  return <ServiceDetailView key={result.data.id} service={result.data} />
}
