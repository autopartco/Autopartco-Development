import { BrainCircuitIcon } from 'lucide-react'
import { EmptyState } from '@/components/common/states'
import { formatNumber } from '@/lib/format'
import { useAsync } from '@/lib/use-async'
import { ListSkeleton } from '@/features/portal/components/list-skeleton'
import { StatusBadge } from '@/features/portal/components/status-badge'
import { formatDate, formatDateTime } from '@/features/portal/lib/labels'
import { getAdminConsoleService } from '../api/admin-console-service'
import type { MlModel } from '../api/console-types'
import { AdminBoundary, AdminPage, AdminSection } from '../components/admin-kit'
import { KeyValues } from '../components/console-kit'
import { ADMIN_CACHE_PREFIX } from '../hooks'

const METRIC_LABEL: Record<string, string> = {
  mae: 'Error absoluto medio (MAE)',
  rmse: 'Raíz del error cuadrático (RMSE)',
  wape: 'Error porcentual ponderado (WAPE)',
  poissonDeviance: 'Devianza de Poisson',
  aucRoc: 'AUC-ROC',
  aucPr: 'AUC-PR',
  brier: 'Puntuación de Brier',
  logLoss: 'Pérdida logarítmica',
  alertRate: 'Tasa de alertas',
  injectedRecall: 'Detección de anomalías inyectadas',
  injectedAucRoc: 'AUC-ROC con anomalías inyectadas',
  declinedAucRoc: 'AUC-ROC frente a pagos rechazados',
  mae_14d: 'MAE a 14 días',
  mape_positive_targets: 'MAPE (ventas > 0)',
}

/** En estas métricas un valor mayor es mejor; en el resto, menor. */
const HIGHER_IS_BETTER = new Set(['aucRoc', 'aucPr', 'injectedRecall', 'injectedAucRoc', 'declinedAucRoc'])

const TASK_LABEL: Record<string, string> = { FORECAST: 'Pronóstico', CLASSIFICATION: 'Clasificación', ANOMALY: 'Anomalías', REGRESSION: 'Regresión', RANKING: 'Ranking' }

const num = (value: unknown) => (typeof value === 'number' ? formatNumber(Math.round(value * 10000) / 10000) : '—')

/** Líneas base comparables: objetos de métricas dentro de `baselineMetrics` (p. ej. `tsb`, `naiveSum4`). */
function baselines(model: MlModel): [string, Record<string, unknown>][] {
  return Object.entries(model.baselineMetrics).filter(
    (entry): entry is [string, Record<string, unknown>] => typeof entry[1] === 'object' && entry[1] !== null && !Array.isArray(entry[1]) && Object.values(entry[1]).some((v) => typeof v === 'number'),
  )
}

