import { Container, PageIntro, type Crumb } from '@/components/common/page-header'
import { ServiceCategories } from '../components/service-categories'
import { ServiceResults } from '../components/service-results'
import { useServiceCategories, useServiceState } from '../hooks'
import { serviceCategoryLabel } from '../lib/categories'
import { SERVICES_PATH } from '../lib/paths'

/**
 * `/servicios`: categorías (índice con ícono y cantidad), filtros, orden, lista o mapa y paginación. La categoría vive en
 * la URL canónica `/servicios?category=AC`.
 */
export default function ServicesPage() {
  const [state, update] = useServiceState()
  const categories = useServiceCategories(state.cityCode)

  const label = state.category ? serviceCategoryLabel(state.category) : undefined
  const title = label ?? 'Servicios para tu vehículo'
  const crumbs: Crumb[] = [{ label: 'Inicio', to: '/' }]
  if (label) crumbs.push({ label: 'Servicios', to: SERVICES_PATH }, { label })
  else crumbs.push({ label: 'Servicios' })

  return (
    <Container className="py-6 sm:py-8">
      <title>{`${title} | Autopartco`}</title>
      <PageIntro
        crumbs={crumbs}
        label={label ? 'Servicios / Categoría' : 'Servicios / Talleres'}
        title={title}
        description="Alineación, cambio de aceite, diagnóstico, instalación y más. Compara talleres cercanos, agenda cuando el taller lo permite o escríbele por WhatsApp. Los precios «desde» son de referencia y pueden cambiar tras revisar tu vehículo."
        className="mb-6"
      />
      <section aria-labelledby="categorias-servicio-titulo" className="mb-8 flex flex-col gap-3 sm:mb-10">
        <h2 id="categorias-servicio-titulo" className="ap-index text-ap-ink">
          Categorías
        </h2>
        <ServiceCategories
          categories={categories.data}
          failed={categories.status === 'error'}
          onRetry={categories.retry}
          selected={state.category}
        />
      </section>
      <ServiceResults state={state} update={update} context="page" />
    </Container>
  )
}
