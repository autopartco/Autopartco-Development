import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { CheckIcon, Loader2Icon, ShoppingCartIcon, TriangleAlertIcon } from 'lucide-react'
import { Container, PageIntro } from '@/components/common/page-header'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useSession } from '@/features/auth/hooks'
import { setSession } from '@/features/auth/session-store'
import { formatCOP } from '@/lib/format'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { cn } from '@/lib/utils'
import { getAddressService, getCheckoutService } from '../api/checkout-service'
import type { CheckoutOptionsDto, PaymentMethodCode, PersonAddressDto, PlaceOrderRequestDto, SimulatedOutcome, WompiCheckoutDto } from '../api/types'
import { AddressSelector } from '../components/address-selector'
import { CheckoutSummary, PayButton, type LineIssue } from '../components/checkout-summary'
import { PaymentMethodPicker, focusMethodPicker } from '../components/payment-method-picker'
import { PaymentRejectedPanel } from '../components/payment-rejected-panel'
import { classifyCheckoutError, leftNothingBehind, lineKey, type CheckoutProblem } from '../lib/checkout-errors'
import { clearCart, removeCartLines, setCartLineQuantity, useCartLines } from '../lib/cart-source'
import { checkoutPath, orderResultPath } from '../lib/paths'
import { saveOrderResult } from '../lib/order-result-store'
import { isSafeCheckoutUrl, savePendingOrderPayment } from '../lib/order-payment'
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

/** Proveedor y métodos activos (`GET /marketplace/checkout-options`); el servidor es la fuente de verdad. */
function useCheckoutOptions() {
  const [state, setState] = useState<{ status: 'loading' | 'error' | 'ready'; data?: CheckoutOptionsDto }>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    getCheckoutService()
      .then((service) => service.options())
      .then(
        (data) => active && setState({ status: 'ready', data }),
        () => active && setState({ status: 'error' }),
      )
    return () => {
      active = false
    }
  }, [attempt])

  const reload = useCallback(() => {
    setState({ status: 'loading' })
    setAttempt((value) => value + 1)
  }, [])
  return { ...state, reload }
}

/** Estado visible de un paso: siempre con texto, nunca solo con color (§4.4). */
type StepState = 'loading' | 'pending' | 'complete' | 'error'

const STEP_STATE: Record<StepState, { label: string; variant: 'neutral' | 'success' | 'danger' }> = {
  loading: { label: 'Cargando', variant: 'neutral' },
  pending: { label: 'Pendiente', variant: 'neutral' },
  complete: { label: 'Listo', variant: 'success' },
  error: { label: 'Revisar', variant: 'danger' },
}

/** Paso del pago: sección abierta con número, título y estado; sin tarjeta ni hilo decorativo. */
function Step({ number, title, state, children }: { number: string; title: string; state: StepState; children: ReactNode }) {
  const status = STEP_STATE[state]
  return (
    <li>
      <section aria-labelledby={`paso-${number}`} className="border-t border-ap-graphite pt-4">
        <div className="mb-4 flex flex-wrap items-center gap-x-3 gap-y-2">
          <span
            aria-hidden
            className={cn(
              'grid size-8 shrink-0 place-items-center rounded-md border font-mono text-xs font-semibold tabular-nums',
              state === 'complete'
                ? 'border-ap-success/40 bg-ap-success-soft text-ap-success'
                : state === 'error'
                  ? 'border-ap-danger bg-ap-danger-soft text-ap-danger'
                  : 'border-ap-line-strong bg-ap-white text-ap-ink',
            )}
          >
            {state === 'complete' ? <CheckIcon className="size-4" /> : number}
          </span>
          <h2 id={`paso-${number}`} className="text-block-title text-ap-ink">
            <span className="sr-only">Paso {Number(number)}: </span>
            {title}
          </h2>
          <Badge variant={status.variant} className="ml-auto">
            {status.label}
          </Badge>
        </div>
        <div className="sm:pl-11">{children}</div>
      </section>
    </li>
  )
}