function MetricsTable({ model }: { model: MlModel }) {
  const keys = Object.keys(model.metrics).filter((key) => typeof model.metrics[key] === 'number')
  const bases = baselines(model)
  if (keys.length === 0) return <p className="text-sm text-ap-muted">Sin métricas registradas.</p>
  return (
    <div className="overflow-x-auto rounded-lg border border-ap-line">
      <table className="w-full min-w-[32rem] text-sm">
        <caption className="sr-only">Métricas de {model.code} frente a sus líneas base</caption>
        <thead className="bg-ap-paper text-left text-ap-muted">
          <tr>
            <th scope="col" className="px-3 py-2 font-semibold">Métrica</th>
            <th scope="col" className="px-3 py-2 text-right font-semibold text-ap-ink">Modelo</th>
            {bases.map(([name]) => (
              <th key={name} scope="col" className="px-3 py-2 text-right font-semibold">
                {name}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-ap-line">
          {keys.map((key) => {
            const value = model.metrics[key] as number
            const better = bases.every(([, base]) => {
              const other = base[key]
              if (typeof other !== 'number') return true
              return HIGHER_IS_BETTER.has(key) ? value >= other : value <= other
            })
            return (
              <tr key={key}>
                <th scope="row" className="px-3 py-2 text-left font-normal text-ap-ink">
                  {METRIC_LABEL[key] ?? key}
                  <span className="block text-xs text-ap-muted">{HIGHER_IS_BETTER.has(key) ? 'Mayor es mejor' : 'Menor es mejor'}</span>
                </th>
                <td className="ap-num px-3 py-2 text-right font-semibold text-ap-ink">
                  {num(value)}
                  {bases.length > 0 && bases.some(([, base]) => typeof base[key] === 'number') ? (
                    <span className={better ? 'block text-xs font-normal text-ap-success' : 'block text-xs font-normal text-ap-warning'}>{better ? 'Supera la línea base' : 'No supera la línea base'}</span>
                  ) : null}
                </td>
                {bases.map(([name, base]) => (
                  <td key={name} className="ap-num px-3 py-2 text-right text-ap-muted">
                    {num(base[key])}
                  </td>
                ))}
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function ModelBlock({ model }: { model: MlModel }) {
  const failed = model.failedRunsLast7Days > 0 || model.lastRunStatus === 'FAILED'
  return (
    <AdminSection
      id={`modelo-${model.code}`}
      title={
        <span className="flex flex-wrap items-center gap-2">
          {model.algorithm}
          <StatusBadge tone={model.status === 'DEPLOYED' ? 'success' : 'neutral'}>{model.status === 'DEPLOYED' ? 'Desplegado' : model.status}</StatusBadge>
          {model.role === 'SHADOW' ? <StatusBadge tone="info">En observación</StatusBadge> : null}
        </span>
      }
      meta={`${model.code} · versión ${model.version} · ${TASK_LABEL[model.task] ?? model.task}`}
    >
      <p className="max-w-prose text-sm text-ap-ink">{model.description}</p>
      <KeyValues
        items={[
          ['Entrenado', formatDateTime(model.trainedAt)],
          ['Datos', model.dataOrigin === 'SYNTHETIC' ? 'Sintéticos' : model.dataOrigin === 'REAL' ? 'Reales' : model.dataOrigin],
          ['Periodo de entrenamiento', model.trainFrom ? `${formatDate(model.trainFrom)} – ${formatDate(model.trainTo)}` : '—'],
          ['Periodo de prueba', model.validationFrom ? `${formatDate(model.validationFrom)} – ${formatDate(model.validationTo)}` : '—'],
          ['Última ejecución', model.lastRunAt ? `${formatDateTime(model.lastRunAt)} · ${model.lastRunRows !== null ? `${formatNumber(model.lastRunRows)} filas` : ''}` : 'Aún no se ejecuta'],
          ['Ejecuciones (7 días)', failed ? <span key="fallos" className="font-semibold text-ap-warning">{formatNumber(model.runsLast7Days)} · {formatNumber(model.failedRunsLast7Days)} con error</span> : formatNumber(model.runsLast7Days)],
        ]}
      />
      <MetricsTable model={model} />
      {model.knownLimitations ? <p className="text-xs text-ap-muted">{model.knownLimitations}</p> : null}
    </AdminSection>
  )
}

/** `/admin/modelos`: modelos predictivos en uso, sus métricas frente a la línea base y la salud de sus ejecuciones. */
export default function AdminModelsPage() {
  const models = useAsync(`${ADMIN_CACHE_PREFIX}ml-models`, () => getAdminConsoleService().models())
  return (
    <AdminPage title="Modelos predictivos" description="Qué modelos usa la plataforma, cómo se compararon con su línea base y si se están ejecutando.">
      <AdminBoundary result={models} what="los modelos" skeleton={<ListSkeleton rows={6} label="Cargando los modelos" />}>
        {(rows) =>
          rows.length === 0 ? (
            <EmptyState icon={<BrainCircuitIcon />} title="Aún no hay modelos registrados" description="Cada modelo se registra la primera vez que el backend lo ejecuta." />
          ) : (
            <div className="flex flex-col gap-10">
              {rows.map((model) => (
                <ModelBlock key={model.code} model={model} />
              ))}
            </div>
          )
        }
      </AdminBoundary>
    </AdminPage>
  )
}
