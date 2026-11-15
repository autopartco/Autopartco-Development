# -*- coding: utf-8 -*-
"""Genera catalog.sql y golden-queries.csv. Uso (desde esta carpeta): python generate.py

Los datos son inventados y deterministas (semilla fija, UUID derivados de contadores). El SQL resultante es solo de
prueba: no es una migracion. Los productos relevantes de cada consulta salen de los atributos estructurados de
generacion (pieza, fabricante, vehiculo, posicion...), no de lo que devuelva la busqueda.
"""
import csv
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fixture_build as fb  # noqa
from catalog_data import *  # noqa
import queries_spec  # noqa

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.dirname(HERE)
rng = random.Random(7)


def q(value):
    if value is None:
        return "null"
    return "'" + str(value).replace("'", "''") + "'"


def insert(table, cols, rows, chunk=100):
    lines = []
    for i in range(0, len(rows), chunk):
        part = rows[i:i + chunk]
        lines.append("insert into %s (%s) values\n%s;" % (table, ", ".join(cols), ",\n".join(
            "  (" + ", ".join(r) + ")" for r in part)))
    return "\n".join(lines)


def main():
    products = fb.build()
    out = ["-- Generado por generator/generate.py. Datos inventados; solo para la evaluacion de busqueda (no es migracion).",
           "-- Tablas fuente: search_listings NO se llena aqui, lo hace el indexador real (SearchIndexer.reindexAll)."]

    out.append(insert("states", ["id", "code"], [[q(uid(K_STATE, 1)), q("ACTIVE")], [q(uid(K_STATE, 2)), q("INACTIVE")]]))
    active = uid(K_STATE, 1)

    cat_ids = {code: uid(K_CAT, i + 1) for i, code in enumerate(CATEGORIES)}
    out.append(insert("categories", ["id", "code"], [[q(v), q(k)] for k, v in cat_ids.items()]))
    mfr_ids = {name: uid(K_MFR, i + 1) for i, name in enumerate(MANUFACTURERS)}
    out.append(insert("manufacturers", ["id", "name", "approved"], [[q(v), q(k), "true"] for k, v in mfr_ids.items()]))

    make_ids, model_ids, version_ids = {}, {}, {}
    rows_make, rows_model, rows_ver = [], [], []
    mi = vi = 0
    for ki, (make, models) in enumerate(MAKES.items()):
        make_ids[make] = uid(K_MAKE, ki + 1)
        rows_make.append([q(make_ids[make]), q(make)])
        for model, gens in models:
            mi += 1
            model_ids[(make, model)] = uid(K_MODEL, mi)
            rows_model.append([q(model_ids[(make, model)]), q(make_ids[make]), q(model)])
            for gi, (yf, yt) in enumerate(gens):
                vi += 1
                version_ids[(make, model, yf, yt)] = uid(K_VER, vi)
                eng = ["1.0", "1.2", "1.4", "1.6", "1.8", "2.0", "2.5"][vi % 7]
                rows_ver.append([q(version_ids[(make, model, yf, yt)]), q(model_ids[(make, model)]),
                                 q("%s %s" % (eng, ["LS", "LT", "GL", "Full"][vi % 4])), str(yf), str(yt)])
    out.append(insert("make", ["id", "name"], rows_make))
    out.append(insert("model", ["id", "make_id", "name"], rows_model))
    out.append(insert("versions", ["id", "model_id", "version", "year_from", "year_to"], rows_ver))

    out.append(insert("geo_municipalities", ["code", "department_code", "name", "is_department_capital", "population"],
                      [[q(c), q(d), q(n), "true" if cap else "false", str(pop)] for c, d, n, cap, pop in MUNICIPALITIES]))

    out.append(insert("search_synonyms", ["term", "expands_to"],
                      [[q(t), "array[%s]" % ", ".join(q(x) for x in e)] for t, e in SYNONYMS.items()]))

    rows_store, rows_branch, rows_loc = [], [], []
    branches = {}
    bn = 0
    for si, (sname, bl) in enumerate(STORES):
        rows_store.append([q(uid(K_STORE, si + 1)), q(sname), q(active)])
        for (bname, muni, lat, lng) in bl:
            bn += 1
            branches.setdefault(si, []).append(uid(K_BRANCH, bn))
            rows_loc.append([q(uid(K_LOC, bn)), q(muni), str(lat), str(lng)])
            rows_branch.append([q(uid(K_BRANCH, bn)), q(uid(K_STORE, si + 1)), q(bname), q(active), q(uid(K_LOC, bn))])
    out.append(insert("store", ["id", "business_name", "state"], rows_store))
    out.append(insert("locations", ["id", "municipality_code", "latitude", "longitude"], rows_loc))
    out.append(insert("branches", ["id", "store_id", "name", "state", "location_id"], rows_branch))

    # descripciones: algunas copian la compatibilidad como hacen los vendedores
    for p in products:
        if p.group in ("b1", "b4", "b6") and p.n % 5 < 2 and p.vehicles:
            mk, md = p.vehicles[0][0], p.vehicles[0][1]
            p.desc = "Repuesto compatible con %s %s. Garantía de 6 meses." % (mk, md)
        elif p.group == "b5" and p.n % 2 == 0:
            p.desc = "Producto universal. Envíos a todo el país."
        elif p.group == "a" and p.n % 7 == 0:
            p.desc = "Repuesto de calidad original con garantía."

    import json
    rows_p, rows_rule, rows_inv = [], [], []
    rn = inv = 0
    for p in products:
        created = "2026-0%d-%02d 10:%02d:00+00" % (1 + p.n % 9, 1 + p.n % 27, p.n % 60)
        rows_p.append([q(p.id), q(uid(K_STORE, p.store + 1)), q(p.name), q(p.sku), q(p.pn), q(p.desc),
                       q(mfr_ids[p.mfr]), q(cat_ids[p.cat]), q(active), "true" if p.universal else "false",
                       q(json.dumps(p.attrs, ensure_ascii=False)) + "::jsonb", q(created)])
        if p.rules:
            for (mk, md, yf, yt) in p.vehicles:
                rn += 1
                ver = version_ids[(mk, md, yf, yt)] if yf is not None else None
                rows_rule.append([q(uid(K_RULE, rn)), q(p.id), q(make_ids[mk]), q(model_ids[(mk, md)]), q(ver)])
        bl = branches[p.store]
        picks = [bl[0]] if p.n % 3 == 0 else (bl[::-1] if p.n % 3 == 1 else bl)
        for bi, b in enumerate(picks):
            inv += 1
            price = int(round(p.price * (1 + 0.07 * bi * rng.choice([1, -1]) + rng.uniform(-0.03, 0.03)) / 100)) * 100
            rows_inv.append([q(uid(K_INV, inv)), q(p.id), q(b), str(price), str(rng.randint(1, 40))])
        if p.n % 11 == 0 and len(bl) > 1:
            # listado sin stock en la otra sucursal: no esta publicado y no debe indexarse
            other = [b for b in bl if b not in picks]
            if other:
                inv += 1
                rows_inv.append([q(uid(K_INV, inv)), q(p.id), q(other[0]), str(p.price), "0"])
    out.append(insert("products", ["id", "store_id", "name", "sku", "part_number", "description", "manufacturer_id",
                                   "category_id", "state_id", "is_universal", "attributes", "created_at"], rows_p))
    out.append(insert("product_vehicle_rules", ["id", "product_id", "make_id", "model_id", "version_id"], rows_rule))
    out.append(insert("inventory", ["id", "product_id", "branch_id", "price", "stock"], rows_inv))

    # reseñas: algunas calificaciones para que la relevancia vea ratings distintos
    rows_sr, rows_pr = [], []
    for i in range(24):
        rows_sr.append([q(uid(K_REV, 1000 + i)), q(uid(K_STORE, i % len(STORES) + 1)), str(rng.choice([3, 4, 4, 5, 5]))])
    for i in range(60):
        p = products[(i * 5) % len(products)]
        rows_pr.append([q(uid(K_REV, 2000 + i)), q(p.id), str(rng.choice([2, 3, 4, 4, 5]))])
    out.append(insert("store_reviews", ["id", "store_id", "rating"], rows_sr))
    out.append(insert("product_reviews", ["id", "product_id", "rating"], rows_pr))

    with open(os.path.join(OUT, "catalog.sql"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n\n".join(out) + "\n")

    queries = queries_spec.build(products)
    with open(os.path.join(OUT, "golden-queries.csv"), "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["id", "query", "expected_product_ids", "kind", "notes"])
        for qid, text, kind, exp, notes in queries:
            w.writerow([qid, text, "|".join(p.id for p in exp), kind, notes])

    from collections import Counter
    print("productos", len(products), Counter(p.group for p in products))
    print("listados", len(rows_inv), "reglas", len(rows_rule))
    print("consultas", len(queries), Counter(k for _, _, k, _, _ in queries))
    print("sin relevantes:", Counter(k for _, _, k, e, _ in queries if not e))


if __name__ == "__main__":
    main()
