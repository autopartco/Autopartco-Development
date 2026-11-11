import { MessageCircleIcon } from 'lucide-react'
import { buttonVariants } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'
import { trackClick } from '@/lib/visibility'
import { canonicalUrl, whatsappUrl } from '../../lib/store-links'

export interface WhatsappTarget {
  /** Sin sucursal (solo el WhatsApp público del perfil) el clic se abre pero no se mide: el servidor exige sede. */
  branchId?: string
  name: string
  phone: string
}

interface WhatsappButtonProps {
  storeId: string
  targets: WhatsappTarget[]
  /** Plantilla del almacén con `{producto}` y `{enlace}`. */
  template?: string
  /** Nombre del repuesto (o de la tienda si es una consulta general). */
  subject: string
  /** Ruta interna que se envía como `{enlace}`. */
  path: string
  productId?: string
  label?: string
  variant?: 'default' | 'outline'
  size?: 'default' | 'sm'
  className?: string
}

/**
 * «Cotizar por WhatsApp»: abre `wa.me` en otra pestaña con el mensaje de interés y registra el clic de la sucursal.
 * Con varias sedes ofrece un menú para elegir a cuál escribir. Sin número no se muestra.
 */
export function WhatsappButton({
  storeId,
  targets,
  template,
  subject,
  path,
  productId,
  label = 'WhatsApp',
  variant = 'default',
  size = 'default',
  className,
}: WhatsappButtonProps) {
  const valid = targets.filter((target) => target.phone)
  if (valid.length === 0) return null
  const classes = cn(buttonVariants({ variant, size }), className)
  const link = canonicalUrl(path)
  const open = (target: WhatsappTarget) => {
    if (target.branchId) trackClick('WHATSAPP_CLICK', storeId, target.branchId, productId)
  }

  if (valid.length === 1) {
    const target = valid[0]!
    const href = whatsappUrl(target.phone, template, subject, link)
    if (!href) return null
    return (
      <a href={href} target="_blank" rel="noopener noreferrer" className={classes} onClick={() => open(target)}>
        <MessageCircleIcon aria-hidden />
        {label}
        <span className="sr-only"> (se abre en una pestaña nueva)</span>
      </a>
    )
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger className={classes}>
        <MessageCircleIcon aria-hidden />
        {label}
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="min-w-64">
        <DropdownMenuGroup>
          <DropdownMenuLabel>¿Con qué sede quieres hablar?</DropdownMenuLabel>
          {valid.map((target) => {
            const href = whatsappUrl(target.phone, template, subject, link)
            if (!href) return null
            return (
              <DropdownMenuItem
                key={target.branchId ?? target.phone}
                render={<a href={href} target="_blank" rel="noopener noreferrer" />}
                onClick={() => open(target)}
              >
                {target.name}
              </DropdownMenuItem>
            )
          })}
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
