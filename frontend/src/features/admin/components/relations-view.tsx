import type { StoreApplication } from '../api/types'
import { DOCUMENT_TYPE_LABEL, LEGAL_TYPE_LABEL } from '../lib/labels'

const ROW = 56
const NODE_H = 42

function clip(text: string, max: number): string {
  return text.length > max ? `${text.slice(0, max - 1)}…` : text
}

interface NodeProps {
  x: number
  y: number
  width: number
  title: string
  subtitle: string
  strong?: boolean
}

function Node({ x, y, width, title, subtitle, strong }: NodeProps) {
  return (
    <g>
      <rect x={x} y={y} width={width} height={NODE_H} rx={6} className={strong ? 'fill-ap-graphite stroke-ap-graphite' : 'fill-ap-white stroke-ap-line-strong'} />
      <text x={x + 12} y={y + 18} className={strong ? 'fill-ap-white text-[12px] font-semibold' : 'fill-ap-ink text-[12px] font-semibold'}>
        {clip(title, Math.floor(width / 7.2))}
      </text>
      <text x={x + 12} y={y + 34} className={strong ? 'fill-ap-on-dark-muted font-mono text-[10.5px]' : 'fill-ap-muted font-mono text-[10.5px]'}>
        {clip(subtitle, Math.floor(width / 6.4))}
      </text>
    </g>
  )
}

/**
 * Relaciones de la solicitud: la solicitud, el almacén que se crearía y los documentos que la respaldan.
 * SVG estático (sin animación), nodos con borde y líneas en gris acero; el rojo queda para las acciones.
 * La misma información está en el resto del panel; el gráfico expone una descripción de texto.
 */
export function RelationsView({ application }: { application: StoreApplication }) {
  const rows = 1 + application.documents.length
  const height = rows * ROW
  const leftX = 4
  const leftW = 200
  const rightX = 296
  const rightW = 300
  const sourceY = height / 2 - NODE_H / 2
  const sourceMid = height / 2

  const targets = [
    { key: 'store', title: application.businessName, subtitle: `Almacén · NIT ${application.nit}` },
    ...application.documents.map((document) => ({
      key: document.id,
      title: DOCUMENT_TYPE_LABEL[document.type],
      subtitle: document.filename,
    })),
  ]
  const description = `Solicitud de ${application.businessName} relacionada con el almacén y con ${application.documents.length} documentos${
    application.documents.length > 0 ? `: ${application.documents.map((document) => DOCUMENT_TYPE_LABEL[document.type]).join(', ')}` : ''
  }.`

  return (
    <figure className="flex flex-col gap-2">
      <svg role="img" aria-label={description} viewBox={`0 0 600 ${height}`} className="h-auto w-full max-w-xl">
        {targets.map((target, index) => {
          const y = index * ROW + (ROW - NODE_H) / 2
          const mid = y + NODE_H / 2
          return (
            <path
              key={`line-${target.key}`}
              d={`M ${leftX + leftW} ${sourceMid} C ${leftX + leftW + 45} ${sourceMid}, ${rightX - 45} ${mid}, ${rightX} ${mid}`}
              fill="none"
              className="stroke-ap-steel"
              strokeWidth={1.5}
            />
          )
        })}
        <Node x={leftX} y={sourceY} width={leftW} title="Solicitud" subtitle={`${LEGAL_TYPE_LABEL[application.legalType]} · ${application.applicantEmail ?? 'Solicitante eliminado'}`} strong />
        {targets.map((target, index) => (
          <Node key={target.key} x={rightX} y={index * ROW + (ROW - NODE_H) / 2} width={rightW} title={target.title} subtitle={target.subtitle} />
        ))}
      </svg>
      <figcaption className="text-xs text-ap-muted">Cada línea une la solicitud con el almacén y los documentos que la respaldan.</figcaption>
    </figure>
  )
}
