import { useCallback, useState } from 'react'
import { toast } from 'sonner'
import { useAsync, type AsyncResult } from '@/lib/use-async'
import { getProductService } from './api/product-service'
import type { ProductRow } from './api/types'
import { usePortal } from './context'
import { actionErrorMessage, publishBlockers } from './lib/errors'

/** Consulta con caché en memoria acotada a la tienda actual (`portal:{storeId}:{name}`). */
export function useStoreQuery<T>(name: string, fetcher: (storeId: string) => Promise<T>): AsyncResult<T> {
  const { storeId } = usePortal()
  return useAsync(`portal:${storeId}:${name}`, () => fetcher(storeId))
}

/** Pone el foco en el contenido principal (p. ej. cuando el elemento enfocado desaparece). */
export function focusMain(): void {
  document.getElementById('contenido')?.focus()
}

/**
 * Publicar o retirar un producto. Si el servidor responde con un límite o una suscripción vencida,
 * abre el diálogo de mejora en vez de mostrar un error genérico.
 */
export function usePublishProduct() {
  const { storeId, promptUpgrade, invalidateStore } = usePortal()
  const [busyId, setBusyId] = useState<string>()

  const setPublished = useCallback(
    async (product: Pick<ProductRow, 'id' | 'name'>, published: boolean) => {
      setBusyId(product.id)
      try {
        await (await getProductService()).setPublished(storeId, product.id, published)
        invalidateStore()
        toast.success(published ? `“${product.name}” ya está publicado en el marketplace.` : `“${product.name}” se retiró del marketplace.`)
        return true
      } catch (error) {
        const blockers = publishBlockers(error)
        if (blockers) {
          const missing = blockers.length > 0 ? blockers.map((blocker) => blocker.message).join(' ') : 'Faltan datos requeridos.'
          toast.error(`“${product.name}” aún no se puede publicar. ${missing} Complétalo y vuelve a intentarlo.`, { duration: 8000 })
        } else if (!promptUpgrade(error)) toast.error(actionErrorMessage(error, 'No pudimos cambiar la publicación. Inténtalo de nuevo.'))
        return false
      } finally {
        setBusyId(undefined)
      }
    },
    [storeId, promptUpgrade, invalidateStore],
  )
  return { setPublished, busyId }
}
