import { useCallback, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { useAsync, type AsyncResult } from '@/lib/use-async'
import { getBranchService } from './api/branch-service'
import { getProductService } from './api/product-service'
import type { Branch, ProductRow } from './api/types'
import { usePortal } from './context'
import { actionErrorMessage, publishBlockers } from './lib/errors'

/** Consulta con caché en memoria acotada a la tienda actual (`portal:{storeId}:{name}`). */
export function useStoreQuery<T>(name: string, fetcher: (storeId: string) => Promise<T>): AsyncResult<T> {
  const { storeId } = usePortal()
  return useAsync(`portal:${storeId}:${name}`, () => fetcher(storeId))
}

/**
 * Sucursales activas del almacén (comparte caché con la pantalla de sucursales). Sin `BRANCH_READ` no se piden:
 * `known` queda en `false` y la interfaz no ofrece lo que depende de ellas.
 */
export function useActiveBranches(): { branches: Branch[]; known: boolean; result: AsyncResult<Branch[]> } {
  const { can } = usePortal()
  const readable = can('BRANCH_READ')
  const result = useStoreQuery('branches', async (id) => (readable ? (await getBranchService()).list(id) : []))
  const branches = useMemo(() => (result.data ?? []).filter((branch) => branch.state === 'ACTIVE'), [result.data])
  return { branches, known: readable && result.status === 'success', result }
}

/** Valor de `?sucursal=` cuando la persona elige «Todas» en el filtro de una pantalla (distinto de no haber elegido). */
export const ALL_BRANCHES_PARAM = 'todas'

/** Valor que guarda en la URL el filtro de sucursal de una pantalla (`''` = todas). */
export const branchParamValue = (value: string): string => value || ALL_BRANCHES_PARAM

/**
 * Filtro de sucursal de una pantalla (`?sucursal=`). Sin valor en la URL sigue a la sucursal activa de la barra de
 * contexto; lo que la persona elija en el filtro de la pantalla (otra sucursal o «Todas») manda sobre ella y no la
 * cambia. «Quitar filtros» vuelve a la sucursal activa. Devuelve el valor efectivo (`''` = todas).
 */
export function useBranchSearchParam(name = 'sucursal'): string {
  const { branchId } = usePortal()
  const [params] = useSearchParams()
  const value = params.get(name)
  if (value === null) return branchId ?? ''
  return value === ALL_BRANCHES_PARAM ? '' : value
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