function CheckoutSkeleton() {
  return (
    <Container className="py-6 sm:py-8">
      <span className="sr-only" role="status">
        Cargando el pago
      </span>
      <div aria-hidden>
        <Skeleton className="mb-6 h-12 w-72" />
        <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_24rem]">
          <div className="flex flex-col gap-6">
            <Skeleton className="h-48" />
            <Skeleton className="h-48" />
          </div>
          <Skeleton className="h-96 rounded-xl" />
        </div>
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
  const options = useCheckoutOptions()

  const [selectedId, setSelectedId] = useState<string>()
  const [chosenMethod, setMethod] = useState<PaymentMethodCode>()
  const [outcome, setOutcome] = useState<SimulatedOutcome>('APPROVED')
  const place = useSubmitOnce(async (input: PlaceOrderRequestDto) => (await getCheckoutService()).placeOrder(input))
  const busy = place.pending
  /** `receipt`: pedido SIMULATED creado; `redirect`: saliendo hacia Wompi. */
  const [placed, setPlaced] = useState<'receipt' | 'redirect'>()
  const [problem, setProblem] = useState<CheckoutProblem>()
  const [addressNotice, setAddressNotice] = useState<string>()
  // Una por intento de compra: se conserva en reintentos ambiguos (red, 5xx) para que el servidor no cobre dos veces.
  const requestId = useRef<string>(crypto.randomUUID())
  const problemRef = useRef<HTMLDivElement>(null)

  const addressId = addresses.data.find((address) => address.id === selectedId)?.id ?? (addresses.data.find((address) => address.isDefault) ?? addresses.data[0])?.id
  const total = useMemo(() => lines.reduce((sum, line) => sum + line.unitPrice * line.quantity, 0), [lines])
  const busyNow = busy || placed !== undefined
  const methods = options.data?.methods ?? []
  // El método elegido solo vale si el servidor lo sigue ofreciendo; si no, el primero de la lista.
  const method = methods.find((item) => item.code === chosenMethod)?.code ?? methods[0]?.code
  const provider = options.data?.provider

  const issues = useMemo(() => {
    const map = new Map<string, LineIssue>()
    if (problem?.kind === 'stock') for (const item of problem.items) map.set(lineKey(item), { kind: 'stock', available: item.available })
    if (problem?.kind === 'gone') for (const item of problem.items) map.set(lineKey(item), { kind: 'gone' })
    return map
  }, [problem])

  useEffect(() => {
    if (problem) problemRef.current?.focus()
  }, [problem])

  /**
   * WOMPI (§2.2): guarda el pedido, vacía el carrito y lleva el navegador a la URL firmada sin tocarla.
   * Una repetición sin enlace (el pedido ya no se puede pagar) abre la página de resultado.
   */
  const goToGateway = useCallback(
    (checkout: WompiCheckoutDto) => {
      savePendingOrderPayment({ orderId: checkout.orderId, orderNumber: checkout.orderNumber, reference: checkout.payment.reference, lines })
      // Un pedido cancelado liberó los repuestos: el carrito se conserva para volver a intentarlo.
      if (checkout.status !== 'CANCELLED') clearCart()
      if (isSafeCheckoutUrl(checkout.payment.checkoutUrl)) {
        setPlaced('redirect')
        window.location.assign(checkout.payment.checkoutUrl)
      } else {
        setPlaced('receipt')
        navigate(orderResultPath(checkout.orderId))
      }
    },
    [lines, navigate],
  )

  const pay = useCallback(async () => {
    if (placed || !addressId || !method || lines.length === 0) return
    setProblem(undefined)
    setAddressNotice(undefined)
    // ÚNICA escritura de la página: el pedido se crea al pulsar «Pagar»; el candado evita el doble envío.
    const result = await place.run({
      clientRequestId: requestId.current,
      addressId,
      paymentMethod: method,
      simulatedOutcome: provider === 'SIMULATED' ? outcome : null,
      items: lines.map(({ productId, branchId, quantity }) => ({ productId, branchId, quantity })),
    })
    if (result.status === 'ok') {
      if (result.value.kind === 'redirect') {
        goToGateway(result.value.checkout)
        return
      }
      const order = result.value.order
      setPlaced('receipt')
      saveOrderResult(order)
      clearCart()
      navigate(orderResultPath(order.orderId), { state: { order } })
    } else if (result.status === 'error') {
      const next = classifyCheckoutError(result.error)
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
        if (next.kind === 'method') {
          setMethod(undefined)
          options.reload()
        }
        setProblem(next)
      }
    }
  }, [addressId, addresses, goToGateway, lines, method, navigate, options, outcome, place, placed, provider])

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
      <Container className="py-16">
        <p role="status" className="mx-auto flex max-w-md items-center justify-center gap-2 text-center text-ap-ink">
          <Loader2Icon className="size-5 shrink-0 animate-spin text-ap-muted" aria-hidden />
          {placed === 'redirect' ? 'Te estamos llevando a Wompi para completar el pago…' : 'Abriendo el estado de tu pedido…'}
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
          <Button nativeButton={false} render={<Link to="/buscar" />}>
            Buscar repuestos
          </Button>
        </EmptyState>
      </Container>
    )
  }

  const blockedReason = !addressId
    ? 'Elige o agrega una dirección de entrega para continuar.'
    : options.status === 'loading'
      ? 'Cargando los métodos de pago…'
      : !method
        ? 'Los pagos no están disponibles en este momento. Inténtalo de nuevo en unos minutos.'
        : undefined
  const canPay = Boolean(addressId) && Boolean(method) && !busyNow
  const addressState: StepState =
    addressNotice ? 'error' : addresses.status === 'loading' ? 'loading' : addressId ? 'complete' : 'pending'
  const paymentState: StepState =
    options.status === 'error' || problem?.kind === 'method' || problem?.kind === 'rejected'
      ? 'error'
      : options.status === 'loading'
        ? 'loading'
        : method
          ? 'complete'
          : 'pending'

  return (
    <Container className="py-6 pb-32 sm:py-8 lg:pb-12">
      <title>Pagar | Autopartco</title>
      <PageIntro crumbs={CRUMBS} label="Pagar" title="Confirma tu pedido" description="Elige a dónde llevamos tu pedido y cómo pagas. Revisa el resumen antes de confirmar." />

      <div className="grid items-start gap-8 lg:grid-cols-[minmax(0,1fr)_24rem]">
        <div className="flex flex-col gap-8">
          {problem ? (
            <div
              ref={problemRef}
              tabIndex={-1}
              role={problem.kind === 'unavailable' ? undefined : 'alert'}
              className={cn('rounded-xl outline-none', problem.kind !== 'unavailable' && 'border border-ap-danger/30 bg-ap-danger-soft p-4 sm:p-5')}
            >
              <ProblemView problem={problem} onAdjust={adjustQuantities} onRemove={removeUnavailable} onRetry={pay} onTryAnotherMethod={() => {
                setProblem(undefined)
                focusMethodPicker()
              }} />
            </div>
          ) : null}

          <ol className="flex flex-col gap-8">
            <Step number="01" title="Dirección de entrega" state={addressState}>
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
            <Step number="02" title="Pago" state={paymentState}>
              <PaymentMethodPicker
                status={options.status}
                options={options.data}
                method={method}
                outcome={outcome}
                disabled={busyNow}
                onMethodChange={setMethod}
                onOutcomeChange={setOutcome}
                onRetry={options.reload}
              />
            </Step>
          </ol>
        </div>

        <div className="lg:sticky lg:top-[calc(var(--header-h)+1rem)]">
          <CheckoutSummary lines={lines} issues={issues} total={total} canPay={canPay} busy={busyNow} onPay={pay} blockedReason={blockedReason} />
        </div>
      </div>

      {/* Barra de pago fija en móvil: el botón del resumen queda lejos al final de la página. */}
      <div className="fixed inset-x-0 bottom-0 z-30 border-t border-ap-line-strong bg-ap-white px-4 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] lg:hidden">
        <div className="mx-auto flex max-w-lg flex-col gap-1.5">
          <PayButton total={total} canPay={canPay} busy={busyNow} onPay={pay} />
          {blockedReason ? (
            <p className="text-center text-xs text-ap-muted">{blockedReason}</p>
          ) : (
            <p className="text-center text-xs text-ap-muted">
              Total informativo <span className="tabular-nums">{formatCOP(total)}</span> · lo confirma Autopartco al pagar
            </p>
          )}
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
          <Button type="button" onClick={onAdjust}>
            Ajustar cantidades
          </Button>
        </ProblemText>
      )
    case 'gone':
      return (
        <ProblemText title="Algunos repuestos ya no están disponibles" body="No se cobró nada. Quítalos del carrito para continuar con el resto.">
          <Button type="button" onClick={onRemove}>
            Quitar del carrito
          </Button>
        </ProblemText>
      )
    case 'changed':
      return <ProblemText title="Un precio cambió mientras pagabas" body="Revisa el resumen e inténtalo de nuevo. No se cobró nada." />
    case 'duplicate':
      return (
        <ProblemText title="Este pedido ya se está procesando" body="Revisa tus pedidos antes de volver a pagar. Si no aparece, recarga la página e inténtalo de nuevo.">
          <Button variant="outline" nativeButton={false} render={<Link to="/mis-pedidos" />}>
            Ver mis pedidos
          </Button>
        </ProblemText>
      )
    case 'method':
      return <ProblemText title="Ese método de pago ya no está disponible" body="Actualizamos las opciones de pago. Elige una y vuelve a pagar; no se cobró nada." />
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
      <p className="flex items-start gap-2 font-semibold text-ap-danger">
        <TriangleAlertIcon className="mt-0.5 size-5 shrink-0" aria-hidden />
        {title}
      </p>
      <p className="max-w-prose text-sm text-ap-ink">{body}</p>
      {children ? <div className="flex flex-wrap gap-2">{children}</div> : null}
    </div>
  )
}
