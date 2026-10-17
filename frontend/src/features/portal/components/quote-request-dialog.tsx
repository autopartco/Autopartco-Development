import { useRef, useState, type FormEvent } from 'react'
import { CheckIcon, CopyIcon, MailIcon } from 'lucide-react'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogClose, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useSession } from '@/features/auth/hooks'
import {
  buildQuoteMailto,
  buildQuoteText,
  ERP_OPTIONS,
  MESSAGE_MAX,
  NEED_OPTIONS,
  validateQuote,
  type ErpValue,
  type NeedValue,
  type QuoteContext,
  type QuoteErrors,
  type QuoteRequestValues,
} from '../lib/quote-request'
import { SelectField, TextAreaField } from './form-controls'

/** Correo de ventas público (`VITE_SALES_EMAIL`); sin él no se inventa ningún destino. */
const SALES_EMAIL = (import.meta.env.VITE_SALES_EMAIL as string | undefined)?.trim()

type Outcome = { kind: 'mailto'; href: string; truncated: boolean } | { kind: 'copy'; text: string }

const FIELD_ORDER: (keyof QuoteErrors)[] = ['contactName', 'email', 'phone', 'branches', 'products', 'users', 'message']
const FIELD_IDS: Record<keyof QuoteErrors, string> = {
  contactName: 'cotizacion-nombre',
  email: 'cotizacion-correo',
  phone: 'cotizacion-telefono',
  branches: 'cotizacion-sedes',
  products: 'cotizacion-productos',
  users: 'cotizacion-usuarios',
  message: 'cotizacion-mensaje',
}

