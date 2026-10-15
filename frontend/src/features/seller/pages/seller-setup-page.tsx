import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { FieldShell, FormAlert, INPUT_CLASS } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { getAuthService } from '@/features/auth/api/auth-service'
import { setSession } from '@/features/auth/session-store'
import { ApiError } from '@/lib/api'
import { getSellerApplicationService } from '../api/seller-service'
import { DOCUMENT_MAX_BYTES } from '../api/types'

const IMAGE_ACCEPT = 'image/png,image/jpeg,image/webp,.png,.jpg,.jpeg,.webp'

/** Mensaje para el usuario según el código de error del backend al configurar el almacén. */
function setupErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'SELLER_SETUP_TOKEN_INVALID') {
      return 'El enlace venció o ya fue utilizado. Escríbenos para recibir uno nuevo.'
    }
    if (error.code === 'SELLER_EMAIL_NOT_VERIFIED') {
      return 'Verifica tu correo antes de terminar la configuración del almacén.'
    }
    return error.message
  }
  return 'No pudimos guardar la configuración. Inténtalo de nuevo.'
}

/** Imagen válida (tipo y tamaño) o mensaje de error. */
function imageError(file: File | undefined): string | undefined {
  if (!file) return undefined
  if (!file.type.startsWith('image/')) return 'Sube una imagen PNG, JPG o WebP.'
  if (file.size > DOCUMENT_MAX_BYTES) return 'La imagen supera 10 MB.'
  return undefined
}

/**
 * `/seller/setup?token=`: el enlace del correo de aprobación. Con el token, el almacén aprobado
 * completa su descripción, logo y banner, y abre sesión como gerente del almacén.
 */
export default function SellerSetupPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const token = params.get('token')?.trim() ?? ''
  const [description, setDescription] = useState('')
  const [logo, setLogo] = useState<File>()
  const [banner, setBanner] = useState<File>()
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)

  if (!token) {
    return (
      <div className="mx-auto w-full max-w-xl px-4 py-10">
        <h1 className="text-2xl font-semibold">Configura tu almacén</h1>
        <p className="mt-4 text-muted-foreground">Este enlace no tiene un token de configuración. Abre el enlace completo del correo de aprobación.</p>
        <Button className="mt-6 h-11 px-5" nativeButton={false} render={<Link to="/" />}>
          Ir al inicio
        </Button>
      </div>
    )
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const next: Record<string, string> = {}
    const logoProblem = imageError(logo)
    const bannerProblem = imageError(banner)
    if (logoProblem) next.logo = logoProblem
    if (bannerProblem) next.banner = bannerProblem
    setErrors(next)
    setFormError(undefined)
    if (Object.keys(next).length > 0) return

    setSubmitting(true)
    try {
      const service = await getSellerApplicationService()
      const result = await service.completeSetup({
        token,
        businessDescription: description,
        logo,
        banner,
      })
      // El backend abre sesión con su cookie de refresh; la leemos para entrar al portal ya autenticado.
      const auth = await getAuthService()
      const session = await auth.restore()
      if (session) setSession(session)
      if (!session) {
        navigate('/ingresar', { replace: true, state: { from: `/portal/${encodeURIComponent(result.storeId)}/inicio` } })
        return
      }
      navigate(`/portal/${encodeURIComponent(result.storeId)}/inicio`, { replace: true })
    } catch (error) {
      setFormError(setupErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto w-full max-w-xl px-4 py-10">
      <h1 className="text-2xl font-semibold">Configura tu almacén</h1>
      <p className="mt-2 text-muted-foreground">Tu solicitud fue aprobada. Completa los datos para empezar a vender.</p>

      {formError ? <FormAlert title="No pudimos completar la configuración">{formError}</FormAlert> : null}

      <form className="mt-6 flex flex-col gap-5" onSubmit={handleSubmit} noValidate>
        <FieldShell id="setup-descripcion" label="Descripción del almacén" optional hint="Cuéntale a tus clientes qué vendes.">
          {(describedBy) => (
            <textarea
              id="setup-descripcion"
              aria-describedby={describedBy}
              className={`${INPUT_CLASS} min-h-24 py-2`}
              maxLength={500}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          )}
        </FieldShell>

        <FieldShell id="setup-logo" label="Logo" optional error={errors.logo} hint="PNG, JPG o WebP, hasta 10 MB.">
          {(describedBy) => (
            <input
              id="setup-logo"
              type="file"
              accept={IMAGE_ACCEPT}
              aria-describedby={describedBy}
              aria-invalid={errors.logo ? true : undefined}
              className={`${INPUT_CLASS} py-2`}
              onChange={(event) => setLogo(event.target.files?.[0])}
            />
          )}
        </FieldShell>

        <FieldShell id="setup-banner" label="Banner" optional error={errors.banner} hint="PNG, JPG o WebP, hasta 10 MB.">
          {(describedBy) => (
            <input
              id="setup-banner"
              type="file"
              accept={IMAGE_ACCEPT}
              aria-describedby={describedBy}
              aria-invalid={errors.banner ? true : undefined}
              className={`${INPUT_CLASS} py-2`}
              onChange={(event) => setBanner(event.target.files?.[0])}
            />
          )}
        </FieldShell>

        <Button type="submit" className="h-11 px-5" disabled={submitting}>
          {submitting ? 'Guardando…' : 'Terminar configuración'}
        </Button>
      </form>
    </div>
  )
}
