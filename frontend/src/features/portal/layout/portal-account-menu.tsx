import { Link, useNavigate } from 'react-router'
import { ChevronDownIcon, LogOutIcon, ShoppingBagIcon, StoreIcon } from 'lucide-react'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { initials } from '@/lib/format'
import { useSession, useSignOut } from '@/features/auth/hooks'

/** Menú de cuenta de la barra superior: datos de la sesión, cambio de almacén, marketplace y salir. */
export function PortalAccountMenu() {
  const session = useSession()
  const { signOut, pending } = useSignOut()
  const navigate = useNavigate()
  if (!session) return null
  const name = session.user.name

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={`Cuenta de ${name}`}
        className="inline-flex h-11 items-center gap-2 rounded-lg px-1.5 text-sm font-medium transition-colors outline-none hover:bg-concrete data-popup-open:bg-concrete lg:h-10"
      >
        <Avatar className="size-8 rounded-full bg-asphalt text-xs text-white">
          <AvatarFallback aria-hidden>{initials(name) || name.slice(0, 2).toUpperCase()}</AvatarFallback>
        </Avatar>
        <ChevronDownIcon className="hidden size-4 text-muted-foreground sm:block" aria-hidden />
      </DropdownMenuTrigger>
      <DropdownMenuContent className="min-w-64">
        <div className="px-3 py-2.5">
          <p className="font-semibold">{name}</p>
          <p className="truncate text-sm text-muted-foreground">{session.user.email}</p>
          {session.simulated ? <p className="mt-1.5 label-mono text-muted-foreground">Sesión simulada</p> : null}
        </div>
        <DropdownMenuSeparator />
        <DropdownMenuItem render={<Link to="/portal" />}>
          <StoreIcon aria-hidden />
          Mis almacenes
        </DropdownMenuItem>
        <DropdownMenuItem render={<Link to="/" />}>
          <ShoppingBagIcon aria-hidden />
          Ir al marketplace
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem
          disabled={pending}
          onClick={() => {
            // Primero se sale del portal: así la guarda no manda a login con un destino que ya no aplica.
            navigate('/', { replace: true })
            void signOut()
          }}
        >
          <LogOutIcon aria-hidden />
          {pending ? 'Cerrando sesión…' : 'Cerrar sesión'}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
