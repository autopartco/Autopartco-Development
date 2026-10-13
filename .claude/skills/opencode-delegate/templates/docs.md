## Cómo redactar
- Idioma: español neutro y técnico, salvo que se pida otro. Tono directo; frases cortas; sin relleno ni frases de marketing.
- Fuente de verdad = el repositorio. Antes de afirmar algo del código, compruébalo (`graphify query/explain`, lectura del archivo). Cita rutas reales como `ruta/archivo.ext` y nombres exactos de clases, endpoints y tablas.
- No inventes. Antes de marcar algo como pendiente, búscalo (`grep` del nombre, propiedad o anotación; `graphify query`): casi todo está en el código o en `application*.yaml`. Usa `**[POR CONFIRMAR]**: <qué falta>` solo si de verdad no está en el repo (decisiones futuras, comportamiento externo). Nunca rellenes huecos con suposiciones presentadas como hechos.
- Nombres de propiedades, variables de entorno, rutas y valores por defecto: cópialos literalmente del archivo de origen, sin traducir el formato (p. ej. `refresh-expires-days`, no `refreshExpiresDays`).
- Nunca copies secretos ni claves (contenido de `.pem`, contraseñas, tokens) al documento; menciona solo la ruta o la variable.
- Respeta las decisiones dadas en la tarea: tu trabajo es redactarlas, no rediseñarlas. Si ves una contradicción con el código, señálala en "Pendientes".
- Estructura: título `#`, un párrafo de propósito, secciones `##` en el orden pedido. Usa tablas para inventarios o mapeos, listas para pasos, bloques de código solo para comandos o contratos. Sin emojis.
- Si el documento ya existe, edita in situ y conserva lo que siga siendo válido; no lo reescribas desde cero.
- Al terminar, relee el documento completo una vez: elimina repeticiones, comprueba que cada sección pedida existe y que los enlaces/rutas apuntan a archivos reales.

## Respuesta final (breve; NO repitas el documento)
ARCHIVOS: <ruta> — <creado|editado>, <n.º de secciones>
SECCIONES: <lista de encabezados ##>
POR CONFIRMAR: <lista de huecos marcados> | ninguno
PENDIENTES: <contradicciones o dudas> | ninguno
