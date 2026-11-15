import {
  CircleDotIcon,
  CogIcon,
  DropletsIcon,
  EllipsisIcon,
  GaugeIcon,
  PaintBucketIcon,
  ScanSearchIcon,
  SnowflakeIcon,
  WrenchIcon,
  ZapIcon,
  type LucideIcon,
} from 'lucide-react'
import type { ServiceCategory } from '@/features/marketplace/api/types'

/** Etiquetas e íconos de las categorías de servicio (mismos nombres que el diccionario del servidor). */
const CATEGORY: Record<ServiceCategory, { label: string; icon: LucideIcon }> = {
  MECHANICS: { label: 'Mecánica general', icon: WrenchIcon },
  ELECTRICAL: { label: 'Electricidad automotriz', icon: ZapIcon },
  ALIGNMENT_BALANCING: { label: 'Alineación y balanceo', icon: GaugeIcon },
  TIRES: { label: 'Llantas y montallantas', icon: CircleDotIcon },
  LUBRICATION: { label: 'Lubricación y cambio de aceite', icon: DropletsIcon },
  BODY_PAINT: { label: 'Latonería y pintura', icon: PaintBucketIcon },
  INSTALLATION: { label: 'Instalación y montaje', icon: CogIcon },
  DIAGNOSTICS: { label: 'Diagnóstico y escáner', icon: ScanSearchIcon },
  AC: { label: 'Aire acondicionado', icon: SnowflakeIcon },
  OTHER: { label: 'Otros servicios', icon: EllipsisIcon },
}

/** Nombre de ícono Lucide (kebab-case) que envía el servidor -> componente. */
const ICON_BY_NAME: Record<string, LucideIcon> = {
  wrench: WrenchIcon,
  zap: ZapIcon,
  gauge: GaugeIcon,
  'circle-dot': CircleDotIcon,
  droplets: DropletsIcon,
  'paint-bucket': PaintBucketIcon,
  cog: CogIcon,
  'scan-search': ScanSearchIcon,
  snowflake: SnowflakeIcon,
  ellipsis: EllipsisIcon,
}

export function isServiceCategory(value: string | null | undefined): value is ServiceCategory {
  return !!value && value in CATEGORY
}

/** Una categoría desconocida (el servidor puede añadir una) cae en «Otros servicios». */
export function toServiceCategory(value: string | null | undefined): ServiceCategory {
  return isServiceCategory(value) ? value : 'OTHER'
}

export function serviceCategoryLabel(category: ServiceCategory): string {
  return CATEGORY[category].label
}

/** Ícono de la categoría; con `iconName` del servidor se prefiere ese, con respaldo al local. */
export function serviceCategoryIcon(category: ServiceCategory, iconName?: string): LucideIcon {
  return (iconName ? ICON_BY_NAME[iconName] : undefined) ?? CATEGORY[category].icon
}
