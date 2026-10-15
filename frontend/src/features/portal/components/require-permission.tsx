import type { ReactNode } from 'react'
import { Navigate } from 'react-router'
import { ShieldAlertIcon } from 'lucide-react'
import { EmptyState } from '@/components/common/states'
import type { StorePermission } from '../api/types'
import { usePortal } from '../context'
import { firstAllowedSection } from '../lib/sections'
import { ListSkeleton } from './list-skeleton'

/**
 * Guarda de una sección del portal: si la persona no tiene el permiso, la lleva a la primera
 * sección a la que sí tiene acceso. Sin ninguna, muestra un aviso en vez de redirigir en bucle.
 * Solo oculta la pantalla: el servidor sigue autorizando cada petición.
 */
export function RequirePermission({ permission, children }: { permission: StorePermission; children: ReactNode }) {
  const { storeId, can, permissionsReady } = usePortal()

  if (!permissionsReady) return <ListSkeleton label="Cargando permisos" />
  if (can(permission)) return <>{children}</>

  const fallback = firstAllowedSection(can, storeId)
  if (fallback) return <Navigate to={fallback} replace />

  return (
    <EmptyState
      icon={<ShieldAlertIcon />}
      title="No tienes permisos en este almacén"
      description="Pide a un administrador del almacén que te dé acceso a alguna sección."
    />
  )
}
