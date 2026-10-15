import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ShoppingCartIcon } from 'lucide-react'
import { Container, PageIntro } from '@/components/common/page-header'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useSession } from '@/features/auth/hooks'
import { setSession } from '@/features/auth/session-store'
import { formatCOP } from '@/lib/format'
import { cn } from '@/lib/utils'
import { getAddressService, getCheckoutService } from '../api/checkout-service'
import type { PaymentMethodCode, PersonAddressDto, SimulatedOutcome } from '../api/types'
import { AddressSelector } from '../components/address-selector'
import { CheckoutSummary, PayButton, type LineIssue } from '../components/checkout-summary'
import { PaymentMethodPicker, focusMethodPicker } from '../components/payment-method-picker'
import { PaymentRejectedPanel } from '../components/payment-rejected-panel'
import { classifyCheckoutError, leftNothingBehind, lineKey, type CheckoutProblem } from '../lib/checkout-errors'
import { clearCart, removeCartLines, setCartLineQuantity, useCartLines } from '../lib/cart-source'
import { checkoutPath, orderResultPath } from '../lib/paths'
import { saveOrderResult } from '../lib/order-result-store'
import { useSessionReady } from '../lib/use-session-ready'

const CRUMBS = [{ label: 'Inicio', to: '/' }, { label: 'Carrito', to: '/carrito' }, { label: 'Pagar' }]

/** Direcciones del comprador: carga, error con reintento y alta local de una nueva. */
function useAddresses() {
  const [state, setState] = useState<{ status: 'loading' | 'error' | 'ready'; data: PersonAddressDto[] }>({ status: 'loading', data: [] })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    getAddressService()
      .then((service) => service.list())
      .then(
        (data) => active && setState({ status: 'ready', data }),
        () => active && setState({ status: 'error', data: [] }),
      )
    return () => {
      active = false
    }
  }, [attempt])

  const reload = useCallback(() => {
    setState((current) => ({ ...current, status: 'loading' }))
    setAttempt((value) => value + 1)
  }, [])
  const add = useCallback((address: PersonAddressDto) => {
    setState((current) => ({
      status: 'ready',
      data: [...(address.isDefault ? current.data.map((item) => ({ ...item, isDefault: false })) : current.data), address],
    }))
  }, [])
  return { ...state, reload, add }
}

function Step({ number, title, last, children }: { number: string; title: string; last?: boolean; children: ReactNode }) {
  return (
    <li className="relative lg:pl-14">
      <div className="absolute top-0 left-0 hidden size-8 place-items-center rounded-full bg-asphalt text-xs font-bold text-white lg:grid" aria-hidden>
        {number}
      </div>
      {last ? null : <div className="thread-v absolute top-10 -bottom-6 left-[13px] hidden lg:block" aria-hidden />}
      <section aria-labelledby={`paso-${number}`} className="rounded-2xl bg-card p-5 shadow-[0_0_0_1px_rgb(0_0_0/0.07),0_18px_40px_-28px_rgb(23_27_32/0.35)] sm:p-6">
        <h2 id={`paso-${number}`} className="text-block-title mb-4">
          {title}
        </h2>
        {children}
      </section>
    </li>
  )
}

function CheckoutSkeleton() {
  return (
    <Container className="py-6 sm:py-8">
      <Skeleton className="mb-6 h-12 w-72" />
      <div className="grid gap-6 lg:grid-cols-[1fr_24rem]">
        <Skeleton className="h-96 rounded-2xl" />
        <Skeleton className="h-96 rounded-2xl" />
      </div>
    </Container>
  )
}

export default function CheckoutPage() {
  const ready = useSessionReady()
  const session = useSession()
  if (!ready) return <CheckoutSkeleton />
  if (!session) return <Navigate to="/ingresar" replace state={{ from: checkoutPath() }} />
  return <CheckoutView />
}

