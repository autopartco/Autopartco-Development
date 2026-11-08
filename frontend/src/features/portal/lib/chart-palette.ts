/**
 * Colores de las visualizaciones del portal (§20.3). Única fuente: todos los gráficos toman de aquí
 * sus series para que una misma métrica conserve su color en cualquier pantalla. Los valores son
 * los tokens `--ap-*` de `index.css`; aquí solo se asigna el papel de cada uno.
 */
export const CHART_COLOR = {
  /** Serie principal: la métrica que responde la pregunta del gráfico. */
  primary: 'var(--ap-red)',
  /** Serie secundaria o comparada (p. ej. la previsión frente a lo real). */
  secondary: 'var(--ap-graphite)',
  /** Series adicionales. */
  tertiary: 'var(--ap-steel)',
  /** Datos históricos y referencias de fondo. */
  historical: 'var(--ap-muted)',
  /** Rangos de incertidumbre, objetivos y referencias (relleno plano, sin degradado). */
  reference: 'var(--ap-line-strong)',
  /** Líneas de cuadrícula y ejes. */
  grid: 'var(--ap-line)',
  positive: 'var(--ap-success)',
  warning: 'var(--ap-warning)',
  negative: 'var(--ap-danger)',
  info: 'var(--ap-info)',
} as const

/** Utilidades Tailwind equivalentes, para barras y leyendas hechas con HTML. */
export const CHART_BG = {
  primary: 'bg-ap-red',
  secondary: 'bg-ap-graphite',
  tertiary: 'bg-ap-steel',
  reference: 'bg-ap-line-strong',
  track: 'bg-ap-paper',
} as const

/**
 * Escala secuencial de un solo tono (rojo) para mapas de calor: 0 = sin actividad. Cinco niveles
 * claramente distinguibles; el nivel vacío lleva borde para no confundirse con el fondo.
 */
export const HEAT_LEVEL_CLASS = [
  'bg-ap-white ring-1 ring-inset ring-ap-line',
  'bg-ap-red/20',
  'bg-ap-red/45',
  'bg-ap-red/70',
  'bg-ap-red',
] as const
