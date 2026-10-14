import { Menu } from '@base-ui/react/menu'
import { Link } from 'react-router'
import { ChevronDownIcon, CircleUserRoundIcon, LogOutIcon, UserRoundIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useSession, useSignOut } from '../hooks'

const ON_DARK = 'text-white hover:bg-header-hover hover:text-white'

function firstName(name: string): string {
  return name.split(/\s+/)[0] ?? name
}

/**
 * Cuenta en la cabecera carbón. Sin sesión: "Ingresar" (icono en móvil) y
 * "Crear cuenta" en escritorio ancho. Con sesión: menú con el nombre y "Cerrar sesión".
 * Solo cambia lo que se muestra: la autorización la aplica el servidor.
 */
export function HeaderAccount({ className }: { className?: string }) {
  const session = useSession()
  const { signOut, pending } = useSignOut()

  if (!session) {
    return (
      <div className={cn('flex items-center gap-1', className)}>
        <Button
          variant="ghost"
          className={cn('h-11 gap-1.5 px-2.5 lg:h-10', ON_DARK)}
          nativeButton={false}
          render={<Link to="/ingresar" aria-label="Ingresar" />}
        >
          <UserRoundIcon className="size-5 sm:hidden" aria-hidden />
          <span className="hidden sm:inline">Ingresar</span>
        </Button>
        <Button
          variant="outline"
          className="hidden h-10 border-white/40 bg-transparent px-3 text-white hover:border-white hover:bg-header-hover hover:text-white xl:inline-flex"
          nativeButton={false}
          render={<Link to="/registro" />}
        >
          Crear cuenta
        </Button>
      </div>
    )
  }

  return (
    <Menu.Root>
      <Menu.Trigger
        aria-label={`Cuenta de ${session.user.name}`}
        className={cn(
          'inline-flex h-11 items-center gap-1.5 rounded-lg px-2.5 text-sm font-medium transition-colors data-popup-open:bg-header-hover lg:h-10',
          ON_DARK,
          className,
        )}
      >
        <CircleUserRoundIcon className="size-5" aria-hidden />
        <span className="hidden max-w-[8rem] truncate sm:inline">{firstName(session.user.name)}</span>
        <ChevronDownIcon className="hidden size-4 text-asphalt-muted sm:block" aria-hidden />
      </Menu.Trigger>
      <Menu.Portal>
        <Menu.Positioner side="bottom" align="end" sideOffset={6} className="isolate z-50">
          <Menu.Popup className="min-w-64 origin-(--transform-origin) rounded-xl bg-popover p-1.5 text-popover-foreground shadow-lg ring-1 ring-foreground/10 outline-none transition-[opacity,transform] duration-150 data-ending-style:scale-95 data-ending-style:opacity-0 data-starting-style:scale-95 data-starting-style:opacity-0">
            <div className="px-3 py-2.5">
              <p className="font-semibold">{session.user.name}</p>
              <p className="truncate text-sm text-muted-foreground">{session.user.email}</p>
              {session.simulated ? <p className="mt-1.5 label-mono text-muted-foreground">Sesión simulada</p> : null}
            </div>
            <Menu.Separator className="mx-1.5 my-1 h-px bg-border" />
            <Menu.Item
              onClick={() => void signOut()}
              disabled={pending}
              className="flex min-h-11 cursor-pointer items-center gap-2.5 rounded-lg px-3 text-sm font-medium outline-none select-none data-disabled:opacity-50 data-highlighted:bg-concrete"
            >
              <LogOutIcon className="size-4 text-muted-foreground" aria-hidden />
              Cerrar sesión
            </Menu.Item>
          </Menu.Popup>
        </Menu.Positioner>
      </Menu.Portal>
    </Menu.Root>
  )
}

/** Bloque de cuenta del menú móvil (panel claro). */
export function MobileMenuAccount({ onNavigate }: { onNavigate: () => void }) {
  const session = useSession()
  const { signOut, pending } = useSignOut()
  if (session) {
    return (
      <div className="flex flex-col gap-2">
        <div>
          <p className="font-semibold">{session.user.name}</p>
          <p className="truncate text-sm text-muted-foreground">{session.user.email}</p>
          {session.simulated ? <p className="mt-1 label-mono text-muted-foreground">Sesión simulada</p> : null}
        </div>
        <Button
          variant="outline"
          className="h-11 justify-start gap-2"
          disabled={pending}
          onClick={async () => {
            await signOut()
            onNavigate()
          }}
        >
          <LogOutIcon aria-hidden />
          {pending ? 'Cerrando sesión…' : 'Cerrar sesión'}
        </Button>
      </div>
    )
  }
  return (
    <div className="grid grid-cols-2 gap-2">
      <Button className="h-11" nativeButton={false} render={<Link to="/ingresar" onClick={onNavigate} />}>
        Ingresar
      </Button>
      <Button variant="outline" className="h-11" nativeButton={false} render={<Link to="/registro" onClick={onNavigate} />}>
        Crear cuenta
      </Button>
    </div>
  )
}