function QuoteForm({ context, onClose }: { context: QuoteContext; onClose: () => void }) {
  const session = useSession()
  const [values, setValues] = useState<QuoteRequestValues>(() => ({
    contactName: session?.user.name ?? '',
    email: session?.user.email ?? '',
    phone: '',
    branches: '',
    products: '',
    users: '',
    erp: 'NONE',
    needs: [],
    message: '',
  }))
  const [errors, setErrors] = useState<QuoteErrors>({})
  const [outcome, setOutcome] = useState<Outcome>()
  const [copied, setCopied] = useState<'ok' | 'manual'>()
  const confirmRef = useRef<HTMLDivElement>(null)
  const textRef = useRef<HTMLTextAreaElement>(null)

  function set<K extends keyof QuoteRequestValues>(key: K, value: QuoteRequestValues[K]) {
    setValues((current) => ({ ...current, [key]: value }))
  }

  function toggleNeed(need: NeedValue, checked: boolean) {
    set('needs', checked ? [...values.needs, need] : values.needs.filter((item) => item !== need))
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateQuote(values)
    setErrors(found)
    const first = FIELD_ORDER.find((key) => found[key])
    if (first) {
      document.getElementById(FIELD_IDS[first])?.focus()
      return
    }
    // TODO(backend): enviar a `POST /api/v1/stores/{id}/quote-requests` cuando exista; hoy no se guarda nada.
    if (SALES_EMAIL) {
      const { href, truncated } = buildQuoteMailto(SALES_EMAIL, values, context)
      setOutcome({ kind: 'mailto', href, truncated })
      window.location.href = href
    } else {
      setOutcome({ kind: 'copy', text: buildQuoteText(values, context) })
    }
    requestAnimationFrame(() => confirmRef.current?.focus())
  }

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text)
      setCopied('ok')
    } catch {
      textRef.current?.select()
      setCopied('manual')
    }
  }

  if (outcome) {
    return (
      <div className="flex flex-col gap-4">
        <DialogHeader>
          <DialogTitle>{outcome.kind === 'mailto' ? 'Tu solicitud está lista' : 'Copia tu solicitud'}</DialogTitle>
          <DialogDescription>Revisa el resumen antes de enviarlo.</DialogDescription>
        </DialogHeader>
        <FormAlert ref={confirmRef} tone="info" title={outcome.kind === 'mailto' ? 'Abrimos tu correo con la solicitud lista.' : 'Solicitud generada.'}>
          {outcome.kind === 'mailto' ? (
            <>
              Solo falta que pulses «Enviar» en tu programa de correo. Si no se abrió, usa el botón de abajo. Todavía no guardamos la solicitud en Autopartco.
              {outcome.truncated ? ' Recortamos tu mensaje para que cupiera; menciónalo completo al responder.' : ''}
            </>
          ) : (
            <>Aún no hay un correo de ventas configurado; envíalo a soporte de Autopartco. Todavía no guardamos la solicitud en Autopartco.</>
          )}
        </FormAlert>
        {outcome.kind === 'copy' ? (
          <div className="flex flex-col gap-2">
            <label htmlFor="cotizacion-resumen" className="text-sm font-semibold">
              Resumen de la solicitud
            </label>
            <textarea
              ref={textRef}
              id="cotizacion-resumen"
              readOnly
              rows={12}
              value={outcome.text}
              className="min-h-40 w-full rounded-lg border border-input bg-card p-3 font-mono text-xs leading-normal"
            />
            <Button type="button" variant="outline" className="h-11 w-fit px-4" onClick={() => void copy(outcome.text)}>
              {copied === 'ok' ? <CheckIcon aria-hidden /> : <CopyIcon aria-hidden />}
              Copiar solicitud
            </Button>
            <p role="status" className="min-h-5 text-sm font-medium">
              {copied === 'ok' ? 'Solicitud copiada. Pégala en un correo a soporte de Autopartco.' : copied === 'manual' ? 'No pudimos copiar automáticamente: el texto quedó seleccionado, cópialo con Ctrl+C.' : ''}
            </p>
          </div>
        ) : null}
        <DialogFooter>
          <DialogClose render={<Button type="button" variant="outline" className="h-11 px-4" />}>Cerrar</DialogClose>
          {outcome.kind === 'mailto' ? (
            <Button className="h-11 px-4" nativeButton={false} render={<a href={outcome.href} />}>
              <MailIcon aria-hidden />
              Abrir mi correo
            </Button>
          ) : null}
        </DialogFooter>
      </div>
    )
  }

  const erpOptions = ERP_OPTIONS.map((option) => ({ value: option.value, label: option.label }))
  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
      <DialogHeader>
        <DialogTitle>Solicitar cotización · Empresarial</DialogTitle>
        <DialogDescription>Cuéntanos el tamaño de tu operación y te enviamos una cotización. Los campos con asterisco son obligatorios.</DialogDescription>
      </DialogHeader>

      <div className="grid gap-4 sm:grid-cols-2">
        <TextField id={FIELD_IDS.contactName} label="Nombre de contacto *" autoComplete="name" value={values.contactName} error={errors.contactName} onChange={(e) => set('contactName', e.target.value)} />
        <TextField id={FIELD_IDS.email} label="Correo *" type="email" autoComplete="email" value={values.email} error={errors.email} onChange={(e) => set('email', e.target.value)} />
        <TextField id={FIELD_IDS.phone} label="Teléfono" optional type="tel" autoComplete="tel" inputMode="tel" value={values.phone} error={errors.phone} hint="Ej. 300 123 4567 o +57 300 123 4567" onChange={(e) => set('phone', e.target.value)} />
        <SelectField id="cotizacion-erp" label="ERP que usas hoy" options={erpOptions} value={values.erp} onChange={(e) => set('erp', e.target.value as ErpValue)} />
      </div>

      <fieldset className="flex flex-col gap-3 rounded-lg border border-foreground/10 p-3">
        <legend className="px-1 text-sm font-semibold">Tamaño aproximado *</legend>
        <div className="grid gap-4 sm:grid-cols-3">
          <TextField id={FIELD_IDS.branches} label="Sedes" inputMode="numeric" value={values.branches} error={errors.branches} onChange={(e) => set('branches', e.target.value)} />
          <TextField id={FIELD_IDS.products} label="Productos" inputMode="numeric" value={values.products} error={errors.products} onChange={(e) => set('products', e.target.value)} />
          <TextField id={FIELD_IDS.users} label="Usuarios" inputMode="numeric" value={values.users} error={errors.users} onChange={(e) => set('users', e.target.value)} />
        </div>
      </fieldset>

      <fieldset className="flex flex-col gap-1 rounded-lg border border-foreground/10 p-3">
        <legend className="px-1 text-sm font-semibold">
          ¿Qué necesitas? <span className="font-normal text-muted-foreground">(opcional)</span>
        </legend>
        <div className="grid gap-x-4 sm:grid-cols-2">
          {NEED_OPTIONS.map((need) => (
            <label key={need.value} className="flex min-h-11 cursor-pointer items-center gap-3 text-sm">
              <Checkbox checked={values.needs.includes(need.value)} onCheckedChange={(checked) => toggleNeed(need.value, checked === true)} />
              <span>{need.label}</span>
            </label>
          ))}
        </div>
      </fieldset>

      <TextAreaField
        id={FIELD_IDS.message}
        label="Mensaje"
        optional
        rows={4}
        maxLength={MESSAGE_MAX}
        value={values.message}
        error={errors.message}
        hint={`${values.message.length} de ${MESSAGE_MAX} caracteres`}
        onChange={(e) => set('message', e.target.value)}
      />

      <p className="text-xs text-muted-foreground">
        Esto no se guarda en Autopartco: {SALES_EMAIL ? 'prepararemos un correo para que lo envíes tú.' : 'generaremos un resumen para que lo envíes a soporte.'}
      </p>

      <DialogFooter>
        <DialogClose render={<Button type="button" variant="outline" className="h-11 px-4" onClick={onClose} />}>Cancelar</DialogClose>
        <Button type="submit" className="h-11 px-4">
          {SALES_EMAIL ? 'Preparar correo' : 'Generar solicitud'}
        </Button>
      </DialogFooter>
    </form>
  )
}

interface QuoteRequestDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  context: QuoteContext
}

/** Solicitud de cotización del plan Empresarial. El formulario se monta al abrir (estado limpio cada vez). */
export function QuoteRequestDialog({ open, onOpenChange, context }: QuoteRequestDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">{open ? <QuoteForm context={context} onClose={() => onOpenChange(false)} /> : null}</DialogContent>
    </Dialog>
  )
}
