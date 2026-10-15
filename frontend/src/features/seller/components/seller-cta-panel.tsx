import { Link } from 'react-router'
import { CheckIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import { formatDate } from '@/features/portal/lib/labels'
import { useSellerCta, type SellerState } from '../hooks'

const BENEFITS = [
  'Publica tu inventario ante conductores y talleres de tu ciudad.',
  'Empiezas con una prueba gratuita; eliges plan después de la aprobación.',
  'Tú fijas precios y existencias; nosotros revisamos cada almacén antes de publicarlo.',
] as const

const COPY: Record<Exclude<SellerState, 'loading'>, { title: string; body: string }> = {
  register: {
    title: 'Registra tu almacén en Autopartco',
    body: 'Vende repuestos a quienes ya los buscan cerca de tu local. Solicitar el registro toma pocos minutos.',
  },
  review: {
    title: 'Tu solicitud de almacén está en curso',
    body: 'Estamos revisando tus documentos. Aquí puedes consultar el estado y lo que sigue.',
  },
  manage: {
    title: 'Administra tu almacén',
    body: 'Gestiona productos, pedidos y tu plan desde el portal del almacén.',
  },
  learn: {
    title: '¿Tienes un almacén?',
    body: 'Conoce cómo vender en Autopartco y qué documentos necesitas.',
  },
}

/**
 * Recuadro de la portada para almacenes. El texto y el botón cambian según la cuenta: invitar a
 * registrar, ver la solicitud en curso o ir al portal. Solo muestra; el servidor decide.
 */
export function SellerCtaPanel({ className }: { className?: string }) {
  const cta = useSellerCta()
  // Mientras se consulta la solicitud del cliente se muestra la invitación a registrar (el caso más común).
  const state = cta.state === 'loading' ? 'register' : cta.state
  const copy = COPY[state]
  const sentOn = cta.state === 'review' && cta.application ? formatDate(cta.application.createdAt) : undefined

  return (
    <section aria-labelledby="cta-almacen" className={cn('surface-asphalt flex flex-col items-start gap-3 rounded-2xl p-6', className)}>
      <p className="label-mono text-asphalt-muted">Para almacenes en Colombia</p>
      <h3 id="cta-almacen" className="text-xl font-bold tracking-tight">
        {copy.title}
      </h3>
      <p className="max-w-sm text-sm text-asphalt-muted">
        {copy.body}
        {sentOn ? ` Enviada el ${sentOn}.` : ''}
      </p>
      {state === 'register' ? (
        <ul className="flex max-w-sm flex-col gap-1.5 text-sm">
          {BENEFITS.map((benefit) => (
            <li key={benefit} className="flex items-start gap-2">
              <CheckIcon className="mt-0.5 size-4 shrink-0 text-brand-on-asphalt" aria-hidden />
              {benefit}
            </li>
          ))}
        </ul>
      ) : null}
      <div className="mt-1 flex flex-wrap items-center gap-x-5 gap-y-1">
        <Link
          to={cta.state === 'loading' ? '/vender/registro' : cta.to}
          className="inline-flex min-h-11 items-center rounded-lg bg-white px-4 py-2 font-semibold text-asphalt transition-colors hover:bg-white/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
        >
          {cta.state === 'loading' ? 'Registra tu almacén' : cta.label}
        </Link>
        {state === 'register' || state === 'learn' ? (
          <Link
            to="/vender"
            className="inline-flex min-h-11 items-center text-sm font-medium text-white underline underline-offset-4 hover:text-white/80"
          >
            Cómo funciona
          </Link>
        ) : null}
      </div>
    </section>
  )
}
