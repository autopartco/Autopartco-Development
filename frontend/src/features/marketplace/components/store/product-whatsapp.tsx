import type { Product } from '../../api/types'
import { useStoreBranches, useStoreProfile } from '../../hooks'
import { productPath } from '../../lib/paths'
import { WhatsappButton } from './whatsapp-button'

/**
 * «Cotizar por WhatsApp» en la ficha: escribe a la sede que tiene el repuesto, con su nombre y enlace en el mensaje,
 * y registra el clic de esa sede y de este producto. Solo aparece si el almacén lo permite y la sede tiene número;
 * mientras cargan los datos o si fallan, simplemente no se muestra.
 */
export function ProductWhatsapp({ product }: { product: Product }) {
  return product.store ? <QuoteButton product={product} storeId={product.store.id} /> : null
}

function QuoteButton({ product, storeId }: { product: Product; storeId: string }) {
  const profile = useStoreProfile(storeId)
  const branches = useStoreBranches(storeId)
  if (!profile.data?.whatsappEnabled || !branches.data) return null
  const branch = branches.data.find((item) => item.id === product.branchId)
  if (!branch?.whatsapp) return null
  return (
    <WhatsappButton
      storeId={storeId}
      targets={[{ branchId: branch.id, name: branch.name, phone: branch.whatsapp }]}
      template={profile.data.whatsappMessage}
      subject={product.name}
      path={productPath(product.id)}
      productId={product.id}
      label="Cotizar por WhatsApp"
      variant="outline"
      className="w-full"
    />
  )
}
