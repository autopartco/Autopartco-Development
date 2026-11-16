"""Exporta ensambles de árboles a un JSON compacto que evalúa core.ml (Java puro) y genera los casos de paridad.

Formato de cada árbol: arreglos paralelos por nodo ``feature`` (-1 = hoja), ``threshold``, ``left``, ``right``,
``value``. Regla de decisión: ``(double)(float) x <= threshold`` va a la izquierda. Las variables nunca son nulas.
"""
import json
import subprocess
from datetime import datetime, timezone

import numpy as np

from .db import REPO, RESOURCES, TEST_RESOURCES


# ---- Conversión ----------------------------------------------------------------------------------------------

def lightgbm_trees(booster) -> list[dict]:
    trees = []
    for info in booster.dump_model()["tree_info"]:
        nodes: list[dict] = []

        def walk(node):
            index = len(nodes)
            nodes.append({})
            if "leaf_value" in node or "split_feature" not in node:
                nodes[index] = {"feature": -1, "threshold": 0.0, "left": -1, "right": -1,
                                "value": float(node.get("leaf_value", 0.0))}
                return index
            if node.get("decision_type", "<=") != "<=":
                raise ValueError("solo se admiten divisiones numéricas <=")
            left = walk(node["left_child"])
            right = walk(node["right_child"])
            nodes[index] = {"feature": int(node["split_feature"]), "threshold": float(node["threshold"]),
                            "left": left, "right": right, "value": 0.0}
            return index

        walk(info["tree_structure"])
        trees.append(_columns(nodes))
    return trees


def sklearn_tree(tree, leaf_value, feature_map=None) -> dict:
    """``leaf_value(node_id)`` da el valor de la hoja; ``feature_map`` traduce índices locales a globales."""
    t = tree.tree_
    nodes = []
    for node in range(t.node_count):
        if t.children_left[node] == -1:
            nodes.append({"feature": -1, "threshold": 0.0, "left": -1, "right": -1,
                          "value": float(leaf_value(node))})
        else:
            feature = int(t.feature[node])
            if feature_map is not None:
                feature = int(feature_map[feature])
            nodes.append({"feature": feature, "threshold": float(t.threshold[node]),
                          "left": int(t.children_left[node]), "right": int(t.children_right[node]), "value": 0.0})
    return _columns(nodes)


def _columns(nodes: list[dict]) -> dict:
    return {k: [n[k] for n in nodes] for k in ("feature", "threshold", "left", "right", "value")}


# ---- Evaluador de referencia (idéntico al de Java) -------------------------------------------------------------

def tree_outputs(trees: list[dict], X: np.ndarray) -> np.ndarray:
    """Valor de hoja de cada árbol para cada fila: matriz [filas, árboles]."""
    X = np.asarray(X, dtype=np.float32).astype(np.float64)
    out = np.empty((X.shape[0], len(trees)))
    for j, tree in enumerate(trees):
        feature, threshold = tree["feature"], tree["threshold"]
        left, right, value = tree["left"], tree["right"], tree["value"]
        for i in range(X.shape[0]):
            node = 0
            while feature[node] != -1:
                node = left[node] if X[i, feature[node]] <= threshold[node] else right[node]
            out[i, j] = value[node]
    return out


def evaluate(model: dict, X: np.ndarray) -> np.ndarray:
    leaves = tree_outputs(model["trees"], X)
    output = model["output"]
    if output == "POISSON_SUM_EXP":
        return np.exp(leaves.sum(axis=1))
    if output == "PROBABILITY_MEAN":
        p = leaves.mean(axis=1)
        cal = model.get("calibration")
        return np.interp(p, cal["x"], cal["y"]) if cal else p
    if output == "ISOLATION_SCORE":
        return np.power(2.0, -leaves.mean(axis=1) / model["isolation"]["averagePathLength"])
    raise ValueError(output)


# ---- Escritura ---------------------------------------------------------------------------------------------------

def code_commit() -> str:
    return subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=REPO, text=True).strip()


def write(model: dict, X_parity: np.ndarray, expected: np.ndarray, tolerance: float) -> None:
    """Escribe model.json y parity.json; falla si el evaluador de referencia no reproduce a la librería."""
    X_parity = np.asarray(X_parity, dtype=np.float32)
    ours = evaluate(model, X_parity)
    diff = float(np.max(np.abs(ours - expected)))
    if diff > tolerance:
        raise AssertionError(f"{model['key']}: el JSON no reproduce a la librería (dif. máx. {diff:.3g})")
    model = {**model, "trainedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
             "codeCommit": code_commit()}
    target = RESOURCES / model["key"] / "model.json"
    target.write_text(json.dumps(model, separators=(",", ":"), allow_nan=False), encoding="utf-8")
    parity_dir = TEST_RESOURCES / model["key"]
    parity_dir.mkdir(parents=True, exist_ok=True)
    (parity_dir / "parity.json").write_text(json.dumps({
        "tolerance": tolerance,
        "rows": [[float(v) for v in row] for row in X_parity],
        "expected": [float(v) for v in expected],
    }, separators=(",", ":")), encoding="utf-8")
    size = target.stat().st_size / 1e6
    print(f"{model['key']}: {target.relative_to(REPO)} ({size:.2f} MB), paridad dif. máx. {diff:.2e}")
