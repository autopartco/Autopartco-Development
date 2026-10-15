import { Fragment } from 'react'
import { Link, useNavigate } from 'react-router'
import { ChevronDownIcon, CircleUserRoundIcon, LogOutIcon, UserRoundIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'
import { SELLER_SETUP_PATH, useSellerCta, type SellerCta } from '@/features/seller/hooks'
import type { Session } from '../api/types'
import { useSession, useSignOut } from '../hooks'
import {
  ACCOUNT_KIND_LABEL,
  accountKindOf,
  accountMenuGroups,
  lastPortalStore,
  type AccountMenuArea,
} from '../lib/account-menu-items'

const ON_DARK = 'text-white hover:bg-header-hover hover:text-white'

function firstName(name: string): string {
  return name.split(/\s+/)[0] ?? name
}

export interface AccountMenuContext {
  /** Dónde está el menú (cambia "Ir al portal" / "Ir al marketplace"). */
  area?: AccountMenuArea
  /** Almacén abierto en el portal; en el marketplace se usa el último abierto. */
  storeId?: string
  /** Permisos del portal para ocultar secciones sin acceso (el servidor sigue decidiendo). */
  can?: (permission: string) => boolean
}

function groupsFor(
  session: Session,
  { area = 'marketplace', storeId, can }: AccountMenuContext,
  seller?: SellerCta,
) {
  return accountMenuGroups({
    sellerApplicationTo: seller?.state === 'review' ? seller.to : undefined,
    sellerSetupTo: seller?.application?.status === 'CONFIGURATION_PENDING' ? SELLER_SETUP_PATH : undefined,
    root: session.roles.includes('ROLE_ROOT'),
    kind: accountKindOf(session.roles),
    area,
    storeId: storeId ?? lastPortalStore(),
    can,
  })
}

/** Cierre de sesión. En el portal primero se sale: así la guarda no manda a login con un destino que ya no aplica. */
function useLeaveAndSignOut(area: AccountMenuArea) {
  const { signOut, pending } = useSignOut()
  const navigate = useNavigate()
  const leave = async () => {
    if (area === 'portal') navigate('/', { replace: true })
    await signOut()
  }
  return { leave, pending }
}

/** Nombre, correo y tipo de cuenta (Cliente, Almacén…), común a la cabecera y al menú móvil. */
function AccountIdentity({ session }: { session: Session }) {
  const kind = accountKindOf(session.roles)
  return (
    <div className="min-w-0">
      <p className="truncate font-semibold">{session.user.name}</p>
      <p className="truncate text-sm text-muted-foreground">{session.user.email}</p>
      <p className="mt-1.5 flex flex-wrap items-center gap-1.5">
        <span className="inline-flex h-5 items-center rounded-full bg-concrete px-2 label-mono text-foreground">
          <span className="sr-only">Tipo de cuenta: </span>
          {ACCOUNT_KIND_LABEL[kind]}
        </span>
        {session.simulated ? <span className="label-mono text-muted-foreground">Sesión simulada</span> : null}
      </p>
    </div>
  )
}

/**
 * Cuenta en una cabecera carbón (marketplace y portal). Sin sesión: "Ingresar" (icono en móvil) y
 * "Crear cuenta" en escritorio ancho. Con sesión: menú con nombre, tipo de cuenta, las opciones
 * de `account-menu-items.ts` y "Cerrar sesión". Solo cambia lo que se muestra: la autorización
 * la aplica el servidor.
 */
export function HeaderAccount({ className, ...context }: { className?: string } & AccountMenuContext) {
  const session = useSession()
  const area = context.area ?? 'marketplace'
  const { leave, pending } = useLeaveAndSignOut(area)
  const seller = useSellerCta()

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
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={`Cuenta de ${session.user.name}`}
        className={cn(
          'group/account inline-flex h-11 items-center gap-1.5 rounded-lg px-2.5 text-sm font-medium transition-colors data-popup-open:bg-header-hover lg:h-10',
          ON_DARK,
          className,
        )}
      >
        <CircleUserRoundIcon className="size-5" aria-hidden />
        <span className="hidden max-w-[8rem] truncate sm:inline">{firstName(session.user.name)}</span>
        <ChevronDownIcon
          className="hidden size-4 text-asphalt-muted transition-transform duration-200 ease-out-snap group-data-popup-open/account:rotate-180 sm:block"
          aria-hidden
        />
      </DropdownMenuTrigger>
      <DropdownMenuContent className="w-72 max-w-[calc(100vw-1.5rem)]">
        <div className="px-3 py-2.5">
          <AccountIdentity session={session} />
        </div>
        {groupsFor(session, context, seller).map((group) => (
          <Fragment key={group[0]?.key}>
            <DropdownMenuSeparator />
            {group.map((item) => (
              <DropdownMenuItem key={item.key} className="min-h-11" render={<Link to={item.to} />}>
                <item.icon aria-hidden />
                {item.label}
              </DropdownMenuItem>
            ))}
          </Fragment>
        ))}
        <DropdownMenuSeparator />
        <DropdownMenuItem className="min-h-11" disabled={pending} onClick={() => void leave()}>
          <LogOutIcon aria-hidden />
          {pending ? 'Cerrando sesión…' : 'Cerrar sesión'}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

/** Bloque de cuenta del menú móvil del marketplace (panel claro): mismas opciones que la cabecera. */
export function MobileMenuAccount({ onNavigate }: { onNavigate: () => void }) {
  const session = useSession()
  const { leave, pending } = useLeaveAndSignOut('marketplace')
  const seller = useSellerCta()
  if (session) {
    const links = groupsFor(session, { area: 'marketplace' }, seller).flat()
    return (
      <div className="flex flex-col gap-3">
        <AccountIdentity session={session} />
        {links.length > 0 ? (
          <ul className="-mx-3 flex flex-col gap-1">
            {links.map((item) => (
              <li key={item.key}>
                <Link
                  to={item.to}
                  onClick={onNavigate}
                  className="flex min-h-12 items-center gap-3 rounded-lg px-3 text-base font-semibold transition-colors hover:bg-concrete"
                >
                  <item.icon className="size-5 text-muted-foreground" aria-hidden />
                  {item.label}
                </Link>
              </li>
            ))}
          </ul>
        ) : null}
        <Button
          variant="outline"
          className="h-11 justify-start gap-2"
          disabled={pending}
          onClick={async () => {
            await leave()
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
