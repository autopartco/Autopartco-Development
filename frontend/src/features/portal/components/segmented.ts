import { cn } from '@/lib/utils'

/**
 * Control segmentado del portal (filtros de vista y rangos): grupo con borde de control y radio 6px;
 * la opción activa va en grafito con texto blanco (no rojo: el rojo queda para la acción principal).
 * Lo usan enlaces, botones con `aria-pressed` y radios, por eso son clases y no un componente.
 */
export const SEGMENT_GROUP_CLASS = 'inline-flex w-fit max-w-full flex-wrap rounded-md border border-ap-line-strong'

export function segmentClass(active: boolean, className?: string): string {
  return cn(
    'inline-flex min-h-11 shrink-0 items-center justify-center gap-1.5 border-l border-ap-line-strong px-4 text-sm font-semibold whitespace-nowrap transition-colors duration-150 first:rounded-l-[5px] first:border-l-0 last:rounded-r-[5px]',
    active ? 'bg-ap-graphite text-ap-white' : 'bg-ap-white text-ap-ink hover:bg-ap-paper',
    className,
  )
}
