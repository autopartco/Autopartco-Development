"""demand_category_28d: unidades de los próximos 28 días por sucursal y categoría.

Candidatos: LightGBM (regresión Poisson, modelo global) frente a Croston/TSB por serie y a la media de las últimas
4 semanas. Partición temporal por fechas de corte semanales; se despliega el ganador en el conjunto de prueba.
"""
from datetime import date, timedelta

import lightgbm as lgb
import numpy as np
import pandas as pd

from .. import db, export

KEY = "demand_category_28d"
HORIZON_DAYS = 28
FEATURES = ["lag1", "lag2", "lag3", "lag4", "lag5", "lag6", "sum4", "sum8", "sum13", "sum26", "active_weeks13",
            "active_weeks26", "weeks_since_last", "weeks_since_first", "products13", "orders13", "branch_sum4",
            "category_avg_sum4"]
IDS = ["store_id", "branch_id", "category_id"]
FIRST_CUTOFF = date(2026, 6, 15)
LAST_CUTOFF = date(2026, 10, 12)
TEST_CUTOFFS = 4
VALIDATION_CUTOFFS = 4
PARAMS = {"objective": "poisson", "learning_rate": 0.05, "num_leaves": 15, "max_depth": 6, "min_data_in_leaf": 40,
          "feature_fraction": 0.8, "bagging_fraction": 0.8, "bagging_freq": 1, "lambda_l2": 1.0, "seed": 42,
          "deterministic": True, "num_threads": 4, "verbose": -1}
MAX_TREES = 300

TARGET_SQL = """
SELECT s.branch_id, d.category_id_snapshot AS category_id, SUM(d.units) AS target
FROM order_details d
JOIN sales s ON s.id = d.sale_id
JOIN states st ON st.id = s.state_id
WHERE s.deleted_at IS NULL AND d.deleted_at IS NULL
  AND st.code IN ('CONFIRMED', 'PREPARING', 'SHIPPED', 'DELIVERED')
  AND d.category_id_snapshot IS NOT NULL
  AND s.created_at >= CAST(CAST(%(cutoff)s AS date) AS timestamp) AT TIME ZONE 'America/Bogota'
  AND s.created_at < CAST(CAST(%(cutoff)s AS date) + %(horizon)s AS timestamp) AT TIME ZONE 'America/Bogota'
GROUP BY 1, 2
"""

WEEKLY_SQL = """
SELECT s.branch_id, d.category_id_snapshot AS category_id,
       CAST(date_trunc('week', s.created_at AT TIME ZONE 'America/Bogota') AS date) AS week, SUM(d.units) AS units
FROM order_details d
JOIN sales s ON s.id = d.sale_id
JOIN states st ON st.id = s.state_id
WHERE s.deleted_at IS NULL AND d.deleted_at IS NULL
  AND st.code IN ('CONFIRMED', 'PREPARING', 'SHIPPED', 'DELIVERED')
  AND d.category_id_snapshot IS NOT NULL
GROUP BY 1, 2, 3
"""


def cutoffs() -> list[date]:
    days = (LAST_CUTOFF - FIRST_CUTOFF).days // 7
    return [FIRST_CUTOFF + timedelta(weeks=i) for i in range(days + 1)]


def build(conn) -> pd.DataFrame:
    sql = db.features_sql(KEY)
    frames = []
    for cutoff in cutoffs():
        feats = db.query(conn, sql, {"cutoff": cutoff})
        target = db.query(conn, TARGET_SQL, {"cutoff": cutoff, "horizon": HORIZON_DAYS})
        frame = feats.merge(target, on=["branch_id", "category_id"], how="left")
        frame["target"] = frame["target"].fillna(0).astype(float)
        frame["cutoff"] = cutoff
        frames.append(frame)
        print(f"  corte {cutoff}: {len(frame)} filas")
    data = pd.concat(frames, ignore_index=True)
    data[FEATURES] = data[FEATURES].astype(float)
    return data


# ---- Croston / TSB -----------------------------------------------------------------------------------------------

def tsb_forecast(series: np.ndarray, alpha: float, beta: float) -> float:
    """Teunter-Syntetos-Babai: demanda por periodo = probabilidad de demanda × tamaño medio de la demanda."""
    nonzero = series[series > 0]
    if nonzero.size == 0:
        return 0.0
    size, prob = float(nonzero[0]), float((series > 0).mean())
    for value in series:
        if value > 0:
            size += alpha * (value - size)
            prob += beta * (1 - prob)
        else:
            prob += beta * (0 - prob)
    return prob * size


