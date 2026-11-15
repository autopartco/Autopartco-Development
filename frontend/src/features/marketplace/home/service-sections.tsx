import { Link } from 'react-router'
import { Container } from '@/components/common/page-header'
import { EmptyState } from '@/components/common/states'
import type { ServiceSummary } from '@/features/services/api/types'
import { ServiceCard, ServiceCardSkeleton } from '@/features/services/components/service-card'
import { ServiceCategories } from '@/features/services/components/service-categories'
import type { AsyncResult } from '@/lib/use-async'
import type { HomeData } from '../api/types'
import { HomeError, SectionHeading } from './home-sections'

/** Bloques de servicios de la portada: con el mismo peso visual que los de repuestos. */

type Home = AsyncResult<HomeData>

function ServiceCarousel({ services, label }: { services: ServiceSummary[]; label: string }) {
  return (
    <div
      role="region"
      tabIndex={0}
      aria-label={label}
      className="-mx-4 flex snap-x snap-mandatory gap-4 overflow-x-auto px-4 pb-3 sm:-mx-6 sm:px-6 lg:mx-0 lg:px-0"
    >
      <ul className="contents">
        {services.map((service) => (
          <li key={service.id} className="w-[16rem] shrink-0 snap-start sm:w-[17.5rem]">
            <ServiceCard service={service} />
          </li>
        ))}
      </ul>
    </div>
  )
}

function ServiceCarouselSkeleton() {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-hidden>
      {Array.from({ length: 4 }, (_, index) => (
        <ServiceCardSkeleton key={index} />
      ))}
    </div>
  )
}

/**
 * «Servicios cerca de ti»: categorías de servicio y un carrusel de servicios cercanos (con la cookie de área en el
 * servidor; sin área, por la ciudad con más oferta). Si el origen de datos no informa servicios, no se dibuja.
 */
export function ServicesSection({ home }: { home: Home }) {
  const services = home.data?.services
  if (home.data && !services) return null
  return (
    <section aria-labelledby="servicios-inicio-titulo" className="border-t border-ap-line py-14 sm:py-20">
      <Container className="flex flex-col gap-14">
        <div>
          <SectionHeading
            id="servicios-inicio-titulo"
            label="Servicios / Talleres"
            title="Servicios cerca de ti"
            intro="Alineación, cambio de aceite, diagnóstico e instalación en talleres de tu ciudad. Los precios «desde» son de referencia."
            link={{ to: '/servicios', label: 'Ver todos los servicios' }}
          />
          {home.status === 'error' ? <HomeError home={home} /> : <ServiceCategories categories={services?.categories} />}
        </div>
        {home.status === 'error' ? null : (
          <div>
            <div className="mb-4 flex flex-wrap items-end justify-between gap-x-6 gap-y-1">
              <h3 className="text-block-title text-ap-ink">Servicios disponibles</h3>
              <Link
                to="/servicios?cerca=1&orden=nearest"
                className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline"
              >
                Ver los más cercanos
              </Link>
            </div>
            {!services ? (
              <ServiceCarouselSkeleton />
            ) : services.nearby.length === 0 ? (
              <EmptyState
                title="Todavía no hay servicios cerca de ti"
                description="Elige tu ciudad arriba para ver los talleres más cercanos o explora todos los servicios."
              >
                <Link to="/servicios" className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
                  Ver todos los servicios
                </Link>
              </EmptyState>
            ) : (
              <ServiceCarousel services={services.nearby} label="Servicios cerca de ti" />
            )}
          </div>
        )}
      </Container>
    </section>
  )
}

/** «Servicios en promoción»: solo se dibuja si hay promociones vigentes (una sección vacía no aporta nada). */
export function ServicePromotionsSection({ home }: { home: Home }) {
  const promoted = home.data?.services?.promoted
  if (home.status === 'error' || (home.data && (promoted?.length ?? 0) === 0)) return null
  return (
    <section aria-labelledby="servicios-promo-titulo" className="border-t border-ap-line py-14 sm:py-20">
      <Container>
        <SectionHeading
          id="servicios-promo-titulo"
          label="Servicios / Promociones"
          title="Servicios en promoción"
          intro="Descuentos vigentes en servicios de talleres, con el precio «desde» antes y después de la promoción."
          link={{ to: '/servicios?oferta=1', label: 'Ver todas las promociones' }}
        />
        {!promoted ? <ServiceCarouselSkeleton /> : <ServiceCarousel services={promoted} label="Servicios en promoción" />}
      </Container>
    </section>
  )
}
