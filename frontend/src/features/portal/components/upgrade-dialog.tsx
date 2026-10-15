import { Link } from 'react-router'
import { ArrowUpRightIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogClose, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { ApiError, detailNumber, detailText } from '@/lib/api'
import { formatNumber } from '@/lib/format'
import type { Capacity } from '../api/types'
import { capabilityLabel, CAPACITY_LABEL, formatDate } from '../lib/labels'

interface UpgradeDialogProps {
  /** Error 409 de plan que originó el diálogo; `undefined` lo mantiene cerrado. */
  error: ApiError | undefined
  planName?: string
  plansPath: string
  onClose: () => void
}

function capacityOf(error: ApiError): Capacity | undefined {
  const value = detailText(error, 'capacity')
  return value === 'PRODUCTS' || value === 'BRANCHES' || value === 'USERS' ? value : undefined
}

interface Copy {
  title: string
  description: string
  rows: { label: string; value: string }[]
  cta: string
}

/** Texto del diálogo según el código y los `details` que devuelve el servidor (plan §4.2). */
function copyFor(error: ApiError, planName: string | undefined): Copy {
  const planCode = detailText(error, 'planCode')
  const plan = planName ?? planCode ?? 'actual'

  if (error.code === 'SELLER_PLAN_LIMIT_REACHED') {
    const capacity = capacityOf(error)
    const names = capacity ? CAPACITY_LABEL[capacity] : undefined
    const limit = detailNumber(error, 'limit')
    const used = detailNumber(error, 'used')
    const requested = detailNumber(error, 'requested')
    const rows = [
      limit !== undefined ? { label: 'Límite del plan', value: formatNumber(limit) } : undefined,
      used !== undefined ? { label: 'En uso', value: formatNumber(used) } : undefined,
      requested !== undefined ? { label: 'Que intentaste agregar', value: formatNumber(requested) } : undefined,
    ].filter((row): row is { label: string; value: string } => row !== undefined)
    return {
      title: `Llegaste al límite de ${names?.plural ?? 'tu plan'}`,
      description: `Tu plan ${plan} ya no permite agregar más ${names?.plural ?? 'elementos'}. Lo que ya tienes no se borra: mejora el plan para seguir creciendo.`,
      rows,
      cta: 'Ver planes',
    }
  }

  if (error.code === 'SELLER_PLAN_FEATURE_UNAVAILABLE') {
    const capability = detailText(error, 'capability')
    return {
      title: 'Esta función no está en tu plan',
      description: `${capability ? capabilityLabel(capability) : 'Esta función'} no está incluida en el plan ${plan}. Cambia de plan para activarla.`,
      rows: [],
      cta: 'Comparar planes',
    }
  }

  const expiredAt = detailText(error, 'expiredAt')
  return {
    title: 'Tu suscripción venció',
    description: `${expiredAt ? `Venció el ${formatDate(expiredAt)}. ` : ''}Mientras esté vencida puedes ver tu información y gestionar pedidos, pero no crear ni publicar productos, sucursales ni invitaciones. Renueva tu plan para volver a hacerlo.`,
    rows: [],
    cta: 'Renovar plan',
  }
}

/** Diálogo reutilizable que invita a mejorar el plan cuando el servidor lo exige. */
export function UpgradeDialog({ error, planName, plansPath, onClose }: UpgradeDialogProps) {
  // Se conserva el último contenido mientras se anima el cierre.
  const copy = error ? copyFor(error, planName) : undefined
  return (
    <Dialog open={error !== undefined} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        {copy ? (
          <>
            <DialogHeader>
              <DialogTitle>{copy.title}</DialogTitle>
              <DialogDescription>{copy.description}</DialogDescription>
            </DialogHeader>
            {copy.rows.length > 0 ? (
              <dl className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-1.5 rounded-lg bg-concrete p-3 text-sm">
                {copy.rows.map((row) => (
                  <div key={row.label} className="contents">
                    <dt className="text-muted-foreground">{row.label}</dt>
                    <dd className="text-right font-semibold tabular-nums">{row.value}</dd>
                  </div>
                ))}
              </dl>
            ) : null}
            <DialogFooter>
              <DialogClose render={<Button variant="outline" className="h-11 px-4" />}>Ahora no</DialogClose>
              <Button className="h-11 px-4" nativeButton={false} render={<Link to={plansPath} onClick={onClose} />}>
                {copy.cta}
                <ArrowUpRightIcon aria-hidden />
              </Button>
            </DialogFooter>
          </>
        ) : null}
      </DialogContent>
    </Dialog>
  )
}
