import { Button as ButtonPrimitive } from "@base-ui/react/button"
import { cva, type VariantProps } from "class-variance-authority"
import { LoaderCircleIcon } from "lucide-react"
import { cn } from "cn"

/**
 * Botones (§8.1). Radio de control 6px, sin sombra, gradiente ni forma píldora.
 * - default: primario rojo. Una acción principal por región.
 * - outline / secondary: secundario, superficie blanca con borde y texto grafito.
 * - ghost: terciario, sin contenedor, para acciones contextuales.
 * - destructive: rojo semántico; acompañar de confirmación cuando corresponda.
 * - link: enlace en línea.
 * Tamaño `default` y `icon` de 44px (objetivo táctil); `sm`/`xs` solo en barras densas.
 * El foco lo dibuja la regla global `:focus-visible` (index.css).
 */
const buttonVariants = cva(
  "group/button relative inline-flex shrink-0 items-center justify-center rounded-md border border-transparent bg-clip-padding text-sm font-semibold whitespace-nowrap ap-transition-colors outline-none select-none active:not-disabled:translate-y-px disabled:pointer-events-none disabled:opacity-50 disabled:aria-busy:opacity-75 aria-invalid:border-destructive [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
  {
    variants: {
      variant: {
        default: "bg-ap-red text-ap-white hover:bg-ap-red-hover active:bg-ap-red-hover",
        outline:
          "border-input bg-ap-white text-ap-graphite hover:border-ap-graphite hover:bg-ap-paper aria-expanded:bg-ap-paper",
        secondary:
          "border-input bg-ap-white text-ap-graphite hover:border-ap-graphite hover:bg-ap-paper aria-expanded:bg-ap-paper",
        ghost:
          "text-ap-graphite hover:bg-ap-paper aria-expanded:bg-ap-paper",
        destructive:
          "bg-ap-danger text-ap-white hover:bg-[color-mix(in_oklab,var(--ap-danger),black_18%)]",
        link: "font-semibold text-ap-red underline-offset-4 hover:text-ap-red-hover hover:underline",
      },
      size: {
        default:
          "h-11 gap-2 px-4 has-data-[icon=inline-end]:pr-3 has-data-[icon=inline-start]:pl-3",
        xs: "h-7 gap-1 rounded-sm px-2 text-xs has-data-[icon=inline-end]:pr-1.5 has-data-[icon=inline-start]:pl-1.5 [&_svg:not([class*='size-'])]:size-3",
        sm: "h-9 gap-1.5 px-3 text-[0.8125rem] has-data-[icon=inline-end]:pr-2 has-data-[icon=inline-start]:pl-2 [&_svg:not([class*='size-'])]:size-3.5",
        lg: "h-12 gap-2 px-5 text-base has-data-[icon=inline-end]:pr-4 has-data-[icon=inline-start]:pl-4",
        icon: "size-11",
        "icon-xs": "size-7 rounded-sm [&_svg:not([class*='size-'])]:size-3",
        "icon-sm": "size-9",
        "icon-lg": "size-12",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
)

/**
 * `loading`: acción en curso. Deshabilita el botón, marca `aria-busy` y muestra un spinner de 16px
 * centrado; el contenido sigue ocupando su sitio (invisible) para que el ancho y el alto no cambien.
 */
function Button({
  className,
  variant = "default",
  size = "default",
  loading = false,
  disabled,
  children,
  ...props
}: ButtonPrimitive.Props & VariantProps<typeof buttonVariants> & { loading?: boolean }) {
  return (
    <ButtonPrimitive
      data-slot="button"
      className={cn(buttonVariants({ variant, size, className }))}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading ? (
        <>
          <span className="contents invisible">{children}</span>
          <LoaderCircleIcon
            className="absolute top-1/2 left-1/2 size-4 -translate-x-1/2 -translate-y-1/2 animate-spin motion-reduce:animate-none"
            aria-hidden
          />
        </>
      ) : (
        children
      )}
    </ButtonPrimitive>
  )
}

export { Button, buttonVariants }
