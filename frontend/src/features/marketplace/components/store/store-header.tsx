import { useState, type ReactNode } from 'react'
import { BadgeCheckIcon, GlobeIcon, PhoneIcon } from 'lucide-react'
import { Container } from '@/components/common/page-header'
import { initials, pluralize } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { StoreBranch, StoreContact, StoreProfile } from '../../api/types'
import { storePath } from '../../lib/paths'
import { ShareButton } from '../share-button'
import { FacebookIcon, InstagramIcon, TikTokIcon } from './brand-icons'
import { DirectionsMenu } from './directions-menu'
import { Stars } from './stars'
import { WhatsappButton, type WhatsappTarget } from './whatsapp-button'

const monthFormatter = new Intl.DateTimeFormat('es-CO', { month: 'long', year: 'numeric' })
const ratingFormatter = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

/** `YYYY-MM-DD` como fecha local (sin desplazar el día por la zona horaria). */
function localDate(value: string): Date {
  const [year, month, day] = value.slice(0, 10).split('-').map(Number)
  return new Date(year ?? 1970, (month ?? 1) - 1, day ?? 1)
}

/** Banner a todo el ancho; sin imagen (o si no carga), un fondo grafito con la franja roja de la marca. */
function Banner({ store }: { store: StoreProfile }) {
  const [failed, setFailed] = useState(false)
  const showImage = !!store.bannerUrl && !failed
  return (
    <div
      className={cn(
        'relative h-36 w-full overflow-hidden border-b-4 border-ap-red sm:h-48 lg:h-60',
        !showImage && 'bg-[linear-gradient(120deg,var(--ap-graphite)_0%,var(--ap-ink)_55%,#3b4148_100%)]',
      )}
    >
      {showImage ? (
        <img
          src={store.bannerUrl}
          alt=""
          width={1600}
          height={320}
          fetchPriority="high"
          className="size-full object-cover"
          onError={() => setFailed(true)}
        />
      ) : null}
    </div>
  )
}

function Logo({ store }: { store: StoreProfile }) {
  const [failed, setFailed] = useState(false)
  const frame = 'size-24 shrink-0 rounded-xl border-4 border-ap-white shadow-none sm:size-32'
  if (store.logoUrl && !failed) {
    return (
      <img
        src={store.logoUrl}
        alt={`Logo de ${store.name}`}
        width={128}
        height={128}
        className={cn(frame, 'bg-ap-white object-contain ring-1 ring-ap-line')}
        onError={() => setFailed(true)}
      />
    )
  }
  return (
    <span className={cn(frame, 'grid place-items-center bg-ap-graphite text-3xl font-semibold text-ap-white sm:text-4xl')} aria-hidden>
      {initials(store.name) || store.name.charAt(0)}
    </span>
  )
}

interface SocialLink {
  label: string
  href: string
  icon: ReactNode
}

/** Redes que la tienda decidió publicar; enlaces armados solo con datos validados por el servidor. */
function socialLinks(contact: StoreContact): SocialLink[] {
  const links: SocialLink[] = []
  if (contact.website?.startsWith('https://')) {
    links.push({ label: 'Sitio web', href: contact.website, icon: <GlobeIcon aria-hidden className="size-5" /> })
  }
  if (contact.instagram) {
    links.push({ label: 'Instagram', href: `https://www.instagram.com/${encodeURIComponent(contact.instagram)}`, icon: <InstagramIcon className="size-5" /> })
  }
  if (contact.facebook) {
    links.push({ label: 'Facebook', href: `https://www.facebook.com/${encodeURIComponent(contact.facebook)}`, icon: <FacebookIcon className="size-5" /> })
  }
  if (contact.tiktok) {
    links.push({ label: 'TikTok', href: `https://www.tiktok.com/@${encodeURIComponent(contact.tiktok)}`, icon: <TikTokIcon className="size-5" /> })
  }
  return links
}

const iconButton =
  'inline-flex size-11 shrink-0 items-center justify-center rounded-md border border-input bg-ap-white text-ap-graphite outline-none transition-colors hover:border-ap-graphite hover:bg-ap-paper focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ap-red'

interface StoreHeaderProps {
  store: StoreProfile
  branches: StoreBranch[]
  crumbs: ReactNode
}

/**
 * Cabecera tipo portada: banner, logo superpuesto, identidad y acciones (WhatsApp, cómo llegar, compartir, redes).
 * El banner va de borde a borde; el resto, en el contenedor de la página.
 */
