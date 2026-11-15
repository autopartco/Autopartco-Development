import { Link } from 'react-router'
import { CalendarCheckIcon } from 'lucide-react'
import { buttonVariants } from '@/components/ui/button'
import { DirectionsMenu } from '@/features/marketplace/components/store/directions-menu'
import { WhatsappButton, type WhatsappTarget } from '@/features/marketplace/components/store/whatsapp-button'
import { directionsLinks } from '@/features/marketplace/lib/store-links'
import { cn } from '@/lib/utils'
import type { ServiceDetail } from '../api/types'
import { serviceBookingPath, servicePath } from '../lib/paths'

/** Sedes con número, con la más cercana (la de la tarjeta) primero. */
function whatsappTargets(service: ServiceDetail): WhatsappTarget[] {
  const targets = service.branches
    .filter((branch) => branch.whatsapp)
    .map((branch) => ({ branchId: branch.id, name: branch.name, phone: branch.whatsapp! }))
  return targets.toSorted((a, b) => Number(b.branchId === service.branch?.id) - Number(a.branchId === service.branch?.id))
}

/**
 * Acciones del detalle: «Agendar cita» (solo si el servicio admite agenda), WhatsApp con el mensaje propio del servicio
 * y «Cómo llegar». Cada canal aparece solo si el servidor lo habilita (WhatsApp activo y sede con número; sede con
 * ubicación o dirección). WhatsApp y la ruta registran el clic con el servicio y la sede.
 */
export function ServiceActions({ service, className }: { service: ServiceDetail; className?: string }) {
  const store = service.store
  const targets = service.whatsappEnabled ? whatsappTargets(service) : []
  const canDirect = service.branches.some((branch) => directionsLinks(branch).length > 0)
  const canWhatsapp = !!store && targets.length > 0
  const canBook = service.bookingEnabled

  if (!store || (!canBook && !canWhatsapp && !canDirect)) {
    return (
      <p className={cn('text-sm text-ap-muted', className)}>
        Este taller aún no publicó un canal para agendar o escribirle desde Autopartco. Revisa su perfil para ver otros datos de contacto.
      </p>
    )
  }

  return (
    <div className={cn('flex flex-col gap-2', className)}>
      {canBook ? (
        <Link to={serviceBookingPath(service.id)} className={cn(buttonVariants(), 'w-full')}>
          <CalendarCheckIcon aria-hidden />
          Agendar cita
        </Link>
      ) : null}
      {canWhatsapp ? (
        <WhatsappButton
          storeId={store.id}
          targets={targets}
          template={service.whatsappMessage}
          subject={service.name}
          path={servicePath(service.id)}
          service={{ id: service.id, promotionLabel: service.promotion?.label }}
          label="Escribir por WhatsApp"
          variant={canBook ? 'outline' : 'default'}
          className="w-full"
        />
      ) : null}
      {canDirect ? <DirectionsMenu storeId={store.id} branches={service.branches} serviceId={service.id} className="w-full" /> : null}
    </div>
  )
}
