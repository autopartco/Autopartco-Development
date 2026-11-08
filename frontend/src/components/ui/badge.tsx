import { mergeProps } from "@base-ui/react/merge-props"
import { useRender } from "@base-ui/react/use-render"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "cn"

/**
 * Etiquetas compactas (§8.4): radio 4px, texto siempre explícito (el color nunca
 * es el único portador del estado). Variantes semánticas: success, warning,
 * danger, info y neutral. "Tienda verificada" y "Disponible" son etiquetas distintas.
 */
const badgeVariants = cva(
  "group/badge inline-flex h-5 w-fit shrink-0 items-center justify-center gap-1 overflow-hidden rounded-sm border border-transparent px-1.5 text-xs leading-none font-semibold whitespace-nowrap transition-colors has-data-[icon=inline-end]:pr-1 has-data-[icon=inline-start]:pl-1 aria-invalid:border-destructive [&>svg]:pointer-events-none [&>svg]:size-3!",
  {
    variants: {
      variant: {
        default: "bg-ap-red text-ap-white [a]:hover:bg-ap-red-hover",
        secondary:
          "border-ap-line bg-ap-paper text-ap-ink [a]:hover:border-ap-line-strong",
        destructive: "border-ap-danger/25 bg-ap-danger-soft text-ap-danger",
        outline:
          "border-ap-line-strong bg-ap-white text-ap-ink [a]:hover:bg-ap-paper",
        ghost: "text-ap-muted hover:bg-ap-paper",
        link: "text-ap-red underline-offset-4 hover:underline",
        success: "border-ap-success/25 bg-ap-success-soft text-ap-success",
        warning: "border-ap-warning/25 bg-ap-warning-soft text-ap-warning",
        danger: "border-ap-danger/25 bg-ap-danger-soft text-ap-danger",
        info: "border-ap-info/25 bg-ap-info-soft text-ap-info",
        neutral: "border-ap-line bg-ap-paper text-ap-muted",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
)

function Badge({
  className,
  variant = "default",
  render,
  ...props
}: useRender.ComponentProps<"span"> & VariantProps<typeof badgeVariants>) {
  return useRender({
    defaultTagName: "span",
    props: mergeProps<"span">(
      {
        className: cn(badgeVariants({ variant }), className),
      },
      props
    ),
    render,
    state: {
      slot: "badge",
      variant,
    },
  })
}

export { Badge, badgeVariants }
