import { Link, NavLink, Outlet, useParams } from 'react-router'
import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'

const NAV_ITEMS = [
  { to: 'inicio', label: 'Inicio' },
  { to: 'plan', label: 'Plan y suscripción' },
]

/** Cabecera y navegación del portal de un almacén. El servidor decide permisos; esto solo oculta. */
export function PortalLayout() {
  const { storeId } = useParams()

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6 px-4 py-6">
      <nav aria-label="Portal del almacén" className="flex flex-wrap items-center gap-2 border-b pb-3">
        <Link to="/portal" className="mr-4 text-sm font-medium text-muted-foreground hover:text-foreground">
          Mis almacenes
        </Link>
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={`/portal/${storeId}/${item.to}`}
            className={({ isActive }) =>
              cn(
                'rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
                isActive ? 'bg-muted text-foreground' : 'text-muted-foreground hover:text-foreground',
              )
            }
          >
            {item.label}
          </NavLink>
        ))}
      </nav>
      <main id="contenido-portal" className="min-w-0">
        <Card>
          <CardContent className="pt-6">
            <Outlet />
          </CardContent>
        </Card>
      </main>
    </div>
  )
}
