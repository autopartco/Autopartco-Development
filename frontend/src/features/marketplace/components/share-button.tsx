import { Share2Icon } from 'lucide-react'
import { toast } from 'sonner'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'
import { shareUrl, trackShare, type ShareChannel } from '@/lib/visibility'

interface ShareTarget {
  channel: ShareChannel
  label: string
  /** URL de compartir de la red; sin ella solo se copia el enlace (TikTok e Instagram no tienen una para web). */
  intent?: (url: string, title: string) => string
  /** Dónde pegarlo, para el aviso tras copiar. */
  pasteIn?: string
}

const TARGETS: ShareTarget[] = [
  { channel: 'wa', label: 'WhatsApp', intent: (url, title) => `https://wa.me/?text=${encodeURIComponent(`${title} ${url}`)}` },
  { channel: 'fb', label: 'Facebook', intent: (url) => `https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(url)}` },
  { channel: 'x', label: 'X', intent: (url, title) => `https://x.com/intent/post?url=${encodeURIComponent(url)}&text=${encodeURIComponent(title)}` },
  { channel: 'tg', label: 'Telegram', intent: (url, title) => `https://t.me/share/url?url=${encodeURIComponent(url)}&text=${encodeURIComponent(title)}` },
  { channel: 'tt', label: 'TikTok', pasteIn: 'TikTok' },
  { channel: 'ig', label: 'Instagram', pasteIn: 'Instagram' },
]

async function copy(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}

interface ShareButtonProps {
  /** Ruta interna (`/producto/…` o `/tiendas/…`). */
  path: string
  /** Texto que acompaña al enlace en WhatsApp, X y Telegram. */
  title: string
  storeId: string
  productId?: string
  className?: string
}

/**
 * Compartir con origen medible: cada opción copia al portapapeles el enlace con `?ref=<canal>` (y abre la red si
 * tiene URL de compartir), para que la tienda sepa por dónde llegan las visitas. TikTok e Instagram solo copian.
 */
export function ShareButton({ path, title, storeId, productId, className }: ShareButtonProps) {
  async function share(target: ShareTarget | { channel: 'link'; label: string; pasteIn?: undefined; intent?: undefined }) {
    const url = shareUrl(path, target.channel)
    // Copiar arranca antes de abrir la red (la pestaña nueva quita el foco y el portapapeles lo exige); la ventana se
    // abre en el mismo gesto del clic, sin esperar, para que el navegador no la bloquee.
    const copying = copy(url)
    if (target.intent) window.open(target.intent(url, title), '_blank', 'noopener,noreferrer')
    const copied = await copying
    trackShare(target.channel, storeId, productId)
    if (copied) {
      toast.success('Enlace copiado', {
        description: target.pasteIn ? `Pégalo en ${target.pasteIn} para compartirlo.` : undefined,
      })
    } else if (!target.intent) {
      toast.error('No pudimos copiar el enlace', { description: url })
    }
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label="Compartir"
        title="Compartir"
        className={cn(
          'inline-flex size-11 shrink-0 items-center justify-center rounded-md border border-input bg-ap-white text-ap-graphite outline-none transition-colors hover:border-ap-graphite hover:bg-ap-paper focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ap-red data-popup-open:bg-ap-paper',
          className,
        )}
      >
        <Share2Icon className="size-5" aria-hidden />
      </DropdownMenuTrigger>
      <DropdownMenuContent className="min-w-52">
        <DropdownMenuGroup>
          <DropdownMenuLabel>Compartir en</DropdownMenuLabel>
          {TARGETS.map((target) => (
            <DropdownMenuItem key={target.channel} onClick={() => void share(target)}>
              {target.label}
            </DropdownMenuItem>
          ))}
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={() => void share({ channel: 'link', label: 'Copiar enlace' })}>Copiar enlace</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
