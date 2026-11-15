/**
 * Compatibilidad con teléfonos y navegadores antiguos (iOS < 16, Android con Chrome viejo): métodos de arreglos que el
 * código usa y que esos navegadores no tienen. Se instalan solo si faltan; en navegadores modernos no hacen nada.
 * Debe importarse antes que cualquier otro módulo de la aplicación.
 */
type Compare<T> = ((a: T, b: T) => number) | undefined

function define(name: string, value: (...args: never[]) => unknown): void {
  if (!(name in Array.prototype)) {
    Object.defineProperty(Array.prototype, name, { value, writable: true, configurable: true, enumerable: false })
  }
}

define('toSorted', function toSorted<T>(this: T[], compare?: Compare<T>) {
  return this.slice().sort(compare)
})

define('toReversed', function toReversed<T>(this: T[]) {
  return this.slice().reverse()
})

define('at', function at<T>(this: T[], index: number) {
  const i = Math.trunc(index) || 0
  return this[i < 0 ? this.length + i : i]
})

define('findLast', function findLast<T>(this: T[], predicate: (value: T, index: number, array: T[]) => unknown) {
  for (let i = this.length - 1; i >= 0; i--) if (predicate(this[i], i, this)) return this[i]
  return undefined
})

define('findLastIndex', function findLastIndex<T>(
  this: T[],
  predicate: (value: T, index: number, array: T[]) => unknown,
) {
  for (let i = this.length - 1; i >= 0; i--) if (predicate(this[i], i, this)) return i
  return -1
})

export {}
