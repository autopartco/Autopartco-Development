"""payment_anomaly: detección no supervisada de pagos atípicos con Isolation Forest.

Sin etiquetas de fraude reales: se evalúa con la tasa de alertas, la separación frente a pagos rechazados (señal
débil) y la detección de anomalías inyectadas (monto ×15 de madrugada, ráfaga de intentos, cuenta nueva).
"""
from datetime import date, datetime, timezone

import numpy as np
import sklearn
from sklearn.ensemble import IsolationForest
from sklearn.ensemble._iforest import _average_path_length
from sklearn.metrics import roc_auc_score

from .. import db, export

KEY = "payment_anomaly"
FEATURES = ["log_amount", "amount_vs_history", "attempt_number", "hour_sin", "hour_cos", "payments_30d",
            "declined_30d", "payments_24h", "orders_365d", "units", "lines", "branches", "shipping_ratio",
            "discount_ratio", "account_age_days"]
SPLIT = datetime(2026, 10, 15, 5, tzinfo=timezone.utc)
PARAMS = {"n_estimators": 200, "max_samples": 256, "contamination": 0.02, "random_state": 42}

STATUS_SQL = """
SELECT p.id AS payment_id, ps.code AS status FROM payments p JOIN payment_statuses ps ON ps.id = p.payment_status_id
"""


def inject(X: np.ndarray, rng) -> np.ndarray:
    col = {f: i for i, f in enumerate(FEATURES)}
    A = X[rng.choice(len(X), size=200, replace=False)].copy()
    A[:, col["log_amount"]] += np.log(15)
    A[:, col["amount_vs_history"]] *= 15
    A[:, col["hour_sin"]], A[:, col["hour_cos"]] = np.sin(2 * np.pi * 3 / 24), np.cos(2 * np.pi * 3 / 24)
    A[:, col["payments_24h"]] += 6
    A[:, col["attempt_number"]] = 4
    A[:, col["account_age_days"]] = 0.1
    return A


def leaf_path(tree):
    t = tree.tree_
    depth = np.zeros(t.node_count)
    for node in range(t.node_count):
        for child in (t.children_left[node], t.children_right[node]):
            if child != -1:
                depth[child] = depth[node] + 1
    correction = _average_path_length(t.n_node_samples)
    return lambda node: depth[node] + correction[node]


def train(conn) -> None:
    data = db.query(conn, db.features_sql(KEY), {"since": datetime(2000, 1, 1, tzinfo=timezone.utc),
                                                 "until": datetime.now(timezone.utc)})
    data = data.merge(db.query(conn, STATUS_SQL), on="payment_id")
    data[FEATURES] = data[FEATURES].astype(float)
    created = db.query(conn, "SELECT id AS payment_id, created_at FROM payments")
    data = data.merge(created, on="payment_id")
    tr, te = data[data.created_at < SPLIT], data[data.created_at >= SPLIT]
    print(f"  entrenamiento {len(tr)}, prueba {len(te)}")

    X_train = tr[FEATURES].to_numpy(np.float32)
    X_test = te[FEATURES].to_numpy(np.float32)
    forest = IsolationForest(**PARAMS).fit(X_train)
    threshold = float(-forest.offset_)
    trees = [export.sklearn_tree(t, leaf_path(t), feature_map=f)
             for t, f in zip(forest.estimators_, forest.estimators_features_)]
    c = float(_average_path_length([forest.max_samples_])[0])
    model_core = {"trees": trees, "output": "ISOLATION_SCORE", "isolation": {"averagePathLength": c}}

    score_test = -forest.score_samples(X_test)
    injected = inject(X_test, np.random.default_rng(7))
    score_injected = -forest.score_samples(injected)
    declined = (te.status == "DECLINED").to_numpy().astype(int)
    alert_rate = float(np.mean(score_test > threshold))
    recall = float(np.mean(score_injected > threshold))
    auc_injected = roc_auc_score(np.r_[np.zeros(len(score_test)), np.ones(len(score_injected))],
                                 np.r_[score_test, score_injected])
    results = {"alertRate": round(alert_rate, 4), "injectedRecall": round(recall, 4),
               "injectedAucRoc": round(float(auc_injected), 4),
               "declinedAucRoc": round(float(roc_auc_score(declined, score_test)), 4) if declined.any() else None}
    # Línea base: puntuación Z máxima sobre las mismas variables (umbral |z| > 3).
    mu, sd = X_train.mean(axis=0), X_train.std(axis=0) + 1e-9
    z = lambda X: np.abs((X - mu) / sd).max(axis=1)
    baseline = {"alertRate": round(float(np.mean(z(X_test) > 3)), 4),
                "injectedRecall": round(float(np.mean(z(injected) > 3)), 4),
                "injectedAucRoc": round(float(roc_auc_score(
                    np.r_[np.zeros(len(X_test)), np.ones(len(injected))], np.r_[z(X_test), z(injected)])), 4)}
    print(f"  isolation_forest {results}\n  zscore_max       {baseline}")

    model = {
        "key": KEY, "version": f"{date.today():%Y.%m.%d}-iforest.1", "name": "Pagos atípicos",
        "algorithm": "Isolation Forest", "library": f"scikit-learn {sklearn.__version__}",
        "task": "ANOMALY", "targetEntity": "PAYMENT", "horizonDays": 0,
        "features": FEATURES, **model_core, "thresholds": {"anomaly": threshold},
        "hyperparameters": PARAMS, "metrics": results, "baselineMetrics": {"zScoreMax3": baseline},
        "trainingRows": int(len(tr)), "trainFrom": str(tr.created_at.min().date()),
        "trainTo": str(tr.created_at.max().date()), "validationFrom": str(te.created_at.min().date()),
        "validationTo": str(te.created_at.max().date()),
    }
    sample = np.vstack([X_test[:40], injected[:10]])
    export.write(model, sample, -forest.score_samples(sample), tolerance=1e-9)
