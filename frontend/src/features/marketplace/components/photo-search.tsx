import { Popover } from '@base-ui/react/popover'
import { CameraIcon, ImageUpIcon, XIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

/**
 * Acceso a "Buscar con una foto", hoy como "Próximamente" (igual que "Usar mi
 * ubicación" en el selector de ciudad). Abre un panel que explica para qué
 * servirá, con las dos opciones deshabilitadas.
 *
 * No hay `<input type="file">`, ni `capture`, ni permisos de cámara: nada se lee
 * ni se sube a ningún sitio.
 * TODO(api): no existe un endpoint de búsqueda por imagen. Cuando exista, estas
 * opciones abrirán el selector de archivos o la cámara.
 */
export function PhotoSearchButton({ className }: { className?: string }) {
  return (
    <Popover.Root>
      <Popover.Trigger
        aria-label="Buscar con una foto (próximamente)"
        // El botón vive dentro de un campo blanco, también en la cabecera oscura: foco rojo e
        // interior (5.7:1 sobre blanco) en vez del blanco de `surface-asphalt`, que no se vería.
        className={cn(
          'grid size-11 shrink-0 place-items-center rounded-md text-muted-foreground [--focus:var(--color-brand)] focus-visible:-outline-offset-2! transition-colors hover:bg-concrete hover:text-foreground data-popup-open:bg-concrete data-popup-open:text-foreground',
          className,
        )}
      >
        <CameraIcon className="size-5" aria-hidden />
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Positioner side="bottom" align="end" sideOffset={8} collisionPadding={12} className="isolate z-50">
          <Popover.Popup className="w-[min(20rem,calc(100vw-1.5rem))] origin-(--transform-origin) rounded-xl bg-popover p-4 text-popover-foreground shadow-lg ring-1 ring-foreground/10 outline-none transition-[opacity,transform] duration-150 data-ending-style:scale-95 data-ending-style:opacity-0 data-starting-style:scale-95 data-starting-style:opacity-0">
            <div className="flex items-start justify-between gap-3">
              <div className="flex flex-col gap-1.5">
                <span className="w-fit rounded-sm bg-concrete px-1.5 py-0.5 label-mono text-muted-foreground">
                  Próximamente
                </span>
                <Popover.Title className="text-base leading-tight font-bold tracking-tight">
                  Buscar con una foto
                </Popover.Title>
              </div>
              <Popover.Close
                aria-label="Cerrar"
                className="-mt-1 -mr-1 grid size-11 shrink-0 place-items-center rounded-md text-muted-foreground hover:bg-concrete hover:text-foreground"
              >
                <XIcon className="size-4" aria-hidden />
              </Popover.Close>
            </div>
            <Popover.Description className="mt-2 text-sm text-muted-foreground">
              Pronto podrás fotografiar el repuesto o su referencia para identificarlo y ver en qué almacenes está.
            </Popover.Description>
            <ul className="mt-3 flex flex-col gap-1" aria-label="Opciones de foto">
              {PHOTO_OPTIONS.map((option) => (
                <li key={option.label}>
                  <button
                    type="button"
                    disabled
                    className="flex min-h-11 w-full cursor-not-allowed items-center gap-3 rounded-lg px-3 text-left text-sm font-medium text-muted-foreground"
                  >
                    <option.icon className="size-4 shrink-0" aria-hidden />
                    {option.label}
                  </button>
                </li>
              ))}
            </ul>
          </Popover.Popup>
        </Popover.Positioner>
      </Popover.Portal>
    </Popover.Root>
  )
}

const PHOTO_OPTIONS = [
  { label: 'Subir imagen', icon: ImageUpIcon },
  { label: 'Tomar foto', icon: CameraIcon },
] as const
