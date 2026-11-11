import { NavigationIcon } from 'lucide-react'
import { buttonVariants } from '@/components/ui/button'
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
import { trackClick } from '@/lib/visibility'
import type { StoreBranch } from '../../api/types'
import { directionsLinks } from '../../lib/store-links'

interface DirectionsMenuProps {
  storeId: string
  /** Una sede: menú de aplicaciones. Varias: el menú agrupa las aplicaciones por sede. */
  branches: StoreBranch[]
  variant?: 'default' | 'outline' | 'ghost'
  size?: 'default' | 'sm'
  className?: string
}

/** «Cómo llegar» con Google Maps, Waze o Apple Maps; cada elección registra un clic de la sede. Operable con teclado. */
export function DirectionsMenu({ storeId, branches, variant = 'outline', size = 'default', className }: DirectionsMenuProps) {
  const usable = branches.filter((branch) => directionsLinks(branch).length > 0)
  if (usable.length === 0) return null
  const grouped = usable.length > 1

  return (
    <DropdownMenu>
      <DropdownMenuTrigger className={cn(buttonVariants({ variant, size }), className)}>
        <NavigationIcon aria-hidden />
        Cómo llegar
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="min-w-60">
        {usable.map((branch, index) => (
          <DropdownMenuGroup key={branch.id}>
            {index > 0 ? <DropdownMenuSeparator /> : null}
            <DropdownMenuLabel>{grouped ? `Ir a ${branch.name}` : 'Abrir ruta en'}</DropdownMenuLabel>
            {directionsLinks(branch).map((link) => (
              <DropdownMenuItem
                key={link.id}
                render={<a href={link.href} target="_blank" rel="noopener noreferrer" />}
                onClick={() => trackClick('DIRECTIONS_CLICK', storeId, branch.id)}
              >
                {link.label}
                <span className="sr-only">{grouped ? ` hacia ${branch.name}` : ''} (se abre en una pestaña nueva)</span>
              </DropdownMenuItem>
            ))}
          </DropdownMenuGroup>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
