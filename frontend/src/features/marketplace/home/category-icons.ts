import {
  BatteryChargingIcon,
  CarFrontIcon,
  CogIcon,
  DiscIcon,
  DropletsIcon,
  FilterIcon,
  GaugeIcon,
  LightbulbIcon,
  SnowflakeIcon,
  WrenchIcon,
  ZapIcon,
  type LucideIcon,
} from 'lucide-react'

/** El backend solo manda `code`: el ícono sale de la palabra clave que contenga (el primero que coincide gana). */
const ICONS: readonly [keyword: string, icon: LucideIcon][] = [
  ['FRENO', DiscIcon],
  ['FILTRO', FilterIcon],
  ['LUBRIC', DropletsIcon],
  ['ACEITE', DropletsIcon],
  ['BATERIA', BatteryChargingIcon],
  ['ELECTRIC', ZapIcon],
  ['ILUMIN', LightbulbIcon],
  ['REFRIGER', SnowflakeIcon],
  ['TRANSMIS', CogIcon],
  ['EMBRAGUE', CogIcon],
  ['MOTOR', GaugeIcon],
  ['CARROCER', CarFrontIcon],
  ['SUSPENS', CarFrontIcon],
  ['DIRECC', CarFrontIcon],
]

/** Ícono genérico para códigos sin coincidencia. */
export function categoryIcon(code: string): LucideIcon {
  const upper = code.normalize('NFD').replace(/[̀-ͯ]/g, '').toUpperCase()
  return ICONS.find(([keyword]) => upper.includes(keyword))?.[1] ?? WrenchIcon
}
