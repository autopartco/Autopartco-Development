import { useMemo, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { FormSection } from '@/components/common/form-section'
import { Button } from '@/components/ui/button'
import { formatCOP, pluralize } from '@/lib/format'
import { invalidateAsync, useAsync } from '@/lib/use-async'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { adsApi, type AdCampaign, type AdCampaignInput, type AdPlacement, type AdPlacementPrice } from '../../api/ads-api'
import { getProductService } from '../../api/product-service'
import { startAdCheckout } from '../../components/ads/start-ad-checkout'
import { TextAreaField } from '../../components/form-controls'
import { PageHeader } from '../../components/page-header'
import { QueryBoundary } from '../../components/query-boundary'
import { usePortal } from '../../context'
import {
  adErrorMessage,
  campaignStatusView,
  daysBetween,
  EMPTY_TARGETING,
  estimateTotal,
  formatDay,
  isStoreOnly,
  NO_REFUND_NOTICE,
  PLACEMENT_HINT,
  PLACEMENT_LABEL,
  PLACEMENT_ORDER,
  toDayString,
} from '../../lib/ads'
import { actionErrorMessage } from '../../lib/errors'
import { humanizeCode } from '../../lib/labels'

const MAX_PRODUCTS = 20
const MAX_DAYS = 90
const MAX_START_AHEAD_DAYS = 60

interface PickItem {
  id: string
  label: string
  detail?: string
}

/** Lista de casillas con nombre accesible (`fieldset` + `legend`) y desplazamiento propio si es larga. */
function CheckList({
  legend,
  hint,
  items,
  selected,
  onToggle,
  empty,
  scroll,
}: {
  legend: string
  hint?: string
  items: PickItem[]
  selected: string[]
  onToggle: (id: string) => void
  empty: string
  scroll?: boolean
}) {
  return (
    <fieldset className="flex min-w-0 flex-col gap-2">
      <legend className="text-sm font-semibold text-ap-ink">{legend}</legend>
      {hint ? <p className="text-xs text-ap-muted">{hint}</p> : null}
      {items.length === 0 ? (
        <p className="rounded-md border border-ap-line bg-ap-paper p-3 text-sm text-ap-muted">{empty}</p>
      ) : (
        <ul className={`flex flex-col divide-y divide-ap-line rounded-md border border-ap-line ${scroll ? 'max-h-72 overflow-y-auto' : ''}`}>
          {items.map((item) => {
            const checked = selected.includes(item.id)
            return (
              <li key={item.id}>
                <label className="flex min-h-11 cursor-pointer items-center gap-3 px-3 py-2 hover:bg-ap-paper">
                  <input type="checkbox" className="size-4 shrink-0 accent-ap-graphite" checked={checked} onChange={() => onToggle(item.id)} />
                  <span className="min-w-0 flex-1 text-sm text-ap-ink">{item.label}</span>
                  {item.detail ? <span className="ap-ref shrink-0 text-ap-muted">{item.detail}</span> : null}
                </label>
              </li>
            )
          })}
        </ul>
      )}
    </fieldset>
  )
}

const toggle = (list: string[], id: string) => (list.includes(id) ? list.filter((value) => value !== id) : [...list, id])

interface FormProps {
  storeId: string
  campaign?: AdCampaign
  prices: AdPlacementPrice[]
}

function CampaignForm({ storeId, campaign, prices }: FormProps) {
  const navigate = useNavigate()
  const listPath = `/portal/${encodeURIComponent(storeId)}/publicidad`
  const products = useAsync(`portal:${storeId}:ads:products`, async () => (await getProductService()).list(storeId))
  const options = useAsync('portal:ads:product-options', async () => (await getProductService()).options())
  const makes = useAsync('portal:vehicles:makes', async () => (await getProductService()).listMakes())

  const [today] = useState(() => new Date())
  const minStart = toDayString(today)
  const maxStart = toDayString(new Date(today.getFullYear(), today.getMonth(), today.getDate() + MAX_START_AHEAD_DAYS))

  const [name, setName] = useState(campaign?.name ?? '')
  const [productIds, setProductIds] = useState<string[]>(campaign?.productIds ?? [])
  const [placements, setPlacements] = useState<AdPlacement[]>(campaign?.placements ?? [])
  const [startsOn, setStartsOn] = useState(campaign?.startsOn ?? minStart)
  const [endsOn, setEndsOn] = useState(campaign?.endsOn ?? '')
  const [keywords, setKeywords] = useState((campaign?.targeting.keywords ?? []).join(', '))
  const [categoryIds, setCategoryIds] = useState(campaign?.targeting.categoryIds ?? [])
  const [makeIds, setMakeIds] = useState(campaign?.targeting.makeIds ?? [])
  const [productFilter, setProductFilter] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string>()
  const alertRef = useRef<HTMLDivElement>(null)

  // Un producto aparece una vez aunque esté en varias sucursales; solo se ofrecen los publicados (activos).
  const publishable = useMemo(() => {
    const unique = new Map<string, PickItem>()
    for (const row of products.data ?? []) {
      if (row.state === 'ACTIVE' && !unique.has(row.id)) unique.set(row.id, { id: row.id, label: row.name, detail: row.sku ?? row.partNumber ?? undefined })
    }
    return [...unique.values()].toSorted((a, b) => a.label.localeCompare(b.label, 'es-CO'))
  }, [products.data])
  const visibleProducts = useMemo(() => {
    const needle = productFilter.trim().toLocaleLowerCase('es-CO')
    return needle ? publishable.filter((item) => `${item.label} ${item.detail ?? ''}`.toLocaleLowerCase('es-CO').includes(needle)) : publishable
  }, [publishable, productFilter])

  const days = daysBetween(startsOn, endsOn)
  const total = estimateTotal(placements, prices, days)
  const activePrices = prices.filter((price) => price.active)
  const editable = !campaign || campaign.status === 'DRAFT'
  // Solo «Mapa del inicio»: se anuncia el almacén; productos y segmentación no aplican.
  const storeOnly = isStoreOnly(placements)

  function buildInput(): AdCampaignInput | undefined {
    const next: Record<string, string> = {}
    if (!name.trim()) next.name = 'Ponle un nombre a la campaña para reconocerla.'
    else if (name.trim().length > 80) next.name = 'Usa máximo 80 caracteres.'
    if (!storeOnly && productIds.length === 0) next.products = 'Elige al menos un producto publicado.'
    else if (!storeOnly && productIds.length > MAX_PRODUCTS) next.products = `Una campaña admite hasta ${MAX_PRODUCTS} productos.`
    if (placements.length === 0) next.placements = 'Elige al menos una ubicación.'
    if (!startsOn) next.startsOn = 'Indica cuándo empieza.'
    else if (startsOn < minStart || startsOn > maxStart) next.startsOn = 'Debe empezar desde hoy y hasta dentro de 60 días.'
    if (!endsOn) next.endsOn = 'Indica cuándo termina.'
    else if (days === 0) next.endsOn = 'La fecha final no puede ser anterior a la inicial.'
    else if (days > MAX_DAYS) next.endsOn = `Una campaña dura máximo ${MAX_DAYS} días.`
    setErrors(next)
    if (Object.keys(next).length > 0) {
      setFormError('Revisa los campos marcados antes de continuar.')
      requestAnimationFrame(() => alertRef.current?.focus())
      return undefined
    }
    return {
      name: name.trim(),
      productIds: storeOnly ? [] : productIds,
      placements,
      startsOn,
      endsOn,
      targeting: storeOnly
        ? EMPTY_TARGETING
        : {
        ...(campaign?.targeting ?? EMPTY_TARGETING),
        keywords: [...new Set(keywords.split(/[,\n]/).map((word) => word.trim()).filter(Boolean))],
        categoryIds,
        makeIds,
      },
    }
  }

  const save = useSubmitOnce(async (pay: boolean) => {
    const input = buildInput()
    if (!input) return
    setFormError(undefined)
    const saved = campaign ? await adsApi.update(storeId, campaign.id, input) : await adsApi.create(storeId, input)
    invalidateAsync(`portal:${storeId}:ads:`)
    if (pay) {
      try {
        await startAdCheckout(storeId, saved)
      } catch (error) {
        toast.error(adErrorMessage(error, actionErrorMessage(error, 'Guardamos el borrador, pero no pudimos abrir el pago. Págalo desde la lista.')))
        void navigate(listPath)
      }
      return
    }
    toast.success('Guardaste el borrador de la campaña.')
    void navigate(listPath)
  })

  async function submit(pay: boolean) {
    const result = await save.run(pay)
    if (result.status === 'error') {
      setFormError(adErrorMessage(result.error, actionErrorMessage(result.error, 'No pudimos guardar la campaña. Inténtalo de nuevo.')))
      requestAnimationFrame(() => alertRef.current?.focus())
    }
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    void submit(false)
  }

  const pending = save.pending
  const perPlacement = placements
    .map((placement) => ({ placement, price: prices.find((candidate) => candidate.placement === placement)?.dailyPrice ?? 0 }))
    .toSorted((a, b) => PLACEMENT_ORDER.indexOf(a.placement) - PLACEMENT_ORDER.indexOf(b.placement))

  return (
    <form noValidate onSubmit={onSubmit} aria-busy={pending || undefined} className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start">
      <div className="flex min-w-0 flex-col gap-8">
        {formError ? (
          <FormAlert ref={alertRef} title="No se guardó la campaña">
            {formError}
          </FormAlert>
        ) : null}
        {!editable && campaign ? (
          <FormAlert tone="info" title={`Esta campaña está en estado «${campaignStatusView(campaign).label}»`}>
            Solo se pueden editar los borradores. Vuelve a la lista para pausarla, cancelarla o ver sus estadísticas.
          </FormAlert>
        ) : null}

        <FormSection title="Campaña" description="El nombre es solo para ti; los compradores no lo ven." disabled={!editable || pending}>
          <TextField
            id="ad-name"
            label="Nombre"
            value={name}
            maxLength={80}
            autoComplete="off"
            error={errors.name}
            onChange={(event) => setName(event.target.value)}
          />
        </FormSection>

        <FormSection
          title="Productos"
          description={`Solo aparecen tus productos publicados. Elige de 1 a ${MAX_PRODUCTS}; llevan la etiqueta «Patrocinado».`}
          aside={<span className="ap-ref text-ap-muted">{productIds.length} de {MAX_PRODUCTS}</span>}
          disabled={!editable || pending}
        >
          <TextField
            id="ad-product-filter"
            label="Buscar producto"
            type="search"
            value={productFilter}
            autoComplete="off"
            onChange={(event) => setProductFilter(event.target.value)}
          />
          {storeOnly ? (
            <p className="rounded-md border border-ap-line bg-ap-paper p-3 text-sm text-ap-muted">
              Solo elegiste «Mapa del inicio»: la campaña anuncia tu almacén, no productos. Si sumas otra ubicación, elige aquí los productos.
            </p>
          ) : (
          <QueryBoundary result={products} what="tus productos" keepPrevious>
            {() => (
              <>
                <CheckList
                  legend="Productos publicados"
                  items={visibleProducts}
                  selected={productIds}
                  onToggle={(id) => setProductIds((current) => toggle(current, id))}
                  empty={publishable.length === 0 ? 'No tienes productos publicados. Publica uno desde Productos para anunciarlo.' : 'Ningún producto coincide con la búsqueda.'}
                  scroll
                />
                {errors.products ? <p className="text-sm font-medium text-ap-danger">{errors.products}</p> : null}
              </>
            )}
          </QueryBoundary>
          )}
        </FormSection>

        <FormSection title="Ubicaciones" description="Pagas la tarifa diaria de cada ubicación por los días que dure la campaña." disabled={!editable || pending}>
          {activePrices.length === 0 ? (
            <p className="rounded-md border border-ap-line bg-ap-paper p-3 text-sm text-ap-muted">No hay ubicaciones disponibles por ahora. Inténtalo más tarde.</p>
          ) : (
            <fieldset className="flex flex-col gap-2">
              <legend className="sr-only">Ubicaciones del anuncio</legend>
              <ul className="flex flex-col divide-y divide-ap-line rounded-md border border-ap-line">
                {PLACEMENT_ORDER.map((placement) => {
                  const price = activePrices.find((candidate) => candidate.placement === placement)
                  if (!price) return null
                  const hintId = `ad-placement-${placement}-hint`
                  return (
                    <li key={placement}>
                      <label className="flex min-h-11 cursor-pointer items-start gap-3 px-3 py-3 hover:bg-ap-paper">
                        <input
                          type="checkbox"
                          className="mt-0.5 size-4 shrink-0 accent-ap-graphite"
                          aria-describedby={hintId}
                          checked={placements.includes(placement)}
                          onChange={() => setPlacements((current) => (current.includes(placement) ? current.filter((value) => value !== placement) : [...current, placement]))}
                        />
                        <span className="min-w-0 flex-1">
                          <span className="block text-sm font-semibold text-ap-ink">{PLACEMENT_LABEL[placement]}</span>
                          <span id={hintId} className="block text-xs text-ap-muted">
                            {PLACEMENT_HINT[placement]}
                          </span>
                        </span>
                        <span className="ap-price shrink-0 text-sm text-ap-ink">
                          {formatCOP(price.dailyPrice)}
                          <span className="font-normal text-ap-muted"> / día</span>
                        </span>
                      </label>
                    </li>
                  )
                })}
              </ul>
              {errors.placements ? <p className="text-sm font-medium text-ap-danger">{errors.placements}</p> : null}
            </fieldset>
          )}
        </FormSection>

        <FormSection title="Fechas" description={`Dura de 1 a ${MAX_DAYS} días. Puede empezar hoy o hasta dentro de ${MAX_START_AHEAD_DAYS} días.`} disabled={!editable || pending}>
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField
              id="ad-starts"
              label="Empieza"
              type="date"
              min={minStart}
              max={maxStart}
              value={startsOn}
              error={errors.startsOn}
              onChange={(event) => setStartsOn(event.target.value)}
            />
            <TextField
              id="ad-ends"
              label="Termina"
              type="date"
              min={startsOn || minStart}
              value={endsOn}
              error={errors.endsOn}
              onChange={(event) => setEndsOn(event.target.value)}
            />
          </div>
        </FormSection>

        {storeOnly ? null : (
        <FormSection
          title="Segmentación"
          description="Opcional. Con segmentación, el anuncio aparece solo cuando la búsqueda, la categoría o el vehículo coinciden. Sin ella, aparece para cualquier comprador."
          disabled={!editable || pending}
        >
          <TextAreaField
            id="ad-keywords"
            label="Palabras clave"
            optional
            hint="Sepáralas con comas, por ejemplo: pastillas de freno, balatas."
            value={keywords}
            onChange={(event) => setKeywords(event.target.value)}
          />
          <QueryBoundary result={options} what="las categorías" keepPrevious>
            {(data) => (
              <CheckList
                legend="Categorías"
                items={data.categories.map((category) => ({ id: category.id, label: humanizeCode(category.code) })).toSorted((a, b) => a.label.localeCompare(b.label, 'es-CO'))}
                selected={categoryIds}
                onToggle={(id) => setCategoryIds((current) => toggle(current, id))}
                empty="No hay categorías disponibles."
                scroll
              />
            )}
          </QueryBoundary>
          <QueryBoundary result={makes} what="las marcas de vehículos" keepPrevious>
            {(data) => (
              <CheckList
                legend="Marcas de vehículo"
                items={data.map((make) => ({ id: make.id, label: make.name }))}
                selected={makeIds}
                onToggle={(id) => setMakeIds((current) => toggle(current, id))}
                empty="No hay marcas disponibles."
                scroll
              />
            )}
          </QueryBoundary>
        </FormSection>
        )}
      </div>

      <aside aria-labelledby="ad-summary-title" className="flex flex-col gap-4 border-t-2 border-ap-graphite pt-4 lg:sticky lg:top-20">
        <h2 id="ad-summary-title" className="text-block-title text-ap-ink">
          Resumen y total
        </h2>
        <dl className="flex flex-col gap-2 text-sm">
          <div className="flex justify-between gap-3">
            <dt className="text-ap-muted">Productos</dt>
            <dd className="ap-num text-ap-ink">{pluralize(productIds.length, 'producto', 'productos')}</dd>
          </div>
          <div className="flex justify-between gap-3">
            <dt className="text-ap-muted">Vigencia</dt>
            <dd className="text-right text-ap-ink">
              {days > 0 ? `${formatDay(startsOn)} al ${formatDay(endsOn)}` : '—'}
            </dd>
          </div>
          <div className="flex justify-between gap-3">
            <dt className="text-ap-muted">Días</dt>
            <dd className="ap-num text-ap-ink">{days > 0 ? days : '—'}</dd>
          </div>
        </dl>
        {perPlacement.length > 0 && days > 0 ? (
          <ul className="flex flex-col gap-1.5 border-t border-ap-line pt-3 text-sm">
            {perPlacement.map(({ placement, price }) => (
              <li key={placement} className="flex justify-between gap-3">
                <span className="text-ap-muted">
                  {PLACEMENT_LABEL[placement]} · {formatCOP(price)} × {days}
                </span>
                <span className="ap-num shrink-0 text-ap-ink">{formatCOP(price * days)}</span>
              </li>
            ))}
          </ul>
        ) : null}
        <div className="flex items-baseline justify-between gap-3 border-t border-ap-line pt-3">
          <span className="font-semibold text-ap-ink">Total a pagar</span>
          <span className="ap-price text-xl text-ap-ink" aria-live="polite">
            {days > 0 && placements.length > 0 ? formatCOP(total) : '—'}
          </span>
        </div>
        <p className="text-xs text-ap-muted">Cálculo de referencia: el total final lo confirma Autopartco al guardar y es el que cobra Wompi.</p>
        <p className="rounded-md border border-ap-warning/40 bg-ap-warning-soft p-3 text-xs text-ap-ink">{NO_REFUND_NOTICE}</p>
        {editable ? (
          <div className="flex flex-col gap-2">
            <Button type="button" className="h-11 w-full" disabled={pending} onClick={() => void submit(true)}>
              {pending ? 'Guardando…' : 'Guardar y pagar'}
            </Button>
            <Button type="submit" variant="outline" className="h-11 w-full" disabled={pending}>
              Guardar borrador
            </Button>
          </div>
        ) : null}
        <Button variant="ghost" className="h-11 w-full" nativeButton={false} render={<Link to={listPath} />}>
          Volver a campañas
        </Button>
      </aside>
    </form>
  )
}

/** `/portal/:storeId/publicidad/nueva` y `/:campaignId/editar`: crea o edita un borrador y lo paga por Wompi. */
export default function PortalAdsCampaignFormPage() {
  const { storeId } = usePortal()
  const { campaignId } = useParams()
  const prices = useAsync(`portal:${storeId}:ads:prices`, () => adsApi.prices(storeId))
  // `null` para una campaña nueva: `QueryBoundary` trata `undefined` como «cargando» y nunca mostraría el formulario.
  const campaign = useAsync(`portal:${storeId}:ads:campaign:${campaignId ?? 'new'}`, async () => (campaignId ? adsApi.get(storeId, campaignId) : null))
  const title = campaignId ? 'Editar campaña' : 'Nueva campaña'

  return (
    <>
      <title>{`${title} | Autopartco`}</title>
      <PageHeader
        label="Publicidad"
        title={title}
        description="Elige qué anunciar, dónde y por cuántos días. Verás el total antes de pagar."
        crumbs={[
          { label: 'Publicidad', to: `/portal/${encodeURIComponent(storeId)}/publicidad` },
          { label: title },
        ]}
      />
      <QueryBoundary result={prices} what="las tarifas de publicidad">
        {(priceList) => (
          <QueryBoundary result={campaign} what="la campaña">
            {(data) => <CampaignForm key={data?.id ?? 'new'} storeId={storeId} campaign={data ?? undefined} prices={priceList} />}
          </QueryBoundary>
        )}
      </QueryBoundary>
    </>
  )
}