def tsb_predictions(data: pd.DataFrame, weekly: pd.DataFrame, alpha: float, beta: float) -> np.ndarray:
    pivot = weekly.pivot_table(index=["branch_id", "category_id"], columns="week", values="units",
                               aggfunc="sum", fill_value=0)
    weeks = np.array(pivot.columns)
    matrix = pivot.to_numpy(dtype=float)
    index = {key: i for i, key in enumerate(pivot.index)}
    out = np.zeros(len(data))
    for k, (branch, category, cutoff) in enumerate(zip(data.branch_id, data.category_id, data.cutoff)):
        row = index.get((branch, category))
        if row is None:
            continue
        history = matrix[row, weeks < np.datetime64(cutoff)]
        first = np.argmax(history > 0) if (history > 0).any() else len(history)
        out[k] = tsb_forecast(history[first:], alpha, beta) * HORIZON_DAYS / 7
    return out


# ---- Métricas ----------------------------------------------------------------------------------------------------

def metrics(y: np.ndarray, p: np.ndarray) -> dict:
    p = np.clip(p, 1e-9, None)
    dev = 2 * np.mean(np.where(y > 0, y * np.log(np.where(y > 0, y, 1) / p), 0) - (y - p))
    return {"mae": round(float(np.mean(np.abs(y - p))), 4), "rmse": round(float(np.sqrt(np.mean((y - p) ** 2))), 4),
            "wape": round(float(np.sum(np.abs(y - p)) / max(np.sum(y), 1e-9)), 4),
            "poissonDeviance": round(float(dev), 4)}


def train(conn) -> None:
    data = build(conn)
    weekly = db.query(conn, WEEKLY_SQL)
    weekly["week"] = pd.to_datetime(weekly["week"])
    all_cutoffs = sorted(data.cutoff.unique())
    test_c = all_cutoffs[-TEST_CUTOFFS:]
    val_c = all_cutoffs[-TEST_CUTOFFS - VALIDATION_CUTOFFS:-TEST_CUTOFFS]
    train_c = all_cutoffs[:-TEST_CUTOFFS - VALIDATION_CUTOFFS]
    tr, va, te = (data[data.cutoff.isin(c)] for c in (train_c, val_c, test_c))
    print(f"  entrenamiento {len(tr)}, validación {len(va)}, prueba {len(te)}")

    # TSB: alfa y beta elegidos en validación.
    best = min(((a, b) for a in (0.05, 0.1, 0.2, 0.3) for b in (0.05, 0.1, 0.2, 0.3)),
               key=lambda ab: metrics(va.target.to_numpy(), tsb_predictions(va, weekly, *ab))["mae"])
    tsb_test = tsb_predictions(te, weekly, *best)

    dtrain = lgb.Dataset(tr[FEATURES].to_numpy(np.float32), tr.target.to_numpy())
    dval = lgb.Dataset(va[FEATURES].to_numpy(np.float32), va.target.to_numpy(), reference=dtrain)
    booster = lgb.train(PARAMS, dtrain, num_boost_round=MAX_TREES, valid_sets=[dval],
                        callbacks=[lgb.early_stopping(30, verbose=False)])
    booster = lgb.Booster(model_str=booster.model_to_string(num_iteration=booster.best_iteration or -1))
    X_test = te[FEATURES].to_numpy(np.float32)
    y_test = te.target.to_numpy()
    lgb_test = booster.predict(X_test)

    results = {"lightgbm_poisson": metrics(y_test, lgb_test),
               f"tsb_a{best[0]}_b{best[1]}": metrics(y_test, tsb_test),
               "naive_sum4": metrics(y_test, te.sum4.to_numpy())}
    for name, m in results.items():
        print(f"  {name:22s} {m}")
    importance = dict(sorted(zip(FEATURES, booster.feature_importance("gain").round(1).tolist()),
                             key=lambda kv: -kv[1])[:8])

    model = {
        "key": KEY, "version": f"{date.today():%Y.%m.%d}-lgbm.1",
        "name": "Demanda por sucursal y categoría (28 días)",
        "algorithm": "LightGBM (gradient boosting, regresión Poisson)", "library": f"lightgbm {lgb.__version__}",
        "task": "FORECAST", "targetEntity": "BRANCH", "horizonDays": HORIZON_DAYS,
        "features": FEATURES, "output": "POISSON_SUM_EXP", "trees": export.lightgbm_trees(booster),
        "hyperparameters": {**{k: v for k, v in PARAMS.items() if k not in ("verbose", "num_threads")},
                            "trees": booster.num_trees()},
        "metrics": results["lightgbm_poisson"],
        "baselineMetrics": {"tsb": results[f"tsb_a{best[0]}_b{best[1]}"], "naiveSum4": results["naive_sum4"],
                            "tsbParams": {"alpha": best[0], "beta": best[1]}},
        "featureImportance": importance,
        "trainingRows": int(len(tr)), "trainFrom": str(train_c[0]), "trainTo": str(train_c[-1]),
        "validationFrom": str(test_c[0]), "validationTo": str(test_c[-1]),
    }
    sample = X_test[np.random.default_rng(42).choice(len(X_test), size=min(50, len(X_test)), replace=False)]
    export.write(model, sample, booster.predict(sample), tolerance=1e-6)
