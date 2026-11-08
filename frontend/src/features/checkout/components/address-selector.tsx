import { useId, useRef, useState } from 'react'
import { MapPinIcon, PlusIcon } from 'lucide-react'
import { ErrorState } from '@/components/common/states'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Skeleton } from '@/components/ui/skeleton'
import type { PersonAddressDto } from '../api/types'
import { AddressForm } from './address-form'

interface AddressSelectorProps {
  status: 'loading' | 'error' | 'ready'
  addresses: PersonAddressDto[]
  selectedId: string | undefined
  /** Aviso cuando el servidor no encontró la dirección elegida. */
  notice?: string
  disabled?: boolean
  onSelect: (id: string) => void
  onCreated: (address: PersonAddressDto) => void
  onRetry: () => void
}

const OPTION_CLASS =
  'flex cursor-pointer items-start gap-3 rounded-md border border-ap-line-strong bg-ap-white p-3.5 transition-colors hover:bg-ap-paper has-data-checked:border-ap-red has-data-checked:bg-ap-red-tint has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-(--focus)'

export function AddressSelector({ status, addresses, selectedId, notice, disabled, onSelect, onCreated, onRetry }: AddressSelectorProps) {
  const [adding, setAdding] = useState(false)
  const labelId = useId()
  const addButton = useRef<HTMLButtonElement>(null)

  if (status === 'loading') {
    return (
      <div className="flex flex-col gap-2.5" role="status" aria-label="Cargando tus direcciones">
        <Skeleton className="h-20 w-full" />
        <Skeleton className="h-20 w-full" />
      </div>
    )
  }
  if (status === 'error') {
    return <ErrorState title="No pudimos cargar tus direcciones" onRetry={onRetry} />
  }

  function closeForm() {
    setAdding(false)
    requestAnimationFrame(() => addButton.current?.focus())
  }

  return (
    <div className="flex flex-col gap-4">
      {notice ? (
        <p role="alert" className="rounded-md border border-ap-danger/30 bg-ap-danger-soft p-3 text-sm font-medium text-ap-danger">
          {notice}
        </p>
      ) : null}

      {addresses.length === 0 && !adding ? (
        <p className="text-sm text-ap-muted">Aún no tienes direcciones guardadas. Agrega una para saber a dónde llevar tu pedido.</p>
      ) : null}

      {addresses.length > 0 ? (
        <>
          <p id={labelId} className="sr-only">
            Dirección de entrega
          </p>
          <RadioGroup aria-labelledby={labelId} value={selectedId ?? ''} disabled={disabled} onValueChange={(value) => onSelect(String(value))}>
            {addresses.map((address) => (
              <label key={address.id} className={OPTION_CLASS}>
                <RadioGroupItem value={address.id} className="mt-1" />
                <MapPinIcon className="mt-0.5 size-5 shrink-0 text-ap-muted" aria-hidden />
                <span className="flex min-w-0 flex-col">
                  <span className="flex flex-wrap items-center gap-2 font-medium text-ap-ink">
                    {address.label}
                    {address.isDefault ? <Badge variant="neutral">Predeterminada</Badge> : null}
                  </span>
                  <span className="text-sm text-ap-ink">
                    {address.addressLine}
                    {address.addressComplement ? `, ${address.addressComplement}` : ''}
                  </span>
                  <span className="text-sm text-ap-muted">
                    {address.city}, {address.department}
                    {address.postalCode ? ` · ${address.postalCode}` : ''}
                  </span>
                </span>
              </label>
            ))}
          </RadioGroup>
        </>
      ) : null}

      {adding ? (
        <AddressForm
          makeDefault={addresses.length === 0}
          onCancel={closeForm}
          onCreated={(address) => {
            onCreated(address)
            closeForm()
          }}
        />
      ) : (
        <Button ref={addButton} type="button" variant="outline" className="w-fit" onClick={() => setAdding(true)} disabled={disabled}>
          <PlusIcon aria-hidden />
          Agregar dirección
        </Button>
      )}
    </div>
  )
}
