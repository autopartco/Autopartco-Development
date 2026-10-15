import { Progress as ProgressPrimitive } from "@base-ui/react/progress"
import { cn } from "cn"

/** Barra de progreso. El indicador usa el ancho calculado por Base UI (`--progress` no hace falta). */
function Progress({
  className,
  indicatorClassName,
  value,
  ...props
}: ProgressPrimitive.Root.Props & { indicatorClassName?: string }) {
  return (
    <ProgressPrimitive.Root data-slot="progress" value={value} className={cn("w-full", className)} {...props}>
      <ProgressPrimitive.Track
        data-slot="progress-track"
        className="relative block h-2 w-full overflow-hidden rounded-full bg-concrete"
      >
        <ProgressPrimitive.Indicator
          data-slot="progress-indicator"
          className={cn("block h-full rounded-full bg-foreground transition-[width] duration-300 ease-out-snap", indicatorClassName)}
        />
      </ProgressPrimitive.Track>
    </ProgressPrimitive.Root>
  )
}

export { Progress }
