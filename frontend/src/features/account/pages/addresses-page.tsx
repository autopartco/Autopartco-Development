import { useState } from 'react'
import { MapPinIcon, PlusIcon, Trash2Icon } from 'lucide-react'
import { toast } from 'sonner'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { AddressForm } from '@/features/checkout/components/address-form'
import { ApiError } from '@/lib/api'
import { invalidateAsync, useAsync } from '@/lib/use-async'
import { AccountSection } from '../account-layout'
import { deleteAddress, listAddresses } from '../api'

/** `/cuenta/direcciones`: direcciones guardadas para las entregas (las mismas que ofrece el checkout). */
export default function AccountAddressesPage() {
  const addresses = useAsync('portal:account:addresses', listAddresses)
  const [adding, setAdding] = useState(false)
  const [removing, setRemoving] = useState<string>()

  function refresh() {
    invalidateAsync('portal:account:addresses')
    invalidateAsync('checkout:')
  }

  async function remove(id: string) {
    setRemoving(id)
    try {
      await deleteAddress(id)
      toast.success('Eliminamos la dirección.')
      refresh()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'No pudimos eliminar la dirección.')
    } finally {
      setRemoving(undefined)
    }
  }

  const items = addresses.data ?? []
  return (
    <AccountSection
      id="dir-lista"
      title="Direcciones guardadas"
      description="Las usamos para tus entregas; puedes elegir otra al pagar."
      aside={
        adding ? null : (
          <Button variant="outline" className="h-11" onClick={() => setAdding(true)}>
            <PlusIcon aria-hidden />
            Agregar dirección
          </Button>
        )
      }
    >
      {adding ? (
        <div className="max-w-2xl rounded-lg border border-ap-line p-4">
          <AddressForm
            makeDefault={items.length === 0}
            onCreated={() => {
              setAdding(false)
              toast.success('Guardamos la dirección.')
              refresh()
            }}
            onCancel={() => setAdding(false)}
          />
        </div>
      ) : null}
      {addresses.status === 'error' ? (
        <ErrorState title="No pudimos cargar tus direcciones" onRetry={addresses.retry} />
      ) : !addresses.data ? (
        <Skeleton className="h-20 max-w-2xl" />
      ) : items.length === 0 ? (
        adding ? null : <EmptyState icon={<MapPinIcon />} title="No tienes direcciones guardadas" description="Agrega una para que tus compras lleguen más rápido." />
      ) : (
        <ul className="max-w-2xl divide-y divide-ap-line border-y border-ap-line">
          {items.map((address) => (
            <li key={address.id} className="flex items-start gap-3 py-3">
              <MapPinIcon className="mt-0.5 size-5 shrink-0 text-ap-muted" aria-hidden />
              <div className="min-w-0 flex-1 text-sm">
                <p className="font-semibold text-ap-ink">
                  {address.label}
                  {address.isDefault ? <span className="ml-2 text-xs font-medium text-ap-muted">Predeterminada</span> : null}
                </p>
                <p className="text-ap-ink">
                  {address.addressLine}
                  {address.addressComplement ? `, ${address.addressComplement}` : ''}
                </p>
                <p className="text-ap-muted">
                  {address.city}, {address.department}
                </p>
              </div>
              <Button variant="ghost" size="icon" className="size-11 shrink-0 text-ap-muted sm:size-9" aria-label={`Eliminar ${address.label}`} disabled={removing === address.id} onClick={() => void remove(address.id)}>
                <Trash2Icon aria-hidden />
              </Button>
            </li>
          ))}
        </ul>
      )}
    </AccountSection>
  )
}
