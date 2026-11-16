"""stockout_14d: probabilidad de que un inventario llegue a 0 en los próximos 14 días.

Random Forest (bagging de árboles) con calibración isotónica fuera de tiempo, frente a la regla de cobertura
(stock / venta diaria) y a la tasa histórica.
"""
from datetime import date, timedelta

import numpy as np
import pandas as pd
import sklearn
from sklearn.ensemble import RandomForestClassifier
from sklearn.isotonic import IsotonicRegression
from sklearn.metrics import average_precision_score, brier_score_loss, log_loss, roc_auc_score

from .. import db, export

KEY = "stockout_14d"
HORIZON_DAYS = 14
FEATURES = ["stock", "sold7", "sold14", "sold28", "sold91", "sale_events91", "days_since_sale", "correction_out91",
            "correction_in91", "correction_events91", "days_since_correction", "days_since_in", "age_days",
            "cover_days", "stock_per_correction_out", "category_branch_avg_sold91", "branch_sold28"]
FIRST_CUTOFF = date(2026, 6, 1)
LAST_CUTOFF = date(2026, 10, 26)
TEST_CUTOFFS = 4
CALIBRATION_CUTOFFS = 3
PARAMS = {"n_estimators": 150, "max_depth": 8, "min_samples_leaf": 30, "max_features": "sqrt",
          "class_weight": "balanced_subsample", "random_state": 42, "n_jobs": -1}

TARGET_SQL = """
SELECT DISTINCT m.inventory_id, 1 AS target
FROM inventory_movements m
WHERE m.deleted_at IS NULL AND m.new_stock = 0
  AND m.occurred_at >= CAST(CAST(%(cutoff)s AS date) AS timestamp) AT TIME ZONE 'America/Bogota'
  AND m.occurred_at < CAST(CAST(%(cutoff)s AS date) + %(horizon)s AS timestamp) AT TIME ZONE 'America/Bogota'
"""


def cutoffs() -> list[date]:
    return [FIRST_CUTOFF + timedelta(weeks=i) for i in range((LAST_CUTOFF - FIRST_CUTOFF).days // 7 + 1)]


def build(conn) -> pd.DataFrame:
    sql = db.features_sql(KEY)
    frames = []
    for cutoff in cutoffs():
        feats = db.query(conn, sql, {"cutoff": cutoff})
        target = db.query(conn, TARGET_SQL, {"cutoff": cutoff, "horizon": HORIZON_DAYS})
        frame = feats.merge(target, on="inventory_id", how="left")
        frame["target"] = frame["target"].fillna(0).astype(int)
        frame["cutoff"] = cutoff
        frames.append(frame)
        print(f"  corte {cutoff}: {len(frame)} filas, {int(frame.target.sum())} agotamientos")
    data = pd.concat(frames, ignore_index=True)
    data[FEATURES] = data[FEATURES].astype(float)
    return data


def metrics(y: np.ndarray, p: np.ndarray) -> dict:
    p = np.clip(p, 1e-6, 1 - 1e-6)
    return {"aucRoc": round(float(roc_auc_score(y, p)), 4), "aucPr": round(float(average_precision_score(y, p)), 4),
            "brier": round(float(brier_score_loss(y, p)), 5), "logLoss": round(float(log_loss(y, p)), 5)}


def leaf_probability(tree):
    values = tree.tree_.value[:, 0, :]
    return lambda node: values[node, 1] / values[node].sum()


def train(conn) -> None:
    data = build(conn)
    all_cutoffs = sorted(data.cutoff.unique())
    test_c = all_cutoffs[-TEST_CUTOFFS:]
    cal_c = all_cutoffs[-TEST_CUTOFFS - CALIBRATION_CUTOFFS:-TEST_CUTOFFS]
    train_c = all_cutoffs[:-TEST_CUTOFFS - CALIBRATION_CUTOFFS]
    tr, ca, te = (data[data.cutoff.isin(c)] for c in (train_c, cal_c, test_c))
    print(f"  entrenamiento {len(tr)} ({tr.target.mean():.2%}), calibración {len(ca)}, prueba {len(te)} "
          f"({te.target.mean():.2%})")

    forest = RandomForestClassifier(**PARAMS).fit(tr[FEATURES].to_numpy(np.float32), tr.target)
    trees = [export.sklearn_tree(t, leaf_probability(t)) for t in forest.estimators_]
    raw = {"trees": trees, "output": "PROBABILITY_MEAN"}
    iso = IsotonicRegression(out_of_bounds="clip", y_min=0, y_max=1)
    iso.fit(export.evaluate(raw, ca[FEATURES].to_numpy(np.float32)), ca.target)
    calibration = {"x": iso.X_thresholds_.tolist(), "y": iso.y_thresholds_.tolist()}

    X_test = te[FEATURES].to_numpy(np.float32)
    y_test = te.target.to_numpy()
    p_raw_test = forest.predict_proba(X_test)[:, 1]
    p_test = iso.predict(p_raw_test)
    cover = te.cover_days.to_numpy()
    results = {"random_forest_isotonic": metrics(y_test, p_test),
               "rule_cover_days": metrics(y_test, 1 / (1 + cover / HORIZON_DAYS)),
               "historical_rate": metrics(y_test, np.full(len(te), tr.target.mean()))}
    for name, m in results.items():
        print(f"  {name:24s} {m}")
    importance = dict(sorted(zip(FEATURES, forest.feature_importances_.round(4).tolist()), key=lambda kv: -kv[1])[:8])

    model = {
        "key": KEY, "version": f"{date.today():%Y.%m.%d}-rf.1", "name": "Riesgo de agotamiento (14 días)",
        "algorithm": "Random Forest con calibración isotónica", "library": f"scikit-learn {sklearn.__version__}",
        "task": "CLASSIFICATION", "targetEntity": "PRODUCT_BRANCH", "horizonDays": HORIZON_DAYS,
        "features": FEATURES, "output": "PROBABILITY_MEAN", "trees": trees, "calibration": calibration,
        "thresholds": {"high": 0.5, "medium": 0.2},
        "hyperparameters": PARAMS,
        "metrics": results["random_forest_isotonic"],
        "baselineMetrics": {"ruleCoverDays": results["rule_cover_days"],
                            "historicalRate": results["historical_rate"]},
        "featureImportance": importance,
        "trainingRows": int(len(tr)), "trainFrom": str(train_c[0]), "trainTo": str(train_c[-1]),
        "validationFrom": str(test_c[0]), "validationTo": str(test_c[-1]),
    }
    rng = np.random.default_rng(42)
    pos = np.flatnonzero(y_test == 1)
    idx = np.concatenate([rng.choice(pos, size=min(15, len(pos)), replace=False),
                          rng.choice(len(X_test), size=35, replace=False)])
    sample = X_test[idx]
    export.write(model, sample, iso.predict(forest.predict_proba(sample)[:, 1]), tolerance=1e-9)
