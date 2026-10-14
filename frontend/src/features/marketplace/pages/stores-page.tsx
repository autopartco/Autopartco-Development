import { Container, PageIntro } from '@/components/common/page-header'
import { EmptyState, ErrorState } from '@/components/common/states'
import { useNearbyStores, useStoreDistances } from '@/features/location/hooks'
import { pluralize } from '@/lib/format'
import { ShieldCheckIcon } from 'lucide-react'
import { RECOMMENDED_EXPLANATION, RecommendedMark } from '../components/product-meta'
import { StoreCard, StoreCardSkeleton } from '../components/store-card'
import { useStores } from '../hooks'

export default function StoresPage() {
  const stores = useStores()
  const distances = useStoreDistances()
  const nearby = useNearbyStores().data
  const hasRecommended = stores.data?.some((store) => store.recommended) ?? false
  const reference = nearby?.origin && nearby.city ? `${nearby.origin.label}, ${nearby.city.name}` : undefined
  return (
    <Container className="py-6 sm:py-8">
      <title>Tiendas de repuestos | Autopartco</title>
      <PageIntro
        crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Tiendas' }]}
        label={stores.data ? `${pluralize(stores.data.length, 'almacén', 'almacenes')} en Autopartco` : 'Almacenes'}
        title="Tiendas de repuestos"
        description={
          <p>
            Almacenes que publican en Autopartco. Entra a una tienda para ver sus repuestos, precios y existencias.
            {distances.size > 0 && reference
              ? ` Las distancias se miden en línea recta desde ${reference} hasta la sede más cercana de cada tienda.`
              : null}
          </p>
        }
      >
        {/* Único lugar donde se menciona la verificación: aplica a todos, así que no se marca tienda por tienda. */}
        <ul className="mt-2 flex flex-col gap-2 text-sm sm:flex-row sm:flex-wrap sm:gap-x-6">
          <li className="flex items-center gap-2">
            <ShieldCheckIcon className="size-4 shrink-0 text-stock" aria-hidden />
            Todos los almacenes de Autopartco están verificados.
          </li>
          {hasRecommended ? (
            <li className="flex flex-wrap items-center gap-2">
              <RecommendedMark />
              <span className="text-muted-foreground">{RECOMMENDED_EXPLANATION}</span>
            </li>
          ) : null}
        </ul>
      </PageIntro>
      {stores.status === 'error' ? (
        <ErrorState title="No pudimos cargar las tiendas" onRetry={stores.retry} />
      ) : !stores.data ? (
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3" aria-hidden>
          {Array.from({ length: 6 }, (_, index) => (
            <li key={index}>
              <StoreCardSkeleton />
            </li>
          ))}
        </ul>
      ) : stores.data.length === 0 ? (
        <EmptyState title="Aún no hay tiendas publicadas" description="Vuelve pronto: estamos sumando almacenes de todo el país." />
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {stores.data.map((store) => (
            <li key={store.id}>
              <StoreCard store={store} distanceKm={distances.get(store.id)} />
            </li>
          ))}
        </ul>
      )}
    </Container>
  )
}
