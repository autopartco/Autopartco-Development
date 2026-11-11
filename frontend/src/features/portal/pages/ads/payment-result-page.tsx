import { useEffect, useState, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ReceiptTextIcon, RotateCwIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { formatCOP } from '@/lib/format'
import { invalidateAsync } from '@/lib/use-async'
import { safeTransactionId } from '@/features/checkout/lib/order-payment'
import { adsApi, type AdCampaign } from '../../api/ads-api'
import { PageHeader } from '../../components/page-header'
import { PaymentStatusIndicator, type PaymentIndicatorState } from '../../components/payment-status-indicator'
import { QueryError } from '../../components/query-boundary'
import { PortalHeader } from '../../layout/portal-header'
import { clearPendingAdCheckout, formatDay, loadPendingAdCheckout, placementLabel, type PendingAdCheckout } from '../../lib/ads'

/** Cada 3 s al principio y luego más espaciado, durante 60 s como máximo (igual que la suscripción). */
const POLL_TIMEOUT_MS = 60_000
const pollDelay = (attempt: number) => (attempt < 5 ? 3_000 : Math.min(3_000 * 1.5 ** (attempt - 4), 8_000))
const MAX_FAILURES = 3

type Phase =
  | { kind: 'checking' }
  | { kind: 'approved'; campaign: AdCampaign }
  | { kind: 'not-completed'; campaign: AdCampaign }
  | { kind: 'closed'; campaign: AdCampaign }
  | { kind: 'timeout' }
  | { kind: 'error'; error: unknown }

/** Con pago aprobado la campaña sale de DRAFT/PENDING_PAYMENT y queda con `paidAt`. */
function classify(campaign: AdCampaign): Phase | undefined {
  if (campaign.paidAt && !['DRAFT', 'PENDING_PAYMENT'].includes(campaign.status)) return { kind: 'approved', campaign }
  if (campaign.status === 'DRAFT') return { kind: 'not-completed', campaign }
  if (campaign.status === 'REJECTED' || campaign.status === 'CANCELLED') return { kind: 'closed', campaign }
  return undefined
}

/**
 * Sincroniza una vez con el `id` de Wompi (`POST …/payment/sync`) y consulta la campaña al servidor hasta un
 * estado final o 60 s. El estado lo decide el servidor, nunca la redirección.
 */
function useAdPaymentStatus(checkout: PendingAdCheckout, transactionId: string | undefined, round: number): Phase {
  const [result, setResult] = useState<{ round: number; phase: Phase }>()

  useEffect(() => {
    let active = true
    let timer: number | undefined
    const deadline = Date.now() + POLL_TIMEOUT_MS
    let attempt = 0
    let failures = 0
    const settle = (phase: Phase) => {
      if (active) setResult({ round, phase })
    }
    const fatal = (error: unknown) => error instanceof ApiError && (error.status === 401 || error.status === 403 || error.status === 404)

    async function poll() {
      try {
        const campaign = await adsApi.get(checkout.storeId, checkout.campaignId)
        if (!active) return
        failures = 0
        const phase = classify(campaign)
        if (phase) {
          if (phase.kind === 'approved') clearPendingAdCheckout()
          invalidateAsync(`portal:${checkout.storeId}:ads:`)
          settle(phase)
          return
        }
      } catch (error) {
        if (!active) return
        failures += 1
        if (fatal(error) || failures >= MAX_FAILURES) {
          settle({ kind: 'error', error })
          return
        }
      }
      const delay = pollDelay(attempt++)
      if (Date.now() + delay > deadline) {
        settle({ kind: 'timeout' })
        return
      }
      timer = window.setTimeout(() => void poll(), delay)
    }

    async function start() {
      if (transactionId) {
        try {
          await adsApi.syncPayment(checkout.storeId, checkout.campaignId, transactionId)
        } catch (error) {
          if (!active) return
          // Cualquier fallo de la sincronización se resuelve con la consulta: el GET dice el estado real.
          if (fatal(error)) {
            settle({ kind: 'error', error })
            return
          }
        }
      }
      if (active) void poll()
    }

    void start()
    return () => {
      active = false
      window.clearTimeout(timer)
    }
  }, [checkout, transactionId, round])

  return result && result.round === round ? result.phase : { kind: 'checking' }
}

function Summary({ rows }: { rows: { label: string; value: ReactNode; strong?: boolean }[] }) {
  return (
    <dl className="grid w-full grid-cols-[auto_1fr] border-t border-ap-line text-left text-sm">
      {rows.map((row) => (
        <div key={row.label} className="contents">
          <dt className="border-b border-ap-line py-2.5 pr-4 text-ap-muted">{row.label}</dt>
          <dd className={row.strong ? 'ap-price border-b border-ap-line py-2.5 text-right text-lg text-ap-ink' : 'min-w-0 border-b border-ap-line py-2.5 text-right break-all text-ap-ink tabular-nums'}>{row.value}</dd>
        </div>
      ))}
    </dl>
  )
}

const BUTTON = 'h-11 w-full px-5 sm:w-auto'

function Actions({ children }: { children: ReactNode }) {
  return <div className="flex w-full flex-col-reverse gap-2 sm:flex-row">{children}</div>
}

function TransactionNote({ id }: { id: string }) {
  return (
    <p className="flex items-start gap-2 text-left text-xs text-ap-muted">
      <ReceiptTextIcon className="mt-px size-4 shrink-0" aria-hidden />
      <span>
        Transacción de Wompi: <span className="ap-ref break-all text-ap-ink">{id}</span>. Compártela con soporte si necesitas ayuda.
      </span>
    </p>
  )
}

function ResultCard({ indicator, title, description, busy, children }: { indicator?: ReactNode; title: string; description: ReactNode; busy?: boolean; children: ReactNode }) {
  return (
    <section aria-busy={busy || undefined} className="flex flex-col items-start gap-6 border-t-2 border-ap-graphite pt-6">
      {indicator}
      <PageHeader label="Pago de publicidad" title={title} description={description} className="mb-0 w-full sm:mb-0" />
      {children}
    </section>
  )
}

function PendingResult({ checkout, transactionId }: { checkout: PendingAdCheckout; transactionId?: string }) {
  const [round, setRound] = useState(0)
  const phase = useAdPaymentStatus(checkout, transactionId, round)
  const listPath = `/portal/${encodeURIComponent(checkout.storeId)}/publicidad`
  const retry = () => setRound((value) => value + 1)

  const estimate = (
    <Summary
      rows={[
        { label: 'Campaña', value: checkout.campaignName },
        { label: 'Referencia', value: <span className="ap-ref">{checkout.reference}</span> },
        ...(checkout.amountInCents > 0 ? [{ label: 'Total', value: formatCOP(checkout.amountInCents / 100), strong: true }] : []),
      ]}
    />
  )
  const toList = (
    <Button variant="outline" className={BUTTON} nativeButton={false} render={<Link to={listPath} />}>
      Ir a mis campañas
    </Button>
  )

  let indicator: { state: PaymentIndicatorState; label: string } | undefined
  let title: string
  let description: ReactNode
  let details: ReactNode = estimate
  let actions: ReactNode = null

  switch (phase.kind) {
    case 'checking':
      indicator = { state: 'pending', label: 'Verificando el pago con Wompi.' }
      title = 'Estamos confirmando tu pago'
      description = 'Wompi nos está enviando la confirmación. Suele tardar unos segundos; no cierres esta página.'
      break
    case 'approved': {
      const { campaign } = phase
      indicator = { state: 'approved', label: 'Pago aprobado.' }
      title = 'Tu pago fue aprobado'
      description = campaign.status === 'SCHEDULED' ? `Tu campaña quedó programada para el ${formatDay(campaign.startsOn)}.` : 'Tu campaña ya está activa y tus anuncios empiezan a mostrarse.'
      details = (
        <Summary
          rows={[
            { label: 'Campaña', value: campaign.name },
            { label: 'Ubicaciones', value: campaign.placements.map(placementLabel).join(', ') },
            { label: 'Vigencia', value: `${formatDay(campaign.startsOn)} al ${formatDay(campaign.endsOn)}` },
            { label: 'Referencia', value: <span className="ap-ref">{checkout.reference}</span> },
            { label: 'Total pagado', value: formatCOP(campaign.totalPrice), strong: true },
          ]}
        />
      )
      actions = (
        <Actions>
          <Button className={BUTTON} nativeButton={false} render={<Link to={listPath} />}>
            Ir a mis campañas
          </Button>
        </Actions>
      )
      break
    }
    case 'not-completed':
      indicator = { state: 'declined', label: 'El pago no se completó.' }
      title = 'El pago no se completó'
      description = 'No se hizo ningún cobro y tu campaña sigue como borrador. Puedes intentar pagar de nuevo, si quieres con otro medio de pago.'
      actions = (
        <Actions>
          {toList}
          <Button className={BUTTON} nativeButton={false} render={<Link to={`${listPath}/${encodeURIComponent(checkout.campaignId)}/editar`} />}>
            Revisar y pagar
          </Button>
        </Actions>
      )
      break
    case 'closed':
      indicator = { state: 'declined', label: 'La campaña no está disponible.' }
      title = phase.campaign.status === 'REJECTED' ? 'Autopartco rechazó la campaña' : 'La campaña fue cancelada'
      description =
        phase.campaign.status === 'REJECTED'
          ? `${phase.campaign.rejectedReason ? `Motivo: ${phase.campaign.rejectedReason}. ` : ''}${phase.campaign.requiresManualRefund ? 'Gestionaremos el reembolso manualmente.' : ''}`.trim() || 'Escríbenos con la referencia si tienes dudas.'
          : 'Esta campaña ya no se puede pagar ni activar.'
      actions = <Actions>{toList}</Actions>
      break
    case 'timeout':
      indicator = { state: 'waiting', label: 'La confirmación del pago está tardando.' }
      title = 'La confirmación está tardando'
      description = 'Wompi puede tardar unos minutos en confirmar el pago. Cuando llegue, tu campaña quedará activa; no hace falta pagar de nuevo.'
      actions = (
        <Actions>
          {toList}
          <Button className={BUTTON} onClick={retry}>
            <RotateCwIcon aria-hidden />
            Volver a verificar
          </Button>
        </Actions>
      )
      break
    case 'error':
      title = 'No pudimos consultar tu pago'
      description = 'Esto no significa que el pago haya fallado. Vuelve a intentarlo en un momento.'
      details = <QueryError error={phase.error} what="el estado del pago" onRetry={retry} className="w-full text-left" />
      actions = <Actions>{toList}</Actions>
      break
  }

  return (
    <ResultCard indicator={indicator ? <PaymentStatusIndicator state={indicator.state} label={indicator.label} /> : null} title={title} description={description} busy={phase.kind === 'checking'}>
      {details}
      {transactionId && phase.kind !== 'approved' ? <TransactionNote id={transactionId} /> : null}
      {actions}
    </ResultCard>
  )
}

/**
 * `/portal/publicidad/pago?id=…`: a donde Wompi devuelve tras pagar una campaña. Lee el cobro pendiente guardado antes
 * de redirigir (almacén y campaña) y consulta su estado al servidor.
 */
export default function AdsPaymentResultPage() {
  const [params] = useSearchParams()
  const [checkout] = useState(loadPendingAdCheckout)
  const transactionId = safeTransactionId(params.get('id'))

  return (
    <div className="min-h-svh bg-ap-white">
      <PortalHeader innerClassName="mx-auto w-full max-w-3xl px-4 sm:px-6 lg:px-6" />
      <main id="contenido" tabIndex={-1} className="mx-auto w-full max-w-lg px-4 py-8 outline-none sm:px-6 sm:py-12">
        <title>Resultado del pago de publicidad | Autopartco</title>
        {checkout ? (
          <PendingResult checkout={checkout} transactionId={transactionId} />
        ) : (
          <ResultCard
            title="No encontramos el pago en esta pestaña"
            description="Si acabas de pagar, revisa la sección Publicidad de tu almacén: la campaña se activa cuando el servidor confirma el pago, y puede tardar unos minutos."
          >
            {transactionId ? <TransactionNote id={transactionId} /> : null}
            <Actions>
              <Button className={BUTTON} nativeButton={false} render={<Link to="/portal" />}>
                Ir a mis almacenes
              </Button>
            </Actions>
          </ResultCard>
        )}
      </main>
    </div>
  )
}
