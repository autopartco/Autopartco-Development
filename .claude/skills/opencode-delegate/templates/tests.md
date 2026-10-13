## Cómo trabajar
- Localiza la clase bajo prueba y sus pruebas vecinas con `graphify query` / `graphify explain`; copia su estructura, librerías y convenciones (nombres de métodos, builders, mocks).
- Cubre: caso feliz, validaciones y errores esperados, límites (nulos, vacíos, máximos) y reglas de autorización si las hay. Una aserción clara por comportamiento.
- Pruebas unitarias aisladas: sin base de datos real, red ni `@SpringBootTest` salvo que se pida. Nunca ejecutes `BackendApplicationTests`.
- No cambies código de producción para que pase una prueba; si encuentras un bug, déjalo descrito en "Pendientes" con archivo:línea.
- Si te rechazan ejecutar las pruebas, sigue y dilo; el revisor las ejecutará.

## Respuesta final (breve, sin repetir código)
ARCHIVOS: <ruta> — <qué pruebas añadiste, una línea>
CASOS: <lista corta de comportamientos cubiertos>
COMANDOS: <comando> — <resultado> | ninguno
PENDIENTES: <bugs encontrados, casos no cubiertos y por qué> | ninguno
