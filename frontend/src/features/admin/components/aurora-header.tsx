import type { ReactNode } from 'react'
import '../admin.css'

/**
 * Cabecera del panel: carbón con un fondo tipo aurora hecho solo con CSS (tres degradados
 * desenfocados que se desplazan lento). Es decorativo (`aria-hidden`) y se detiene con
 * `prefers-reduced-motion`.
 */
export function AuroraHeader({ label, title, description, children }: { label: string; title: string; description: ReactNode; children?: ReactNode }) {
  return (
    <section className="aurora surface-asphalt">
      <div aria-hidden>
        <span className="aurora__blob aurora__blob--a" />
        <span className="aurora__blob aurora__blob--b" />
        <span className="aurora__blob aurora__blob--c" />
      </div>
      <div className="mx-auto flex w-full max-w-7xl flex-col gap-6 px-4 py-8 sm:px-6 sm:py-10 lg:px-8">
        <div className="flex flex-col gap-1.5">
          <p className="label-mono text-asphalt-muted">{label}</p>
          <h1 className="text-[1.75rem] leading-[1.05] font-extrabold tracking-[-0.035em] text-balance sm:text-[2.25rem]">{title}</h1>
          <p className="max-w-2xl text-asphalt-muted">{description}</p>
        </div>
        {children}
      </div>
    </section>
  )
}
