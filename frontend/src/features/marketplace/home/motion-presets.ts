/** Curva de entrada de la página de inicio. */
export const EASE_THREAD = [0.22, 1, 0.36, 1] as const

/** Grupo que revela a sus hijos una sola vez, con 70 ms de escalonado. */
export const REVEAL_GROUP = {
  hidden: {},
  show: { transition: { staggerChildren: 0.07 } },
}

export const REVEAL_ITEM = {
  hidden: { opacity: 0, y: 14 },
  show: { opacity: 1, y: 0, transition: { duration: 0.5, ease: EASE_THREAD } },
}

/** Props comunes de un contenedor que se revela al entrar en pantalla (una vez). */
export const REVEAL_ONCE = {
  initial: 'hidden',
  whileInView: 'show',
  viewport: { once: true, amount: 0.25 },
  variants: REVEAL_GROUP,
} as const
