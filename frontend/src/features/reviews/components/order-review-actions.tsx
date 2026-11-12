import { useState } from 'react'
import { BadgeCheckIcon, PencilIcon, StarIcon } from 'lucide-react'
import { useSession } from '@/features/auth/hooks'
import { useProductReviewEligibility, useReviewEligibility } from '@/features/marketplace/hooks'
import { Button } from '@/components/ui/button'
import { usePendingReviews } from '../hooks'
import { editWindowClosed } from '../lib/errors'
import { ReviewDialog } from './review-dialog'

interface StoreReviewActionProps {
  orderId: string
  orderNumber: number
  storeId: string
  storeName: string
}

/**
 * «Calificar almacén» en una venta entregada. El servidor dice si el pedido aún se puede calificar; si ya tiene
 * reseña (`reviewId` de los pendientes), ofrece «Editar mi reseña».
 */
export function StoreReviewAction({ orderId, orderNumber, storeId, storeName }: StoreReviewActionProps) {
  const session = useSession()
  const eligibility = useReviewEligibility(storeId, session?.user.email)
  const pending = usePendingReviews()
  const [open, setOpen] = useState(false)

  const eligible = (eligibility.data?.eligibleOrderIds ?? []).includes(orderId)
  const pendingItem = pending.data?.find((item) => item.orderId === orderId && item.store.id === storeId)
  const reviewed = pendingItem?.store.reviewed ?? false
  const reviewId = pendingItem?.store.reviewId ?? undefined
  // La fecha límite del servidor vale para la reseña más reciente; si es la de este pedido, se usa.
  const editableUntil = reviewId && eligibility.data?.existingReviewId === reviewId ? eligibility.data.editableUntil : undefined

  if (reviewed && reviewId) {
    const closed = editWindowClosed(editableUntil)
    return (
      <>
        <Button variant="outline" className="h-11 px-4" aria-label={`${closed ? 'Ver mi reseña de' : 'Editar mi reseña de'} ${storeName}`} onClick={() => setOpen(true)}>
          <PencilIcon aria-hidden />
          {closed ? 'Mi reseña del almacén' : 'Editar mi reseña'}
        </Button>
        {open ? (
          <ReviewDialog
            open
            onOpenChange={setOpen}
            target={{ type: 'store', id: storeId }}
            targetName={storeName}
            orders={[{ id: orderId, label: `Pedido #${orderNumber}` }]}
            existing={{ id: reviewId, editableUntil }}
          />
        ) : null}
      </>
    )
  }

  if (eligible) {
    return (
      <>
        <Button variant="outline" className="h-11 px-4" onClick={() => setOpen(true)}>
          <StarIcon aria-hidden />
          Calificar almacén
        </Button>
        {open ? (
          <ReviewDialog
            open
            onOpenChange={setOpen}
            target={{ type: 'store', id: storeId }}
            targetName={storeName}
            orders={[{ id: orderId, label: `Pedido #${orderNumber}` }]}
          />
        ) : null}
      </>
    )
  }
  if (reviewed) {
    return (
      <p className="inline-flex items-center gap-1.5 text-sm font-medium text-ap-success">
        <BadgeCheckIcon aria-hidden className="size-4" />
        Ya calificaste este almacén
      </p>
    )
  }
  return null
}

interface ProductReviewActionProps {
  orderId: string
  orderNumber: number
  productId: string
  productName: string
}

/** «Calificar» o «Editar mi reseña» junto a un producto entregado, según lo que diga el servidor. */
export function ProductReviewAction({ orderId, orderNumber, productId, productName }: ProductReviewActionProps) {
  const session = useSession()
  const eligibility = useProductReviewEligibility(productId, session?.user.email).data
  const [open, setOpen] = useState(false)

  const existingId = eligibility?.existingReviewId
  const canCreate = eligibility?.eligibleOrderIds.includes(orderId) ?? false
  if (!existingId && !canCreate) return null

  const closed = existingId ? editWindowClosed(eligibility?.editableUntil) : false
  return (
    <>
      <Button variant="outline" className="h-11 shrink-0 px-3" aria-label={`${existingId ? 'Editar mi reseña de' : 'Calificar'} ${productName}`} onClick={() => setOpen(true)}>
        {existingId ? <PencilIcon aria-hidden /> : <StarIcon aria-hidden />}
        {existingId ? (closed ? 'Mi reseña' : 'Editar mi reseña') : 'Calificar'}
      </Button>
      {open ? (
        <ReviewDialog
          open
          onOpenChange={setOpen}
          target={{ type: 'product', id: productId }}
          targetName={productName}
          orders={[{ id: orderId, label: `Pedido #${orderNumber}` }]}
          existing={existingId ? { id: existingId, editableUntil: eligibility?.editableUntil } : undefined}
        />
      ) : null}
    </>
  )
}
