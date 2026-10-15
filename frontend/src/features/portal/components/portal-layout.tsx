import { Suspense, useCallback, useEffect, useMemo, useRef, useState, type RefObject } from 'react'
import { Outlet, useLocation, useParams } from 'react-router'
import { MenuIcon, XIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Sheet, SheetClose, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { ApiError } from '@/lib/api'
import { invalidateAsync, useAsync } from '@/lib/use-async'
import { getPortalService } from '../api/portal-service'
import { getSubscriptionService } from '../api/subscription-service'
import type { StorePermission, Subscription } from '../api/types'
import { PortalContext, type PortalContextValue } from '../context'
import { isPlanError } from '../lib/errors'
import { rememberPortalStore } from '@/features/auth/lib/account-menu-items'
import { PortalBrand, PortalHeader } from '../layout/portal-header'
import { PortalNav } from '../layout/portal-nav'
import { PlanBadge } from '../layout/plan-badge'
import { StoreSwitcher } from '../layout/store-switcher'
import { ListSkeleton } from './list-skeleton'
import { QueryError } from './query-boundary'
import { SubscriptionBanner } from './subscription-banner'
import { UpgradeDialog } from './upgrade-dialog'
import { StoreInactiveNotice } from './store-inactive-notice'

/**
 * Barra lateral carbón: continúa la cabecera hacia abajo. En el `Sheet` móvil lleva arriba
 * el logo y la etiqueta del portal (en escritorio ya están en la cabecera).
 */
function SidebarContent({ onNavigate, withBrand = false }: { onNavigate?: () => void; withBrand?: boolean }) {
  return (
    <div className="surface-asphalt flex h-full flex-col gap-6 py-5">
      {withBrand ? <PortalBrand className="h-11 pr-14 pl-5" /> : null}
      <div className="flex-1 overflow-y-auto">
        <PortalNav onNavigate={onNavigate} />
      </div>
    </div>
  )
}

function MobileMenu() {
  const [open, setOpen] = useState(false)
  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger
        render={
          <Button
            variant="ghost"
            className="size-11 shrink-0 text-white hover:bg-header-hover hover:text-white aria-expanded:bg-header-hover aria-expanded:text-white lg:hidden"
            aria-label="Abrir menú del portal"
          />
        }
      >
        <MenuIcon className="size-5" aria-hidden />
      </SheetTrigger>
      <SheetContent side="left" showCloseButton={false} className="w-[min(18rem,85vw)] gap-0 p-0 sm:max-w-72">
        <SheetHeader className="sr-only">
          <SheetTitle>Menú del portal</SheetTitle>
          <SheetDescription>Secciones del portal del almacén</SheetDescription>
        </SheetHeader>
        <SidebarContent withBrand onNavigate={() => setOpen(false)} />
        <SheetClose
          aria-label="Cerrar menú"
          render={<Button variant="ghost" className="absolute top-3 right-2 size-11 text-white hover:bg-header-hover hover:text-white" />}
        >
          <XIcon aria-hidden />
        </SheetClose>
      </SheetContent>
    </Sheet>
  )
}

/** Mueve el foco al contenido al cambiar de pantalla, para lectores de pantalla y teclado. */
function useFocusMainOnNavigate(mainRef: RefObject<HTMLElement | null>) {
  const { pathname } = useLocation()
  const first = useRef(true)
  useEffect(() => {
    if (first.current) {
      first.current = false
      return
    }
    mainRef.current?.focus({ preventScroll: true })
  }, [pathname, mainRef])
}

/**
 * Carcasa del portal de un almacén (`/portal/:storeId/*`): barra lateral, barra superior con
 * selector de tienda, insignia del plan y cuenta; aviso de suscripción y contexto compartido.
 * Solo oculta navegación según los permisos: el servidor autoriza cada petición.
 */
export function PortalLayout() {
  const { storeId = '' } = useParams()
  // Al cambiar de tienda se remonta todo, así nunca se ven datos de la anterior.
  return <PortalShell key={storeId} storeId={storeId} />
}

