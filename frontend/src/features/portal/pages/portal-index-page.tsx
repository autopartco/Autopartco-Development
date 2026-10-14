import { Navigate, Link } from 'react-router'
import { Button } from '@/components/ui/button'
import { useAsync } from '@/lib/use-async'
import { getPortalService } from '../api/portal-service'

/** `/portal`: lleva a la primera tienda del usuario, o muestra el estado vacío. */
export default function PortalIndexPage() {
  const stores = useAsync('portal:my-stores', async () => (await getPortalService()).myStores())

  if (stores.status === 'loading') {
    return <p className="text-sm text-muted-foreground">Cargando tus almacenes…</p>
  }
  if (stores.status === 'error' || !stores.data) {
    return (
      <div role="alert" className="flex flex-col gap-3">
        <p className="text-sm">No pudimos cargar tus almacenes.</p>
        <Button variant="outline" onClick={stores.retry}>Reintentar</Button>
      </div>
    )
  }
  const first = stores.data.find((store) => store.subscription !== null) ?? stores.data[0]
  if (first) {
    return <Navigate to={`/portal/${first.storeId}/inicio`} replace />
  }
  return (
    <div className="flex flex-col gap-3">
      <h1 className="text-lg font-semibold">Aún no tienes un almacén</h1>
      <p className="text-sm text-muted-foreground">Registra tu almacén para empezar a vender en Autopartco.</p>
      <Button className="w-fit" nativeButton={false} render={<Link to="/vender" />}>Quiero vender</Button>
    </div>
  )
}
