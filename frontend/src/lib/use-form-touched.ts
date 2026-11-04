import { useCallback, useState } from 'react'

/**
 * Detecta que el usuario tocó un formulario no controlado (los que leen `FormData` al enviar):
 * cualquier `input`/`change` dentro del `<form>` lo marca. Es una señal gruesa («escribió algo»),
 * suficiente para el guardia de cambios sin guardar. Esparce `formProps` en el `<form>`.
 */
export function useFormTouched() {
  const [touched, setTouched] = useState(false)
  const mark = useCallback(() => setTouched(true), [])
  const clear = useCallback(() => setTouched(false), [])
  return { touched, clear, formProps: { onInput: mark, onChange: mark } }
}