function PortalShell({ storeId }: { storeId: string }) {
  const mainRef = useRef<HTMLElement>(null)
  useFocusMainOnNavigate(mainRef)

  const stores = useAsync('portal:stores', async () => (await getPortalService()).myStores())
  const permissions = useAsync(`portal:${storeId}:permissions`, async () => (await getPortalService()).permissions(storeId))
  const subscriptionQuery = useAsync(`portal:${storeId}:subscription`, async () =>
    (await getSubscriptionService()).subscription(storeId),
  )
  const [applied, setApplied] = useState<{ value: Subscription; base: Subscription | undefined }>()
  const [upgradeError, setUpgradeError] = useState<ApiError>()

  const store = stores.data?.find((candidate) => candidate.storeId === storeId)
  // Atajo del menú de cuenta en el marketplace: enlaza las secciones del último almacén abierto.
  useEffect(() => {
    if (store?.applicationStatus === 'ACTIVE') rememberPortalStore(storeId)
  }, [store, storeId])
  const permissionSet = useMemo(() => new Set<string>(permissions.data ?? []), [permissions.data])
  // Un pago devuelve la suscripción nueva: se muestra esa hasta que la recarga del servidor la reemplaza.
  const subscription = applied && applied.base === subscriptionQuery.data ? applied.value : subscriptionQuery.data
  const currentSubscription = subscriptionQuery.data

  const can = useCallback((permission: StorePermission) => permissionSet.has(permission), [permissionSet])
  const invalidateStore = useCallback(() => invalidateAsync(`portal:${storeId}:`), [storeId])
  const reloadSubscription = useCallback(() => invalidateAsync(`portal:${storeId}:subscription`), [storeId])
  const applySubscription = useCallback(
    (value: Subscription) => setApplied({ value, base: currentSubscription }),
    [currentSubscription],
  )
  const promptUpgrade = useCallback((error: unknown) => {
    if (!isPlanError(error)) return false
    setUpgradeError(error)
    return true
  }, [])

  const value: PortalContextValue = {
    storeId,
    stores: stores.data ?? [],
    store,
    permissionsReady: permissions.status === 'success',
    can,
    subscription,
    subscriptionError: subscriptionQuery.status === 'error' ? subscriptionQuery.error : undefined,
    reloadSubscription,
    applySubscription,
    promptUpgrade,
    invalidateStore,
  }

  // Sin acceso: permisos rechazados por el servidor, o la tienda no figura entre las de la persona.
  const missingFromList = stores.status === 'success' && stores.data !== undefined && !store
  const accessError: unknown = permissions.status === 'error'
    ? permissions.error
    : missingFromList
      ? new ApiError('Tu cuenta no pertenece a este almacén.', { status: 403, code: 'FORBIDDEN' })
      : undefined
  const inactive = store !== undefined && store.applicationStatus !== 'ACTIVE'
  const plansPath = `/portal/${encodeURIComponent(storeId)}/plan`

  let content
  if (accessError !== undefined) {
    content = <QueryError error={accessError} what="el portal de este almacén" onRetry={permissions.status === 'error' ? permissions.retry : undefined} />
  } else if (inactive && store) {
    content = <StoreInactiveNotice status={store.applicationStatus} name={store.businessName} />
  } else {
    content = (
      <Suspense fallback={<ListSkeleton label="Cargando pantalla" />}>
        <Outlet />
      </Suspense>
    )
  }

  return (
    <PortalContext.Provider value={value}>
      <div className="flex min-h-svh flex-col bg-background">
        <a
          href="#contenido"
          className="sr-only z-[60] rounded-md bg-white px-4 py-2.5 font-semibold text-foreground shadow-lg focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
        >
          Saltar al contenido
        </a>
        <PortalHeader start={<MobileMenu />} />
        <div className="flex-1 lg:grid lg:grid-cols-[15rem_minmax(0,1fr)]">
          <aside className="hidden border-t border-asphalt-line bg-asphalt lg:block">
            <div className="sticky top-16 h-[calc(100svh-4rem)]">
              <SidebarContent />
            </div>
          </aside>
          <div className="flex min-w-0 flex-col">
            {/* Barra de contexto clara: almacén actual y plan, bajo la cabecera carbón. */}
            <div className="sticky top-14 z-30 flex h-14 items-center gap-3 border-b border-border bg-card px-4 sm:px-6 lg:top-16 lg:px-8">
              <div className="flex min-w-0 flex-1 items-center">
                <StoreSwitcher />
              </div>
              {store?.applicationStatus === 'ACTIVE' ? <PlanBadge /> : null}
            </div>
            {subscription && !inactive && accessError === undefined ? (
              <SubscriptionBanner subscription={subscription} plansPath={plansPath} canManage={subscription.canManage} />
            ) : null}
            <main
              id="contenido"
              ref={mainRef}
              tabIndex={-1}
              className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 outline-none sm:px-6 lg:px-8 lg:py-8"
            >
              {content}
            </main>
          </div>
        </div>
      </div>
      <UpgradeDialog
        error={upgradeError}
        planName={subscription?.plan.name}
        plansPath={plansPath}
        onClose={() => setUpgradeError(undefined)}
      />
    </PortalContext.Provider>
  )
}
