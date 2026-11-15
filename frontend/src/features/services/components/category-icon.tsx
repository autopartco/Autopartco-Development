import { createElement } from 'react'
import type { ServiceCategory } from '@/features/marketplace/api/types'
import { serviceCategoryIcon } from '../lib/categories'

/** Ícono Lucide de una categoría de servicio; decorativo (el nombre de la categoría siempre va en texto). */
export function CategoryIcon({ category, iconName, className }: { category: ServiceCategory; iconName?: string; className?: string }) {
  // Los íconos salen de una tabla fija de componentes Lucide: no se crea ningún componente durante el render.
  return createElement(serviceCategoryIcon(category, iconName), { className, 'aria-hidden': true })
}
