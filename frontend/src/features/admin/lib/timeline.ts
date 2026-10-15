import type { StoreApplication } from '../api/types'
import { DAY_MS, startOfDay, timeOf } from './labels'

export interface DayRange {
  /** Desplazamientos en días desde la solicitud más antigua. */
  from: number
  to: number
}

/** Días entre la solicitud más antigua y la más reciente (0 si hay una sola fecha). */
export function spanDays(applications: readonly StoreApplication[]): { start: number; days: number } {
  if (applications.length === 0) return { start: 0, days: 0 }
  const times = applications.map((item) => startOfDay(timeOf(item.createdAt)))
  const start = Math.min(...times)
  return { start, days: Math.round((Math.max(...times) - start) / DAY_MS) }
}

export const dayOffset = (createdAt: string, start: number): number => Math.round((startOfDay(timeOf(createdAt)) - start) / DAY_MS)