export function StoreHeader({ store, branches, crumbs }: StoreHeaderProps) {
  const path = storePath(store.id)
  const social = socialLinks(store.contact)

  const whatsappTargets: WhatsappTarget[] = store.whatsappEnabled
    ? branches.some((branch) => branch.whatsapp)
      ? branches.flatMap((branch) => (branch.whatsapp ? [{ branchId: branch.id, name: branch.name, phone: branch.whatsapp }] : []))
      : store.contact.whatsapp
        ? [{ name: store.name, phone: store.contact.whatsapp }]
        : []
    : []

  // Con muchas sedes un menú de 3 aplicaciones por sede sería largo: se invita a elegir en la lista de sedes.
  const manyBranches = branches.length > 3

  return (
    <header>
      <Container className="pt-4 pb-4 sm:pt-6">{crumbs}</Container>
      <Banner store={store} />
      <Container>
        <div className="flex flex-col gap-4 sm:flex-row sm:gap-6">
          <div className="-mt-12 sm:-mt-16">
            <Logo store={store} />
          </div>
          <div className="flex min-w-0 flex-1 flex-col gap-1.5 sm:pt-3">
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
              <h1 className="text-page-title break-words text-ap-ink">{store.name}</h1>
              {store.verified ? (
                <span
                  title="Tienda verificada por Autopartco"
                  className="inline-flex h-6 items-center gap-1 rounded-sm border border-ap-success/25 bg-ap-success-soft px-2 text-xs font-semibold text-ap-success"
                >
                  <BadgeCheckIcon aria-hidden className="size-3.5" />
                  Verificada
                  <span className="sr-only"> por Autopartco</span>
                </span>
              ) : null}
            </div>
            {store.tagline ? <p className="max-w-prose text-base text-ap-ink">{store.tagline}</p> : null}
            <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-ap-muted">
              {store.cities.length > 0 ? <span>{store.cities.join(', ')}</span> : null}
              {store.cities.length > 0 && store.memberSince ? <span aria-hidden>·</span> : null}
              {store.memberSince ? <span>En Autopartco desde {monthFormatter.format(localDate(store.memberSince))}</span> : null}
            </p>
            <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-ap-muted">
              {store.ratingAverage !== undefined && store.ratingCount > 0 ? (
                <>
                  <Stars value={store.ratingAverage} />
                  <span className="font-semibold text-ap-ink tabular-nums">{ratingFormatter.format(store.ratingAverage)}</span>
                  <a href="#resenas" className="underline-offset-4 hover:text-ap-red hover:underline">
                    {pluralize(store.ratingCount, 'reseña', 'reseñas')}
                  </a>
                </>
              ) : (
                <a href="#resenas" className="underline-offset-4 hover:text-ap-red hover:underline">
                  Aún sin reseñas
                </a>
              )}
            </p>
          </div>
        </div>

        <div className="mt-5 flex flex-wrap items-center gap-2">
          <WhatsappButton
            storeId={store.id}
            targets={whatsappTargets}
            template={store.whatsappMessage}
            subject={store.name}
            path={path}
            label="Escribir por WhatsApp"
          />
          {branches.length > 0 && !manyBranches ? (
            <DirectionsMenu storeId={store.id} branches={branches} />
          ) : manyBranches ? (
            <a
              href="#sedes"
              className="inline-flex h-11 items-center justify-center gap-2 rounded-md border border-input bg-ap-white px-4 text-sm font-semibold text-ap-graphite hover:border-ap-graphite hover:bg-ap-paper"
            >
              Ver las sedes
            </a>
          ) : null}
          <ShareButton path={path} title={`${store.name} en Autopartco`} storeId={store.id} />
          {social.length > 0 || store.contact.phone ? <span aria-hidden className="mx-1 hidden h-6 w-px bg-ap-line sm:block" /> : null}
          {store.contact.phone ? (
            <a href={`tel:${store.contact.phone}`} aria-label={`Llamar a ${store.name}`} title="Llamar" className={iconButton}>
              <PhoneIcon aria-hidden className="size-5" />
            </a>
          ) : null}
          {social.map((link) => (
            <a
              key={link.label}
              href={link.href}
              target="_blank"
              rel="noopener noreferrer"
              aria-label={`${link.label} de ${store.name} (se abre en una pestaña nueva)`}
              title={link.label}
              className={iconButton}
            >
              {link.icon}
            </a>
          ))}
        </div>
      </Container>
    </header>
  )
}
