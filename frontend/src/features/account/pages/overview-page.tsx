import { Link } from 'react-router'
import { ChevronRightIcon } from 'lucide-react'
import { ErrorState } from '@/components/common/states'
import { Skeleton } from '@/components/ui/skeleton'
import { useSession } from '@/features/auth/hooks'
import { ACCOUNT_KIND_LABEL, accountKindOf } from '@/features/auth/lib/account-menu-items'
import { PendingReviewsNotice } from '@/features/reviews/components/pending-reviews-notice'
import { useAsync } from '@/lib/use-async'
import { AccountSection } from '../account-layout'
import { ACCOUNT_LINKS, ACCOUNT_SHORTCUTS } from '../links'
import { getProfile, getSettings } from '../api'

const DESCRIPTIONS: Record<string, string> = {
  '/cuenta/perfil': 'Nombre, documento, fecha de nacimiento y teléfono.',
  '/cuenta/seguridad': 'Contraseña, correo, verificación en dos pasos, sesiones y Google.',
  '/cuenta/direcciones': 'Direcciones guardadas para tus entregas.',
  '/cuenta/preferencias': 'Qué correos quieres recibir.',
  '/cuenta/privacidad': 'Descarga tus datos o solicita eliminar la cuenta.',
  '/mis-pedidos': 'Compras, seguimiento, comprobantes y facturas.',
  '/cuenta/calificar': 'Lo que recibiste y aún no calificas.',
  '/cuenta/notificaciones': 'Bandeja e historial de avisos.',
}

/** `/cuenta`: quién eres, estado de la seguridad y accesos a cada sección. */
export default function AccountOverviewPage() {
  const session = useSession()
  const profile = useAsync('portal:account:profile', getProfile)
  const settings = useAsync('portal:account:settings', getSettings)
  const kind = accountKindOf(session?.roles ?? [])

  return (
    <div className="flex flex-col">
      <PendingReviewsNotice />
      <AccountSection id="cuenta-resumen" title="Tu cuenta">
        {profile.status === 'error' ? (
          <ErrorState title="No pudimos cargar tu cuenta" onRetry={profile.retry} />
        ) : !profile.data ? (
          <div className="flex flex-col gap-2" aria-hidden>
            <Skeleton className="h-5 w-48" />
            <Skeleton className="h-4 w-64" />
          </div>
        ) : (
          <dl className="grid gap-x-8 gap-y-3 text-sm sm:grid-cols-3">
            <div>
              <dt className="text-ap-muted">Nombre</dt>
              <dd className="font-semibold text-ap-ink">
                {profile.data.name} {profile.data.surname}
              </dd>
            </div>
            <div>
              <dt className="text-ap-muted">Correo</dt>
              <dd className="break-all text-ap-ink">{profile.data.email}</dd>
            </div>
            <div>
              <dt className="text-ap-muted">Tipo de cuenta</dt>
              <dd className="text-ap-ink">{ACCOUNT_KIND_LABEL[kind]}</dd>
            </div>
            <div>
              <dt className="text-ap-muted">Verificación en dos pasos</dt>
              <dd className="text-ap-ink">{settings.data ? (settings.data.security.twoFactor.enabled ? 'Activa' : 'Desactivada') : '—'}</dd>
            </div>
          </dl>
        )}
      </AccountSection>
      <AccountSection id="cuenta-secciones" title="Administrar">
        <ul className="grid gap-px overflow-hidden rounded-lg border border-ap-line bg-ap-line sm:grid-cols-2">
          {[...ACCOUNT_LINKS.slice(1), ...ACCOUNT_SHORTCUTS].map((link) => (
            <li key={link.to} className="bg-ap-white">
              <Link to={link.to} className="flex min-h-16 items-center gap-3 px-4 py-3 outline-none hover:bg-ap-paper focus-visible:ring-3 focus-visible:ring-ring/50">
                <link.icon className="size-5 shrink-0 text-ap-muted" aria-hidden />
                <span className="flex min-w-0 flex-1 flex-col">
                  <span className="text-sm font-semibold text-ap-ink">{link.label}</span>
                  <span className="text-xs text-ap-muted">{DESCRIPTIONS[link.to]}</span>
                </span>
                <ChevronRightIcon className="size-4 shrink-0 text-ap-muted" aria-hidden />
              </Link>
            </li>
          ))}
        </ul>
      </AccountSection>
    </div>
  )
}
