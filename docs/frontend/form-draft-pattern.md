# Patrón «borrador local, un solo envío»

> Notas de uso (T7): `useUnsavedChanges` acepta `when` como booleano o como función `() => boolean`. Orden de guardado correcto: `markSaved()` → navegar → `invalidateStore()`; invalidar antes desmonta el formulario y la navegación se pierde.

## Regla

- **Las lecturas de apoyo pueden ir al servidor** mientras se llena el formulario: catálogos en cascada, búsqueda de fabricantes, opciones, comprobaciones de solo lectura.
- **Las escrituras solo ocurren al enviar**: un único `POST`/`PATCH` (multipart si hay archivos) al pulsar el botón final. Nada se crea, sube o guarda antes: ni fabricantes nuevos, ni imágenes, ni borradores en el servidor.
- Si el envío falla, no queda nada a medias (el backend es atómico) y el usuario conserva lo escrito.

## Piezas

| Pieza | Archivo | Responsabilidad |
|---|---|---|
| `useDraftForm` | `frontend/src/lib/use-draft-form.ts` | Valores en memoria, `isDirty`, `reset`/`markSaved`, validación del cliente por campo, errores de servidor por campo. No hace peticiones. |
| `useSubmitOnce` | `frontend/src/lib/use-submit-once.ts` | Envuelve la escritura: candado síncrono contra doble envío, `pending`, `error`. |
| `useUnsavedChanges` | `frontend/src/lib/use-unsaved-changes.ts` | `useBlocker` de react-router + `beforeunload` mientras `isDirty`. |
| `UnsavedChangesDialog` | `frontend/src/components/common/unsaved-changes-dialog.tsx` | Diálogo «Seguir editando / Salir sin guardar» para el blocker. |
| `FormSection` | `frontend/src/components/common/form-section.tsx` | `fieldset` con título `h2`, ayuda y `disabled` para el grupo. |
| `Stepper` | `frontend/src/components/common/stepper.tsx` | Pasos como botones nativos, `aria-current="step"`, estados completo/error, compacto en móvil. |

## Uso

```tsx
const draft = useDraftForm({ initial: valuesFrom(row), validate })
const submit = useSubmitOnce((input: ProductCreateInput, files: File[]) => service.create(storeId, input, files))
const blocker = useUnsavedChanges(draft.isDirty && !submit.pending)

async function onSubmit(event: FormEvent) {
  event.preventDefault()
  const check = draft.validateAll()
  if (!check.valid) return focusById(fieldId(check.firstInvalid))   // sin red
  const result = await submit.run(toInput(draft.values), files)      // ÚNICA escritura
  if (result.status === 'ok') {
    draft.markSaved()                      // isDirty = false antes de navegar
    navigate(`/productos/${result.value.id}`, { replace: true })
  } else if (result.status === 'error' && result.error instanceof ApiError) {
    draft.setServerErrors(result.error.fieldErrors)   // se limpian al editar el campo
  }
}

return (<form noValidate onSubmit={onSubmit}>… <UnsavedChangesDialog blocker={blocker} /></form>)
```

- Campo con error: `error={draft.errorFor('name')}` (muestra el del servidor, o el del cliente si el campo se visitó con `draft.touch('name')` o ya se intentó enviar).
- Pasos: el paso se marca `error` si algún campo suyo tiene `draft.errors[campo]` tras el primer intento.
- Los archivos y fabricantes nuevos viven en el estado del borrador (objetos `File`, nombre propuesto) y viajan en el envío final.
- `useSubmitOnce.run` nunca lanza: devuelve `ok`, `error` o `busy` (envío ya en curso, no toca el servidor).
- `markSaved()` debe llamarse antes de navegar; si no, el guardia seguirá viendo cambios.

## Verificación («cero escrituras antes del submit»)

El proyecto no tiene runner de pruebas; no se instaló ninguno. Los hooks no importan `fetch`, servicios ni `apiRequest`, así que por construcción no pueden escribir; `tsc -b` verifica los tipos. Caso a automatizar cuando exista un runner (Vitest + Testing Library):

1. Montar el formulario con un servicio falso: `create`/`update` son `vi.fn()`; `fetch` espiado.
2. Escribir en todos los campos, elegir reglas de vehículo, agregar imágenes y un fabricante nuevo, cambiar de paso e intentar navegar (aparece el diálogo de cambios sin guardar).
3. Afirmar: `create`/`update` **0 llamadas**; ninguna petición `POST/PUT/PATCH/DELETE` en `fetch` (las `GET` de catálogo sí se permiten).
4. Pulsar el botón final dos veces seguidas: `create` **1 llamada** con un solo multipart (`data` + `images`).
5. Con un servicio que rechaza: `isDirty` sigue en `true`, los errores por campo se muestran y el botón vuelve a habilitarse.

En el navegador: pestaña Red con filtro `Fetch/XHR` y método distinto de `GET`; debe estar vacía hasta pulsar el botón final.

## Selector de reglas de vehículo

`VehicleRulesPicker` (`features/portal/components/vehicle-rules-picker.tsx`) es una lectura de apoyo: carga marcas, modelos y versiones al elegir cada nivel (`useAsync`, caché en memoria) y devuelve las reglas al borrador. Las reglas viajan en `vehicleRules` del envío final.

## Piezas adicionales (T9)
- `useCloseGuard`: avisa al cerrar paneles y diálogos (X, Esc, fondo, Cancelar) con cambios sin guardar.
- `useFormTouched`: detecta cambios en formularios no controlados.
- `DiscardChangesDialog`: diálogo «Seguir editando / Descartar».
