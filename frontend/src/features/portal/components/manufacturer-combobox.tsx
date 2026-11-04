import { useEffect, useId, useRef, useState } from 'react'
import TomSelect from 'tom-select'
import { FieldShell } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { getProductService } from '../api/product-service'
import type { StoreManufacturer } from '../api/types'
import './manufacturer-combobox.css'

/** Fabricante elegido: uno existente (`id`) o uno nuevo local (`newName`) que viaja con el producto. */
export interface ManufacturerChoice {
  id: string
  newName: string
  /** El usuario confirmó que el nombre nuevo no es ninguno de los parecidos. */
  confirmed: boolean
}



interface ManufacturerComboboxProps {
  id: string
  storeId: string
  /** Fabricantes iniciales (`GET /manufacturers`): aprobados y pendientes propios. */
  options: StoreManufacturer[]
  value: ManufacturerChoice
  onChange: (choice: ManufacturerChoice) => void
  onBlur?: () => void
  label?: string
  hint?: string
  error?: string
  required?: boolean
  disabled?: boolean
}

interface Opt {
  value: string
  text: string
  approved: boolean
  isNew?: boolean
}

interface Similar {
  name: string
  suggestions: { id: string; name: string }[]
}

const NEW_PREFIX = 'new:'
const SIMILAR_FROM = 0.6
const normalize = (value: string) => value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/\s+/g, ' ').trim()
const toOpt = (item: StoreManufacturer): Opt => ({ value: item.id, text: item.name, approved: item.approved })
const newOpt = (name: string): Opt => ({ value: NEW_PREFIX + name, text: name, approved: false, isNew: true })
const choiceKey = (choice: ManufacturerChoice) => choice.id || (choice.newName ? NEW_PREFIX + choice.newName : '')

const BADGE_NEW = 'Nuevo · pendiente de revisión'

/**
 * Selector de fabricante con búsqueda difusa (Tom Select). No escribe nada en el servidor: «Agregar
 * “texto”» deja un fabricante nuevo como valor local (`newName`) que viaja con el envío final del
 * producto. Antes consulta la búsqueda (solo lectura) y, si hay nombres parecidos, pregunta
 * «¿Quisiste decir…?»; «No, crear» marca `confirmed` localmente.
 */
