import {
  CogIcon,
  Disc3Icon,
  FunnelIcon,
  GaugeIcon,
  MoveVerticalIcon,
  ThermometerIcon,
  WindIcon,
  WrenchIcon,
  ZapIcon,
  type LucideIcon,
} from 'lucide-react'

const ICONS: Record<string, LucideIcon> = {
  frenos: Disc3Icon,
  suspension: MoveVerticalIcon,
  motor: CogIcon,
  filtros: FunnelIcon,
  electrico: ZapIcon,
  refrigeracion: ThermometerIcon,
  embrague: GaugeIcon,
  escape: WindIcon,
}

/** Icono por categoría; las categorías nuevas del servidor usan una llave genérica. */
export function CategoryIcon({ slug, className }: { slug: string; className?: string }) {
  const Icon = ICONS[slug] ?? WrenchIcon
  return <Icon className={className} aria-hidden />
}
