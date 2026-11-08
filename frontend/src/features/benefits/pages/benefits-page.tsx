import { Link } from 'react-router'
import { GiftIcon } from 'lucide-react'
import { Container, PageIntro } from '@/components/common/page-header'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { storeProductsPath } from '@/features/marketplace/lib/paths'
import { formatCOP } from '@/lib/format'
import { useAsync } from '@/lib/use-async'
import { getBenefitsService } from '../api/benefits-service'
import type { Benefit } from '../api/types'

const dateFormatter = new Intl.DateTimeFormat('es-CO', { dateStyle: 'long' })

function discountLabel(benefit: Benefit): string {
  return benefit.discountType === 'PERCENTAGE'
    ? `${new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 }).format(benefit.discountValue)} % de descuento`
    : `${formatCOP(benefit.discountValue)} de descuento`
}

const TARGET_TEXT: Record<Benefit['targetType'], string> = {
  ALL: 'en los repuestos de Autopartco',
  STORE: 'en todos los repuestos de la tienda',
  PRODUCT: 'en un repuesto seleccionado',
  CATEGORY: 'en una categoría de repuestos',
  MANUFACTURER: 'en una marca de repuestos',
  ATTRIBUTE: 'en repuestos seleccionados',
}

/** Por qué recibes este beneficio, dicho al comprador. */
function reasonText(benefit: Benefit): string {
  const store = benefit.storeName ?? 'la tienda'
  switch (benefit.audience) {
    case 'PLATFORM_FREQUENT':
      return 'Es tuyo por comprar seguido en Autopartco.'
    case 'STORE_FREQUENT':
      return `Es tuyo por comprar seguido en ${store}.`
    case 'STORE_AT_RISK':
      return `${store} quiere volver a verte.`
    case 'STORE_NEW':
      return `Bienvenido a ${store}: es tuyo por tu primera compra.`
    case 'STORE_DORMANT':
      return `${store} te tiene un descuento para que vuelvas.`
    default:
      return 'Aplica para todos los compradores.'
  }
}

/**
 * Fila de un beneficio: quién lo ofrece y hasta cuándo, el descuento como dato principal, a qué
 * aplica y por qué lo recibes. La acción queda alineada a la derecha desde `md`.
 */
function BenefitRow({ benefit }: { benefit: Benefit }) {
  return (
    <article className="grid gap-x-6 gap-y-3 py-5 md:grid-cols-[minmax(0,1fr)_auto] md:items-center">
      <div className="flex min-w-0 flex-col gap-1.5">
        <p className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5 text-sm">
          <span className="font-semibold text-ap-ink">{benefit.storeName ?? 'Autopartco'}</span>
          <span className="text-ap-muted">
            · Vence el{' '}
            <time dateTime={benefit.endsAt} className="tabular-nums">
              {dateFormatter.format(new Date(benefit.endsAt))}
            </time>
          </span>
        </p>
        <h2 className="text-base font-semibold text-ap-ink">{benefit.name}</h2>
        <p className="flex flex-wrap items-baseline gap-x-2">
          <span className="ap-price text-xl text-ap-ink">{discountLabel(benefit)}</span>
          <span className="text-sm text-ap-muted">{TARGET_TEXT[benefit.targetType]}</span>
        </p>
        <p className="max-w-prose text-sm text-ap-ink">
          {reasonText(benefit)} El precio con descuento se ve en cada repuesto y se confirma al pagar.
        </p>
      </div>
      <div>
        <Button variant="outline" nativeButton={false} render={<Link to={benefit.storeId ? storeProductsPath(benefit.storeId) : '/buscar'} />}>
          {benefit.storeId ? 'Ver repuestos de la tienda' : 'Buscar repuestos'}
        </Button>
      </div>
    </article>
  )
}

/** `/mis-beneficios`: promociones dirigidas al comprador (`GET /marketplace/me/benefits`). */
export default function BenefitsPage() {
  // Prefijo `marketplace:benefits` para descartarlo al cambiar de sesión.
  const benefits = useAsync('marketplace:benefits', () => getBenefitsService().then((service) => service.list()))

  return (
    <Container className="py-6 sm:py-8">
      <title>Tus beneficios | Autopartco</title>
      <PageIntro
        crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Tus beneficios' }]}
        label="Tu cuenta"
        title="Tus beneficios"
        description="Descuentos que Autopartco y las tiendas tienen para ti según cómo compras."
      />
      {benefits.status === 'error' ? (
        <ErrorState title="No pudimos cargar tus beneficios" onRetry={benefits.retry} />
      ) : !benefits.data ? (
        <div className="flex flex-col divide-y divide-ap-line border-y border-ap-line">
          <span className="sr-only" role="status">
            Cargando tus beneficios
          </span>
          {[0, 1, 2].map((key) => (
            <div key={key} className="flex flex-col gap-2 py-5" aria-hidden>
              <Skeleton className="h-4 w-48" />
              <Skeleton className="h-5 w-2/3" />
              <Skeleton className="h-6 w-56" />
            </div>
          ))}
        </div>
      ) : benefits.data.length === 0 ? (
        <EmptyState
          icon={<GiftIcon />}
          title="Por ahora no tienes beneficios"
          description="Cuando compres seguido en Autopartco o en una tienda, aquí aparecerán los descuentos que tienen para ti."
        >
          <Button nativeButton={false} render={<Link to="/buscar" />}>
            Buscar repuestos
          </Button>
        </EmptyState>
      ) : (
        <ul className="flex flex-col divide-y divide-ap-line border-y border-ap-line">
          {benefits.data.map((benefit) => (
            <li key={benefit.promotionId}>
              <BenefitRow benefit={benefit} />
            </li>
          ))}
        </ul>
      )}
    </Container>
  )
}