export function ManufacturerCombobox({ id, storeId, options, value, onChange, onBlur, label = 'Fabricante', hint, error, required, disabled }: ManufacturerComboboxProps) {
  const selectRef = useRef<HTMLSelectElement>(null)
  const tomRef = useRef<TomSelect | null>(null)
  const similarRef = useRef<HTMLDivElement>(null)
  const confirmedNames = useRef(new Set<string>())
  const [similar, setSimilar] = useState<Similar>()
  const [checking, setChecking] = useState(false)
  const titleId = useId()

  // Las funciones de Tom Select se registran una sola vez: leen siempre los valores vigentes.
  const latest = useRef({ options, value, onChange, onBlur, storeId })
  useEffect(() => {
    latest.current = { options, value, onChange, onBlur, storeId }
  })
  // Si el valor local ya estaba confirmado (volver a este paso), se recuerda.
  useEffect(() => {
    if (value.newName && value.confirmed) confirmedNames.current.add(normalize(value.newName))
  }, [value.newName, value.confirmed])

  const hintId = hint ? `${id}-ayuda` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [errorId, hintId].filter(Boolean).join(' ') || undefined

  /** Selecciona un fabricante (añadiéndolo a la lista local si hace falta). */
  function pick(item: Opt) {
    const tom = tomRef.current
    if (!tom) return
    if (tom.options[item.value]) tom.updateOption(item.value, item)
    else tom.addOption(item)
    tom.addItem(item.value)
    tom.setTextboxValue('')
    tom.close()
  }

  useEffect(() => {
    const element = selectRef.current
    if (!element) return
    const remote = new Map<string, Set<string>>()
    let lastTyped = ''

    const startValue = choiceKey(latest.current.value)
    const initialOptions: Opt[] = latest.current.options.map(toOpt)
    if (latest.current.value.newName) initialOptions.push(newOpt(latest.current.value.newName))

    const tom = new TomSelect(element, {
      maxItems: 1,
      valueField: 'value',
      labelField: 'text',
      searchField: ['text'],
      options: initialOptions,
      items: startValue ? [startValue] : [],
      placeholder: 'Busca o agrega',
      openOnFocus: true,
      selectOnTab: true,
      closeAfterSelect: true,
      maxOptions: 50,
      loadThrottle: 250,
      shouldLoad: (query: string) => query.trim().length >= 2,
      sortField: [{ field: '$score', direction: 'desc' }, { field: 'text' }],
      // Coincidencia sin acentos ni mayúsculas; lo que devuelve el servidor (errores ortográficos) también cuenta.
      score: (search: string) => {
        const q = normalize(search)
        const tokens = q.split(' ').filter(Boolean)
        return (item: Opt) => {
          const text = normalize(item.text)
          if (text === q) return 4
          if (text.startsWith(q)) return 3
          if (tokens.length > 0 && tokens.every((token) => text.includes(token))) return 2
          return remote.get(q)?.has(item.value) ? 1 : 0
        }
      },
      load(query: string, callback: (results?: Opt[]) => void) {
        getProductService()
          .then((service) => service.searchManufacturers(latest.current.storeId, query))
          .then((results) => {
            remote.set(normalize(query), new Set(results.map((item) => item.id)))
            callback(results.map(toOpt))
          })
          .catch(() => callback())
      },
      // Solo lectura: consulta parecidos y deja el nombre nuevo como valor local. No devuelve nada de forma
      // síncrona, así que Tom Select queda bloqueado hasta que llamamos a `finish` (o lo desbloqueamos).
      create: (input: string, finish: (data?: Opt) => void): boolean => {
        const name = input.trim().replace(/\s+/g, ' ')
        const known = Object.values(tomRef.current?.options ?? {}) as Opt[]
        const exact = known.find((item) => normalize(item.text) === normalize(name))
        if (exact) {
          finish(exact)
          return true
        }
        setSimilar(undefined)
        setChecking(true)
        getProductService()
          .then((service) => service.searchManufacturers(latest.current.storeId, name))
          .then((results) => {
            const match = results.find((item) => normalize(item.name) === normalize(name))
            if (match) return finish(toOpt(match))
            const suggestions = results.filter((item) => item.similarity >= SIMILAR_FROM).map(({ id: suggestionId, name: suggestionName }) => ({ id: suggestionId, name: suggestionName }))
            if (suggestions.length === 0) return finish(newOpt(name))
            tomRef.current?.unlock()
            tomRef.current?.setTextboxValue('')
            tomRef.current?.close()
            setSimilar({ name, suggestions })
          })
          // Si la búsqueda falla, se deja como nuevo: el servidor vuelve a comprobar parecidos al enviar.
          .catch(() => finish(newOpt(name)))
          .finally(() => setChecking(false))
        // Sin retorno síncrono: Tom Select sigue bloqueado hasta que `finish` o `unlock` lo liberen.
        return undefined as unknown as boolean
      },
      createOnBlur: false,
      createFilter: (input: string) => input.trim().length >= 2 && input.trim().length <= 100,
      render: {
        option: (data: Opt, escape: (text: string) => string) =>
          `<div>${escape(data.text)}${data.isNew ? `<span class="ts-pending">${BADGE_NEW}</span>` : data.approved ? '' : '<span class="ts-pending">Pendiente de revisión</span>'}</div>`,
        item: (data: Opt, escape: (text: string) => string) =>
          `<div>${escape(data.text)}${data.isNew ? `<span class="ts-pending">${BADGE_NEW}</span>` : data.approved ? '' : '<span class="ts-pending">Pendiente de revisión</span>'}</div>`,
        option_create: (data: { input: string }, escape: (text: string) => string) => `<div class="create">Agregar “${escape(data.input.trim())}”</div>`,
        no_results: (data: { input: string }, escape: (text: string) => string) =>
          `<div class="no-results">${data.input ? `Sin coincidencias para “${escape(data.input)}”.` : 'Sin fabricantes todavía.'}</div>`,
        loading: () => '<div class="no-results">Buscando…</div>',
      },
    })

    tomRef.current = tom
    tom.control_input.setAttribute('aria-label', 'Fabricante')
    tom.control_input.setAttribute('autocomplete', 'off')
    // Enfocar el <select> original (etiqueta, foco al fallar la validación) abre el buscador.
    element.addEventListener('focus', () => tom.focus())

    // `data-typing` solo está mientras hay texto de búsqueda: con el campo vacío se ve el fabricante elegido.
    const setTyping = (typing: boolean) => tom.wrapper.toggleAttribute('data-typing', typing)
    tom.on('type', (text: string) => {
      lastTyped = text
      setTyping(text.length > 0)
    })
    tom.on('change', (selected: string | string[]) => {
      lastTyped = ''
      setTyping(false)
      setSimilar(undefined)
      const key = Array.isArray(selected) ? (selected[0] ?? '') : selected
      if (key.startsWith(NEW_PREFIX)) {
        const name = key.slice(NEW_PREFIX.length)
        latest.current.onChange({ id: '', newName: name, confirmed: confirmedNames.current.has(normalize(name)) })
      } else {
        latest.current.onChange({ id: key, newName: '', confirmed: false })
      }
      // Con una selección hecha se suelta el foco para que se vea lo elegido y no un cursor de escritura.
      if (key) tom.blur()
    })
    tom.on('blur', () => {
      // Lo escrito coincide exacto con uno existente: se selecciona en lugar de perderse.
      const typed = lastTyped.trim()
      lastTyped = ''
      setTyping(false)
      if (typed) {
        const exact = Object.values(tom.options as Record<string, Opt>).find((item) => normalize(item.text) === normalize(typed))
        if (exact && tom.getValue() !== exact.value) tom.addItem(exact.value)
      }
      latest.current.onBlur?.()
    })

    return () => {
      tomRef.current = null
      tom.destroy()
    }
  }, [])

  // Lista local: altas, aprobaciones o pendientes nuevos que llegan desde fuera.
  useEffect(() => {
    const tom = tomRef.current
    if (!tom) return
    for (const item of options) {
      const current = tom.options[item.id] as Opt | undefined
      if (!current) tom.addOption(toOpt(item))
      else if (current.text !== item.name || current.approved !== item.approved) tom.updateOption(item.id, toOpt(item))
    }
  }, [options])

  // Valor del formulario → selector (sin disparar `change`).
  const key = choiceKey(value)
  useEffect(() => {
    const tom = tomRef.current
    if (!tom || tom.getValue() === key) return
    if (value.newName && !tom.options[key]) tom.addOption(newOpt(value.newName))
    tom.setValue(key, true)
  }, [key, value.newName, options])

  useEffect(() => {
    const tom = tomRef.current
    if (!tom) return
    if (disabled) tom.disable()
    else tom.enable()
  }, [disabled])

  useEffect(() => {
    const tom = tomRef.current
    if (!tom) return
    tom.wrapper.classList.toggle('is-invalid', Boolean(error))
    const input = tom.control_input
    if (error) input.setAttribute('aria-invalid', 'true')
    else input.removeAttribute('aria-invalid')
    if (required) input.setAttribute('aria-required', 'true')
    else input.removeAttribute('aria-required')
    if (describedBy) input.setAttribute('aria-describedby', describedBy)
    else input.removeAttribute('aria-describedby')
  }, [error, required, describedBy])

  useEffect(() => {
    if (similar) similarRef.current?.focus()
  }, [similar])

  return (
    <FieldShell id={id} label={label} hint={hint} error={error}>
      {() => (
        <>
          <div>
            <select ref={selectRef} id={id} required={required} />
          </div>
          {checking ? (
            <p role="status" className="text-sm text-muted-foreground">
              Comprobando nombres parecidos…
            </p>
          ) : null}
          {value.newName && !similar ? (
            <p className="text-sm text-muted-foreground">
              “{value.newName}” es un fabricante nuevo: se crea al guardar el producto y queda pendiente de revisión.
            </p>
          ) : null}
          {similar ? (
            <div ref={similarRef} tabIndex={-1} role="group" aria-labelledby={titleId} className="flex flex-col gap-2 rounded-lg border border-border bg-muted/50 p-3 outline-none">
              <p id={titleId} className="text-sm font-semibold">
                ¿Quisiste decir…?
              </p>
              <p className="text-sm text-muted-foreground">Ya existen fabricantes con un nombre parecido a “{similar.name}”. Elige uno para no duplicarlo.</p>
              <div className="flex flex-wrap gap-2">
                {similar.suggestions.map((suggestion) => (
                  <Button
                    key={suggestion.id}
                    type="button"
                    variant="outline"
                    className="h-11 px-4"
                    onClick={() => {
                      const known = latest.current.options.find((item) => item.id === suggestion.id)
                      pick({ value: suggestion.id, text: suggestion.name, approved: known?.approved ?? true })
                    }}
                  >
                    {suggestion.name}
                  </Button>
                ))}
                <Button
                  type="button"
                  variant="ghost"
                  className="h-11 px-4"
                  onClick={() => {
                    confirmedNames.current.add(normalize(similar.name))
                    pick(newOpt(similar.name))
                  }}
                >
                  No, crear “{similar.name}”
                </Button>
              </div>
            </div>
          ) : null}
        </>
      )}
    </FieldShell>
  )
}
