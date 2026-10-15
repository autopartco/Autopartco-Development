import { useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { EyeIcon, EyeOffIcon, PackageSearchIcon, Trash2Icon } from 'lucide-react'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { useAsync } from '@/lib/use-async'
import { getBranchService } from '../api/branch-service'
import { getProductService } from '../api/product-service'
import type { ProductCreateInput, ProductOptions, ProductRow, ProductUpdateInput } from '../api/types'
import { CompatibilityPicker } from '../components/compatibility-picker'
import { ConfirmDialog } from '../components/confirm-dialog'
import { SelectField, TextAreaField } from '../components/form-controls'
import { ListSkeleton } from '../components/list-skeleton'
import { PageHeader } from '../components/page-header'
import { ProductImages } from '../components/product-images'
import { QueryBoundary, QueryError } from '../components/query-boundary'
import { StatusBadge } from '../components/status-badge'
import { usePortal } from '../context'
import { focusMain, usePublishProduct, useStoreQuery } from '../hooks'
import { actionErrorMessage } from '../lib/errors'
import { focusById, parseAmount, parseCount, type FieldErrors } from '../lib/forms'
import { humanizeCode } from '../lib/labels'

const FIELD_ORDER = ['name', 'sku', 'partNumber', 'description', 'manufacturerId', 'categoryId', 'branchId', 'price', 'quantity', 'compatibilityIds'] as const
type Field = (typeof FIELD_ORDER)[number]
const fieldId = (field: string) => `producto-${field}`

interface Values {
  name: string
  sku: string
  partNumber: string
  description: string
  manufacturerId: string
  categoryId: string
  branchId: string
  price: string
  quantity: string
  compatibilityIds: string[]
}

function valuesFrom(row: ProductRow | undefined, branchId: string): Values {
  return {
    name: row?.name ?? '',
    sku: row?.sku ?? '',
    partNumber: row?.partNumber ?? '',
    description: row?.description ?? '',
    manufacturerId: row?.manufacturerId ?? '',
    categoryId: row?.categoryId ?? '',
    branchId: row?.branchId ?? branchId,
    price: row ? String(row.price) : '',
    quantity: row ? String(row.quantity) : '',
    compatibilityIds: row?.compatibilityIds ?? [],
  }
}

/** Mismas reglas que `ProductCreateRequest`; el servidor las vuelve a comprobar. */
function validate(values: Values, mode: 'create' | 'edit'): FieldErrors {
  const errors: FieldErrors = {}
  if (!values.name.trim()) errors.name = 'Escribe el nombre del producto.'
  else if (values.name.trim().length > 200) errors.name = 'El nombre no puede superar 200 caracteres.'
  if (values.sku.trim().length > 100) errors.sku = 'El SKU no puede superar 100 caracteres.'
  if (values.partNumber.trim().length > 50) errors.partNumber = 'La referencia no puede superar 50 caracteres.'
  if (!values.description.trim()) errors.description = 'Describe el producto para que los compradores lo entiendan.'
  if (!values.manufacturerId) errors.manufacturerId = 'Elige el fabricante.'
  if (!values.categoryId) errors.categoryId = 'Elige la categoría.'
  if (mode === 'create' && !values.branchId) errors.branchId = 'Elige la sucursal donde está el producto.'
  const price = parseAmount(values.price)
  if (!values.price.trim()) errors.price = 'Escribe el precio en pesos.'
  else if (price === undefined) errors.price = 'Escribe solo números, por ejemplo 85000.'
  else if (price < 0.01) errors.price = 'El precio debe ser mayor que 0.'
  else if (price >= 1e10) errors.price = 'El precio es demasiado alto.'
  const quantity = parseCount(values.quantity)
  if (!values.quantity.trim()) errors.quantity = 'Escribe cuántas unidades tienes (0 si no hay).'
  else if (quantity === undefined) errors.quantity = 'Escribe un número entero, sin decimales.'
  if (values.compatibilityIds.length === 0) errors.compatibilityIds = 'Elige al menos un vehículo compatible.'
  return errors
}

function ProductForm({
  mode,
  rows,
  options,
  branches,
}: {
  mode: 'create' | 'edit'
  rows: ProductRow[]
  options: ProductOptions
  branches: { value: string; label: string }[]
}) {
  const { storeId, promptUpgrade, invalidateStore } = usePortal()
  const navigate = useNavigate()
  const base = `/portal/${encodeURIComponent(storeId)}`
  const first = rows[0]
  const [values, setValues] = useState<Values>(() => valuesFrom(first, branches[0]?.value ?? ''))
  const [touched, setTouched] = useState<Partial<Record<Field, boolean>>>({})
  const [submitted, setSubmitted] = useState(false)
  const [serverErrors, setServerErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string>()
  const [pending, setPending] = useState(false)
  const alertRef = useRef<HTMLDivElement>(null)

  const clientErrors = validate(values, mode)
  const visible = (field: Field): string | undefined =>
    serverErrors[field] ?? (submitted || touched[field] ? clientErrors[field] : undefined)

  function update<K extends keyof Values>(field: K, value: Values[K]) {
    setValues((current) => ({ ...current, [field]: value }))
    if (serverErrors[field]) setServerErrors(({ [field]: _removed, ...rest }) => rest)
  }
  const blur = (field: Field) => () => setTouched((current) => ({ ...current, [field]: true }))

  function changeBranch(branchId: string) {
    const row = rows.find((candidate) => candidate.branchId === branchId)
    setValues((current) => ({ ...current, branchId, price: row ? String(row.price) : current.price, quantity: row ? String(row.quantity) : current.quantity }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) return
    setSubmitted(true)
    setFormError(undefined)
    const errors = validate(values, mode)
    if (Object.keys(errors).length > 0) {
      const target = FIELD_ORDER.find((field) => errors[field])
      focusById(target ? fieldId(target) : undefined)
      return
    }
    const price = parseAmount(values.price) as number
    const quantity = parseCount(values.quantity) as number
    setPending(true)
    try {
      const service = await getProductService()
      if (mode === 'create') {
        const input: ProductCreateInput = {
          branchId: values.branchId,
          name: values.name.trim(),
          sku: values.sku.trim() || undefined,
          partNumber: values.partNumber.trim() || undefined,
          description: values.description.trim(),
          price,
          quantity,
          manufacturerId: values.manufacturerId,
          categoryId: values.categoryId,
          compatibilityIds: values.compatibilityIds,
        }
        const created = await service.create(storeId, input)
        invalidateStore()
        toast.success('Producto creado. Aún no está publicado: súbele imágenes y publícalo cuando esté listo.')
        navigate(`${base}/productos/${encodeURIComponent(created.id)}`, { replace: true })
      } else {
        const row = rows.find((candidate) => candidate.branchId === values.branchId) ?? first
        if (!row) return
        const input: ProductUpdateInput = {}
        if (values.name.trim() !== row.name) input.name = values.name.trim()
        if (values.sku.trim() !== (row.sku ?? '')) input.sku = values.sku.trim()
        if (values.partNumber.trim() !== (row.partNumber ?? '')) input.partNumber = values.partNumber.trim()
        if (values.description.trim() !== row.description) input.description = values.description.trim()
        if (values.manufacturerId !== row.manufacturerId) input.manufacturerId = values.manufacturerId
        if (values.categoryId !== row.categoryId) input.categoryId = values.categoryId
        if (price !== row.price) input.price = price
        if (quantity !== row.quantity) input.quantity = quantity
        const sameCompat =
          values.compatibilityIds.length === row.compatibilityIds.length && values.compatibilityIds.every((id) => row.compatibilityIds.includes(id))
        if (!sameCompat) input.compatibilityIds = values.compatibilityIds
        if (Object.keys(input).length === 0) {
          toast.info('No hay cambios para guardar.')
          return
        }
        await service.update(storeId, row.branchId, row.id, input)
        invalidateStore()
        toast.success('Cambios guardados.')
      }
    } catch (error) {
      if (promptUpgrade(error)) return
      if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
        setServerErrors(error.fieldErrors)
        requestAnimationFrame(() => {
          const target = FIELD_ORDER.find((field) => error.fieldErrors[field])
          if (target) focusById(fieldId(target))
          else alertRef.current?.focus()
        })
      }
      setFormError(actionErrorMessage(error, 'No pudimos guardar el producto. Inténtalo de nuevo.'))
      requestAnimationFrame(() => alertRef.current?.focus())
    } finally {
      setPending(false)
    }
  }

  const manufacturerOptions = options.manufacturers.map((item) => ({ value: item.id, label: item.name }))
  const categoryOptions = options.categories.map((item) => ({ value: item.id, label: humanizeCode(item.code) }))

  return (
    <form noValidate onSubmit={(event) => void handleSubmit(event)} className="flex max-w-3xl flex-col gap-8" aria-busy={pending || undefined}>
      {formError ? <FormAlert ref={alertRef} title="No se guardó el producto">{formError}</FormAlert> : null}

      <fieldset className="flex flex-col gap-4" disabled={pending}>
        <legend className="mb-2 text-xl font-extrabold tracking-tight">Datos del producto</legend>
        <TextField
          id={fieldId('name')}
          label="Nombre"
          value={values.name}
          onChange={(event) => update('name', event.target.value)}
          onBlur={blur('name')}
          error={visible('name')}
          maxLength={200}
          autoComplete="off"
          required
          hint="Como lo buscaría un comprador. Ej.: Pastillas de freno delanteras."
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField id={fieldId('sku')} label="SKU" optional value={values.sku} onChange={(event) => update('sku', event.target.value)} onBlur={blur('sku')} error={visible('sku')} maxLength={100} autoComplete="off" hint="Tu código interno." />
          <TextField id={fieldId('partNumber')} label="Referencia del fabricante" optional value={values.partNumber} onChange={(event) => update('partNumber', event.target.value)} onBlur={blur('partNumber')} error={visible('partNumber')} maxLength={50} autoComplete="off" />
        </div>
        <TextAreaField
          id={fieldId('description')}
          label="Descripción"
          value={values.description}
          onChange={(event) => update('description', event.target.value)}
          onBlur={blur('description')}
          error={visible('description')}
          required
          hint="Material, medidas, posición, garantía."
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField id={fieldId('manufacturerId')} label="Fabricante" placeholder="Elige un fabricante" options={manufacturerOptions} value={values.manufacturerId} onChange={(event) => update('manufacturerId', event.target.value)} onBlur={blur('manufacturerId')} error={visible('manufacturerId')} required />
          <SelectField id={fieldId('categoryId')} label="Categoría" placeholder="Elige una categoría" options={categoryOptions} value={values.categoryId} onChange={(event) => update('categoryId', event.target.value)} onBlur={blur('categoryId')} error={visible('categoryId')} required />
        </div>
      </fieldset>

      <fieldset className="flex flex-col gap-4" disabled={pending}>
        <legend className="mb-2 text-xl font-extrabold tracking-tight">Precio y existencias</legend>
        {branches.length > 1 ? (
          <SelectField
            id={fieldId('branchId')}
            label="Sucursal"
            placeholder={mode === 'create' ? 'Elige una sucursal' : undefined}
            options={branches}
            value={values.branchId}
            onChange={(event) => (mode === 'edit' ? changeBranch(event.target.value) : update('branchId', event.target.value))}
            onBlur={blur('branchId')}
            error={visible('branchId')}
            hint={mode === 'edit' ? 'El precio y las existencias son propios de cada sucursal.' : undefined}
            required
          />
        ) : (
          <p className="text-sm text-muted-foreground">
            Sucursal: <strong className="font-semibold text-foreground">{branches[0]?.label ?? 'sin sucursal activa'}</strong>
          </p>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField id={fieldId('price')} label="Precio (COP)" inputMode="decimal" value={values.price} onChange={(event) => update('price', event.target.value)} onBlur={blur('price')} error={visible('price')} autoComplete="off" required hint="En pesos colombianos, ej. 85000." />
          <TextField id={fieldId('quantity')} label="Existencias" inputMode="numeric" value={values.quantity} onChange={(event) => update('quantity', event.target.value)} onBlur={blur('quantity')} error={visible('quantity')} autoComplete="off" required hint="Unidades disponibles hoy." />
        </div>
      </fieldset>

      <CompatibilityPicker
        id={fieldId('compatibilityIds')}
        options={options.compatibilities}
        selected={values.compatibilityIds}
        onChange={(ids) => update('compatibilityIds', ids)}
        onBlur={blur('compatibilityIds')}
        error={visible('compatibilityIds')}
        disabled={pending}
      />

      <div className="flex flex-wrap gap-2">
        <Button type="submit" className="h-11 px-6" disabled={pending}>
          {pending ? 'Guardando…' : mode === 'create' ? 'Crear producto' : 'Guardar cambios'}
        </Button>
        <Button type="button" variant="outline" className="h-11 px-6" nativeButton={false} render={<Link to={`${base}/productos`} />}>
          Cancelar
        </Button>
      </div>
    </form>
  )
}

function EditHeaderActions({ product, rows }: { product: ProductRow; rows: ProductRow[] }) {
  const { storeId, can, invalidateStore } = usePortal()
  const navigate = useNavigate()
  const { setPublished, busyId } = usePublishProduct()
  const [confirmDelete, setConfirmDelete] = useState(false)
  const published = product.state === 'ACTIVE'
  return (
    <>
      {can('PRODUCT_PUBLISH') ? (
        <Button variant={published ? 'outline' : 'default'} className="h-11 px-4" disabled={busyId === product.id} onClick={() => void setPublished(product, !published)}>
          {published ? <EyeOffIcon aria-hidden /> : <EyeIcon aria-hidden />}
          {published ? 'Retirar del marketplace' : 'Publicar'}
        </Button>
      ) : null}
      {can('PRODUCT_DELETE') ? (
        <Button variant="destructive" className="h-11 px-4" onClick={() => setConfirmDelete(true)}>
          <Trash2Icon aria-hidden />
          Eliminar
        </Button>
      ) : null}
      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title={`¿Eliminar “${product.name}”?`}
        description={`Dejará de verse en el marketplace y se borrarán sus imágenes y sus existencias${rows.length > 1 ? ' en todas las sucursales' : ''}. Esta acción no se puede deshacer.`}
        confirmLabel="Eliminar producto"
        destructive
        onConfirm={async () => {
          await (await getProductService()).remove(storeId, product.id)
          invalidateStore()
          toast.success(`Eliminaste “${product.name}”.`)
          navigate(`/portal/${encodeURIComponent(storeId)}/productos`, { replace: true })
          focusMain()
        }}
      />
    </>
  )
}

/** `/portal/:storeId/productos/nuevo` y `/productos/:productId`: alta y edición con imágenes. */
export default function PortalProductFormPage() {
  const { productId } = useParams()
  const mode = productId ? 'edit' : 'create'
  const { storeId } = usePortal()
  const base = `/portal/${encodeURIComponent(storeId)}`

  const products = useStoreQuery('products', async (id) => (await getProductService()).list(id))
  const options = useAsync('portal:product-options', async () => (await getProductService()).options())
  const branchQuery = useStoreQuery('branches', async (id) => (await getBranchService()).list(id))

  const crumbs = [
    { label: 'Productos', to: `${base}/productos` },
    { label: mode === 'create' ? 'Nuevo producto' : 'Editar producto' },
  ]

  if (products.status === 'error') return <QueryError error={products.error} what="tus productos" onRetry={products.retry} />
  if (options.status === 'error') return <QueryError error={options.error} what="las opciones del catálogo" onRetry={options.retry} />
  if (branchQuery.status === 'error') return <QueryError error={branchQuery.error} what="tus sucursales" onRetry={branchQuery.retry} />
  if (!products.data || !options.data || !branchQuery.data) {
    return <ListSkeleton rows={6} label="Cargando el formulario" />
  }

  const activeBranches = branchQuery.data.filter((branch) => branch.state === 'ACTIVE').map((branch) => ({ value: branch.id, label: branch.name }))
  const rows = productId ? products.data.filter((row) => row.id === productId) : []
  const product = rows[0]

  if (mode === 'edit' && !product) {
    return (
      <EmptyState icon={<PackageSearchIcon />} title="No encontramos ese producto" description="Puede que se haya eliminado o que no sea de este almacén.">
        <Button variant="outline" className="h-11 px-4" nativeButton={false} render={<Link to={`${base}/productos`} />}>
          Volver a productos
        </Button>
      </EmptyState>
    )
  }
  if (mode === 'create' && activeBranches.length === 0) {
    return (
      <EmptyState
        icon={<PackageSearchIcon />}
        title="Primero necesitas una sucursal activa"
        description="Cada producto se guarda en una sucursal con su precio y existencias. Crea o reactiva una sucursal y vuelve."
      >
        <Button className="h-11 px-4" nativeButton={false} render={<Link to={`${base}/sucursales`} />}>
          Ir a sucursales
        </Button>
      </EmptyState>
    )
  }

  const branchOptions = mode === 'edit' ? rows.map((row) => ({ value: row.branchId, label: row.branchName })) : activeBranches

  return (
    <>
      <title>{`${mode === 'create' ? 'Nuevo producto' : (product?.name ?? 'Producto')} | Autopartco`}</title>
      <PageHeader
        crumbs={crumbs}
        title={mode === 'create' ? 'Nuevo producto' : (product?.name ?? 'Producto')}
        description={mode === 'create' ? 'Se crea sin publicar. Cuando esté listo, publícalo para que los compradores lo vean.' : undefined}
        meta={product ? (product.state === 'ACTIVE' ? <StatusBadge tone="success">Publicado</StatusBadge> : <StatusBadge>Sin publicar</StatusBadge>) : undefined}
        actions={product ? <EditHeaderActions product={product} rows={rows} /> : undefined}
      />
      {/* key: al cambiar de producto o de modo el formulario se reinicia con los datos nuevos. */}
      <div className="flex flex-col gap-10">
        <ProductForm key={`${mode}:${productId ?? ''}`} mode={mode} rows={rows} options={options.data} branches={branchOptions} />
        {product ? (
          <div className="max-w-3xl">
            <QueryBoundary result={products} what="las imágenes">
              {(all) => <ProductImages productId={product.id} productName={product.name} images={all.find((row) => row.id === product.id)?.images ?? []} />}
            </QueryBoundary>
          </div>
        ) : null}
      </div>
    </>
  )
}