function CheckoutView() {
  const navigate = useNavigate()
  const lines = useCartLines()
  const addresses = useAddresses()

  const [selectedId, setSelectedId] = useState<string>()
  const [method, setMethod] = useState<PaymentMethodCode>('SIMULATED_CARD')
  const [outcome, setOutcome] = useState<SimulatedOutcome>('APPROVED')
  const [busy, setBusy] = useState(false)
  const [placed, setPlaced] = useState(false)
  const [problem, setProblem] = useState<CheckoutProblem>()
  const [addressNotice, setAddressNotice] = useState<string>()
  // Una por intento de compra: se conserva en reintentos ambiguos (red, 5xx) para que el servidor no cobre dos veces.
  const requestId = useRef<string>(crypto.randomUUID())
  const problemRef = useRef<HTMLDivElement>(null)

  const addressId = addresses.data.find((address) => address.id === selectedId)?.id ?? (addresses.data.find((address) => address.isDefault) ?? addresses.data[0])?.id
  const total = useMemo(() => lines.reduce((sum, line) => sum + line.unitPrice * line.quantity, 0), [lines])
  const busyNow = busy || placed

  const issues = useMemo(() => {
    const map = new Map<string, LineIssue>()
    if (problem?.kind === 'stock') for (const item of problem.items) map.set(lineKey(item), { kind: 'stock', available: item.available })
    if (problem?.kind === 'gone') for (const item of problem.items) map.set(lineKey(item), { kind: 'gone' })
    return map
  }, [problem])

  useEffect(() => {
    if (problem) problemRef.current?.focus()
  }, [problem])

  const pay = useCallback(async () => {
    if (busy || placed || !addressId || lines.length === 0) return
    setBusy(true)
    setProblem(undefined)
    setAddressNotice(undefined)
    try {
      const service = await getCheckoutService()
      const order = await service.placeOrder({
        clientRequestId: requestId.current,
        addressId,
        paymentMethod: method,
        simulatedOutcome: outcome,
        items: lines.map(({ productId, branchId, quantity }) => ({ productId, branchId, quantity })),
      })
      setPlaced(true)
      saveOrderResult(order)
      clearCart()
      navigate(orderResultPath(order.orderId), { state: { order } })
    } catch (error) {
      const next = classifyCheckoutError(error)
      if (next.kind === 'unauthorized') {
        setSession(undefined)
        navigate('/ingresar', { replace: true, state: { from: checkoutPath() } })
        return
      }
      if (leftNothingBehind(next)) requestId.current = crypto.randomUUID()
      if (next.kind === 'address') {
        setSelectedId(undefined)
        setAddressNotice('No encontramos esa dirección. Elige otra o agrega una nueva.')
        addresses.reload()
        document.getElementById('paso-01')?.scrollIntoView({ block: 'center' })
      } else {
        setProblem(next)
      }
    } finally {
      setBusy(false)
    }
  }, [addressId, addresses, busy, lines, method, navigate, outcome, placed])

  function adjustQuantities() {
    if (problem?.kind !== 'stock') return
    for (const item of problem.items) {
      if (item.available > 0) setCartLineQuantity(item, item.available)
      else removeCartLines([item])
    }
    setProblem(undefined)
  }

  function removeUnavailable() {
    if (problem?.kind !== 'gone') return
    removeCartLines(problem.items)
    setProblem(undefined)
  }

  if (placed) {
    return (
      <Container className="py-16" >
        <p role="status" className="text-center text-muted-foreground">
          Pago aprobado. Abriendo tu confirmación…
        </p>
      </Container>
    )
  }

  if (lines.length === 0) {
    return (
      <Container className="py-6 sm:py-8">
        <title>Pagar | Autopartco</title>
        <PageIntro crumbs={CRUMBS} label="Pagar" title="Tu carrito está vacío" />
        <EmptyState icon={<ShoppingCartIcon />} title="No hay repuestos para pagar" description="Busca el repuesto que necesitas y agrégalo al carrito para continuar.">
          <Button className="h-11 px-5" nativeButton={false} render={<Link to="/buscar" />}>
            Buscar repuestos
          </Button>
        </EmptyState>
      </Container>
    )
  }

  const blockedReason = !addressId ? 'Elige o agrega una dirección de entrega para continuar.' : undefined
  const canPay = Boolean(addressId) && !busyNow

  return (
    <Container className="py-6 pb-32 sm:py-8 lg:pb-12">
      <title>Pagar | Autopartco</title>
      <PageIntro crumbs={CRUMBS} label="Pagar" title="Confirma tu pedido" description="Elige a dónde llevamos tu pedido y cómo pagas. Revisa el resumen antes de confirmar." />

      <div className="grid items-start gap-6 lg:grid-cols-[1fr_24rem] lg:gap-8">
        <div className="flex flex-col gap-6">
          {problem ? (
            <div
              ref={problemRef}
              tabIndex={-1}
              role={problem.kind === 'unavailable' ? undefined : 'alert'}
              className={cn('rounded-lg outline-none', problem.kind !== 'unavailable' && 'border border-destructive/25 bg-destructive/5 p-4')}
            >
              <ProblemView problem={problem} onAdjust={adjustQuantities} onRemove={removeUnavailable} onRetry={pay} onTryAnotherMethod={() => {
                setProblem(undefined)
                focusMethodPicker()
              }} />
            </div>
          ) : null}

          <ol className="flex flex-col gap-6">
            <Step number="01" title="Dirección de entrega">
              <AddressSelector
                status={addresses.status}
                addresses={addresses.data}
                selectedId={addressId}
                notice={addressNotice}
                disabled={busyNow}
                onSelect={(id) => {
                  setSelectedId(id)
                  setAddressNotice(undefined)
                }}
                onCreated={(address) => {
                  addresses.add(address)
                  setSelectedId(address.id)
                  setAddressNotice(undefined)
                }}
                onRetry={addresses.reload}
              />
            </Step>
            <Step number="02" title="Pago" last>
              <PaymentMethodPicker method={method} outcome={outcome} disabled={busyNow} onMethodChange={setMethod} onOutcomeChange={setOutcome} />
            </Step>
          </ol>
        </div>

        <div className="lg:sticky lg:top-[calc(var(--header-h)+1rem)]">
          <CheckoutSummary lines={lines} issues={issues} total={total} canPay={canPay} busy={busyNow} onPay={pay} blockedReason={blockedReason} />
        </div>
      </div>

      {/* Barra de pago fija en móvil: el botón del resumen queda lejos al final de la página. */}
      <div className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-card/95 px-4 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] shadow-[0_-8px_24px_-16px_rgb(23_27_32/0.4)] backdrop-blur lg:hidden">
        <div className="mx-auto flex max-w-lg flex-col gap-1.5">
          <PayButton total={total} canPay={canPay} busy={busyNow} onPay={pay} />
          {blockedReason ? <p className="text-center text-xs text-muted-foreground">{blockedReason}</p> : <p className="text-center text-xs text-muted-foreground">Total informativo {formatCOP(total)} · lo confirma Autopartco al pagar</p>}
        </div>
      </div>
    </Container>
  )
}

