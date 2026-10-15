import { Link } from 'react-router'
import { HourglassIcon, OctagonXIcon } from 'lucide-react'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import type { StoreApplicationStatus } from '../api/types'

/** Almacén que todavía no puede operar: el servidor responde 403 hasta que la solicitud esté activa. */
export function StoreInactiveNotice({ status, name }: { status: StoreApplicationStatus; name: string }) {
  const rejected = status === 'REJECTED'
  const setup = status === 'CONFIGURATION_PENDING'
  return (
    <EmptyState
      icon={rejected ? <OctagonXIcon /> : <HourglassIcon />}
      title={
        rejected
          ? `La solicitud de ${name} no fue aprobada`
          : setup
            ? `Falta terminar la configuración de ${name}`
            : `${name} está en revisión`
      }
      description={
        rejected
          ? 'Revisa el correo con la respuesta del equipo de Autopartco. Si crees que es un error, responde ese mismo correo.'
          : setup
            ? 'Abre el enlace que te enviamos al correo para completar la configuración. Cuando termines podrás usar el portal.'
            : 'Nuestro equipo revisa tus documentos. Te avisaremos por correo cuando esté aprobado; mientras tanto no puedes operar.'
      }
    >
      <Button variant="outline" className="h-11 px-4" nativeButton={false} render={<Link to="/portal" />}>
        Ver mis almacenes
      </Button>
    </EmptyState>
  )
}
