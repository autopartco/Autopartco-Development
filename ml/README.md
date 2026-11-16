# ml/ — entrenamiento de modelos (no se despliega)

Entrena con los datos de Supabase y exporta cada modelo como JSON a `backend/src/main/resources/ml/<clave>/model.json`.
El backend (`core.ml`) lo evalúa en Java puro. Las variables salen de `backend/src/main/resources/ml/<clave>/features.sql`,
el mismo SQL que usa el backend para predecir.

| Clave | Algoritmo | Predice | Línea base superada (prueba temporal) |
|---|---|---|---|
| `demand_category_28d` | LightGBM, regresión Poisson | Unidades en 28 días por sucursal y categoría | MAE 1,49 frente a 1,78 de Croston/TSB y 2,12 de la media de 4 semanas |
| `stockout_14d` | Random Forest + calibración isotónica | Probabilidad de agotarse en 14 días por inventario | AUC-PR 0,38 frente a 0,23 de la regla de cobertura; Brier 0,0034 frente a 0,0041 |
| `payment_anomaly` | Isolation Forest | Pagos atípicos (`payment_risk_assessments`, REVIEW) | Detecta el 96,5 % de las anomalías inyectadas con 4 % de alertas, frente al 10 % de alertas de la puntuación Z |

## Reentrenar

```bash
py -3.14 -m venv ml/.venv
ml/.venv/Scripts/python -m pip install -r ml/requirements.txt
cd ml && .venv/Scripts/python -m autopartco_ml train all
```

Lee las credenciales del `.env` raíz y abre la conexión en solo lectura. Cada entrenamiento:

1. Usa una partición temporal: las últimas fechas de corte quedan para prueba.
2. Compara el modelo contra su línea base.
3. Verifica que el JSON reproduzca a la librería.
4. Escribe `backend/src/test/resources/ml/<clave>/parity.json`.

Después corre `TreeEnsembleParityTest`, haz commit y despliega. La nueva versión se registra sola en `ml.model_versions`
la primera vez que puntúa.

Si cambias un `features.sql`, tienes que reentrenar ese modelo.
