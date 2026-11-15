import { cn } from "cn"

/** Esqueleto de carga: barrido sutil (`ap-skeleton`, index.css); fijo con movimiento reducido. */
function Skeleton({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="skeleton"
      className={cn("ap-skeleton rounded-md", className)}
      {...props}
    />
  )
}

export { Skeleton }