interface ProblemViewProps {
  problem: CheckoutProblem
  onAdjust: () => void
  onRemove: () => void
  onRetry: () => void
  onTryAnotherMethod: () => void
}

function ProblemView({ problem, onAdjust, onRemove, onRetry, onTryAnotherMethod }: ProblemViewProps) {
  switch (problem.kind) {
    case 'rejected':
      return <PaymentRejectedPanel amount={problem.amount} onTryAnotherMethod={onTryAnotherMethod} />
    case 'stock':
      return (
        <ProblemText title="Algunos repuestos tienen menos unidades de las que pediste" body="No se cobró nada. Ajusta las cantidades a lo disponible y vuelve a pagar.">
          <Button type="button" className="h-11 px-4" onClick={onAdjust}>
            Ajustar cantidades
          </Button>
        </ProblemText>
      )
    case 'gone':
      return (
        <ProblemText title="Algunos repuestos ya no están disponibles" body="No se cobró nada. Quítalos del carrito para continuar con el resto.">
          <Button type="button" className="h-11 px-4" onClick={onRemove}>
            Quitar del carrito
          </Button>
        </ProblemText>
      )
    case 'changed':
      return <ProblemText title="Un precio cambió mientras pagabas" body="Revisa el resumen e inténtalo de nuevo. No se cobró nada." />
    case 'duplicate':
      return (
        <ProblemText title="Este pedido ya se está procesando" body="Revisa tus pedidos antes de volver a pagar.">
          <Button variant="outline" className="h-11 px-4" nativeButton={false} render={<Link to="/" />}>
            Ir al inicio
          </Button>
        </ProblemText>
      )
    case 'invalid':
      return <ProblemText title="No pudimos procesar tu pedido" body={problem.message} />
    case 'unavailable':
      return <ErrorState title="No pudimos completar el pago" description="Hubo un problema de conexión o del servidor. Si reintentas, no se cobrará dos veces." onRetry={onRetry} />
    default:
      return null
  }
}

function ProblemText({ title, body, children }: { title: string; body: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col gap-3">
      <p className="font-semibold text-destructive">{title}</p>
      <p className="max-w-prose text-sm text-foreground/85">{body}</p>
      {children ? <div className="flex flex-wrap gap-2">{children}</div> : null}
    </div>
  )
}
