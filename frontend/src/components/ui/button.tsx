import { Button as ButtonPrimitive } from "@base-ui/react/button"
import { cva, type VariantProps } from "class-variance-authority"
import { CheckIcon, LoaderCircleIcon } from "lucide-react"
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
  "group/button relative inline-flex shrink-0 items-center justify-center rounded-md border border-transparent bg-clip-padding text-sm font-semibold whitespace-nowrap ap-transition-colors outline-none select-none active:not-disabled:scale-97 disabled:pointer-events-none disabled:opacity-50 disabled:aria-busy:opacity-75 aria-invalid:border-destructive [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
  {
    variants: {
      variant: {
        default:
          "ap-sheen bg-ap-red text-ap-white hover:bg-ap-red-hover active:bg-ap-red-hover [&_svg:not(.ap-live)]:transition-transform [&_svg:not(.ap-live)]:duration-200 hover:not-disabled:[&_svg:not(.ap-live)]:translate-x-0.5",
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
 * `loading`: acción en curso. Deshabilita el botón, marca `aria-busy` y muestra un spinner siempre en movimiento
 * (con movimiento reducido pulsa la opacidad). Con `loadingText` el spinner va junto al texto de progreso
 * («Ingresando…»); el contenido original sigue ocupando su sitio (invisible) para que el ancho no salte.
 * `success`: check breve en lugar del contenido (el llamador lo apaga a los ~600 ms).
 */
function Button({
  className,
  variant = "default",
  size = "default",
  loading = false,
  loadingText,
  success = false,
  disabled,
  children,
  ...props
}: ButtonPrimitive.Props &
  VariantProps<typeof buttonVariants> & { loading?: boolean; loadingText?: string; success?: boolean }) {
  const covered = loading || success
  return (
    <ButtonPrimitive
      data-slot="button"
      className={cn(buttonVariants({ variant, size, className }))}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loadingText ? (
        // Contenido y texto de progreso apilados en la misma celda: el botón mide lo que mida el más ancho, así
        // «Ingresando…» con su spinner nunca se recorta ni el botón cambia de ancho al empezar la carga.
        <span className="grid place-items-center">
          <span
            className={cn("col-start-1 row-start-1 inline-flex items-center justify-center gap-2", covered && "invisible")}
          >
            {children}
          </span>
          <span
            className={cn("col-start-1 row-start-1 inline-flex items-center justify-center gap-2", !loading && "invisible")}
            role={loading ? "status" : undefined}
            aria-hidden={loading ? undefined : true}
          >
            <LoaderCircleIcon className="ap-spinner ap-live size-4 shrink-0" aria-hidden />
            {loadingText}
          </span>
          {success ? (
            <CheckIcon className="ap-check-in ap-live col-start-1 row-start-1 size-4" aria-hidden />
          ) : null}
        </span>
      ) : covered ? (
        <>
          <span className="contents invisible">{children}</span>
          {loading ? (
            <LoaderCircleIcon
              className="ap-spinner ap-live absolute top-1/2 left-1/2 size-4 -translate-x-1/2 -translate-y-1/2"
              aria-hidden
            />
          ) : (
            <CheckIcon
              className="ap-check-in ap-live absolute top-1/2 left-1/2 size-4 -translate-x-1/2 -translate-y-1/2"
              aria-hidden
            />
          )}
        </>
      ) : (
        children
      )}
    </ButtonPrimitive>
  )
}

export { Button, buttonVariants }
