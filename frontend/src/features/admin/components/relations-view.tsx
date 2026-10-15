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
  delay: number
}

function Node({ x, y, width, title, subtitle, strong, delay }: NodeProps) {
  return (
    <g className="relation-node" style={{ animationDelay: `${delay}ms` }}>
      <rect
        x={x}
        y={y}
        width={width}
        height={NODE_H}
        rx={8}
        className={strong ? 'fill-asphalt stroke-asphalt' : 'fill-card stroke-foreground/20'}
      />
      <text x={x + 12} y={y + 18} className={strong ? 'fill-white text-[12px] font-bold' : 'fill-foreground text-[12px] font-bold'}>
        {clip(title, Math.floor(width / 7.2))}
      </text>
      <text x={x + 12} y={y + 34} className={strong ? 'fill-asphalt-muted text-[10.5px]' : 'fill-muted-foreground text-[10.5px]'}>
        {clip(subtitle, Math.floor(width / 5.6))}
      </text>
    </g>
  )
}

/**
 * Relaciones de la solicitud: solicitud, almacén que se crearía y documentos que la respaldan.
 * SVG puro; las líneas se dibujan al abrir (solo sin `prefers-reduced-motion`). La misma
 * información está en el resto del panel, por eso el gráfico expone una descripción de texto.
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
  const description = `Solicitud de ${application.businessName} relacionada con el almacén y con ${application.documents.length} documentos: ${application.documents
    .map((document) => DOCUMENT_TYPE_LABEL[document.type])
    .join(', ')}.`

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
              pathLength={1}
              className="relation-line stroke-thread"
              strokeWidth={1.75}
              style={{ animationDelay: `${index * 90}ms` }}
            />
          )
        })}
        <Node
          x={leftX}
          y={sourceY}
          width={leftW}
          title="Solicitud"
          subtitle={`${LEGAL_TYPE_LABEL[application.legalType]} · ${application.applicantEmail}`}
          strong
          delay={0}
        />
        {targets.map((target, index) => (
          <Node
            key={target.key}
            x={rightX}
            y={index * ROW + (ROW - NODE_H) / 2}
            width={rightW}
            title={target.title}
            subtitle={target.subtitle}
            delay={120 + index * 90}
          />
        ))}
      </svg>
      <figcaption className="text-xs text-muted-foreground">Cada línea une la solicitud con lo que la respalda.</figcaption>
    </figure>
  )
}
