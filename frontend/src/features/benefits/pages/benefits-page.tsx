import { Link } from 'react-router'
import { GiftIcon, SparklesIcon } from 'lucide-react'
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

function BenefitCard({ benefit }: { benefit: Benefit }) {
  return (
    <article className="flex flex-col gap-3 rounded-xl bg-card p-5 shadow-[0_0_0_1px_rgb(0_0_0/0.07)]">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <span className="inline-flex items-center gap-1 rounded-full bg-recommended-soft px-2 py-0.5 text-xs font-semibold text-recommended">
          <SparklesIcon className="size-3.5" aria-hidden />
          {benefit.storeName ?? 'Autopartco'}
        </span>
        <p className="text-xs text-muted-foreground">
          Vence el <time dateTime={benefit.endsAt}>{dateFormatter.format(new Date(benefit.endsAt))}</time>
        </p>
      </div>
      <div className="flex flex-col gap-1">
        <h2 className="text-lg font-bold tracking-tight">{benefit.name}</h2>
        <p className="text-2xl leading-tight font-extrabold tracking-[-0.03em]">{discountLabel(benefit)}</p>
        <p className="text-sm text-muted-foreground">{TARGET_TEXT[benefit.targetType]}</p>
      </div>
      <p className="text-sm text-foreground/85">{reasonText(benefit)} El precio con descuento se ve en cada repuesto y se confirma al pagar.</p>
      <div>
        <Button variant="outline" className="h-11 px-4" nativeButton={false} render={<Link to={benefit.storeId ? storeProductsPath(benefit.storeId) : '/buscar'} />}>
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
        <div role="status" aria-busy className="grid gap-4 md:grid-cols-2">
          <span className="sr-only">Cargando tus beneficios</span>
          {[0, 1].map((key) => (
            <Skeleton key={key} className="h-48 w-full rounded-xl" />
          ))}
        </div>
      ) : benefits.data.length === 0 ? (
        <EmptyState
          icon={<GiftIcon />}
          title="Por ahora no tienes beneficios"
          description="Cuando compres seguido en Autopartco o en una tienda, aquí aparecerán los descuentos que tienen para ti."
        >
          <Button className="h-11 px-5" nativeButton={false} render={<Link to="/buscar" />}>
            Buscar repuestos
          </Button>
        </EmptyState>
      ) : (
        <ul className="grid gap-4 md:grid-cols-2">
          {benefits.data.map((benefit) => (
            <li key={benefit.promotionId}>
              <BenefitCard benefit={benefit} />
            </li>
          ))}
        </ul>
      )}
    </Container>
  )
}
