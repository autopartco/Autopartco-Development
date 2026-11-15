# -*- coding: utf-8 -*-
"""Construye el catalogo de prueba (productos con sus atributos estructurados)."""
import random
import unicodedata

from catalog_data import *  # noqa

rng = random.Random(20261115)


def noacc(text):
    return "".join(c for c in unicodedata.normalize("NFD", text) if unicodedata.category(c) != "Mn")


POPULAR = [
    ("Chevrolet", "Spark"), ("Chevrolet", "Spark GT"), ("Chevrolet", "Sail"), ("Chevrolet", "Tracker"),
    ("Chevrolet", "Aveo"), ("Renault", "Logan"), ("Renault", "Duster"), ("Renault", "Sandero"),
    ("Renault", "Clio"), ("Mazda", "Mazda 2"), ("Mazda", "Mazda 3"), ("Mazda", "CX-5"), ("Kia", "Picanto"),
    ("Kia", "Rio"), ("Kia", "Sportage"), ("Hyundai", "Accent"), ("Hyundai", "Tucson"), ("Hyundai", "i10"),
    ("Nissan", "Versa"), ("Nissan", "Sentra"), ("Nissan", "March"), ("Nissan", "Frontier"), ("Toyota", "Corolla"),
    ("Toyota", "Hilux"), ("Toyota", "Prado"), ("Toyota", "Yaris"), ("Suzuki", "Swift"), ("Suzuki", "Vitara"),
    ("Suzuki", "Alto"), ("Ford", "Fiesta"), ("Ford", "Escape"), ("Ford", "Ranger"), ("Volkswagen", "Gol"),
    ("Volkswagen", "Jetta"), ("Honda", "Civic"), ("Honda", "CR-V"), ("Mitsubishi", "L200"), ("Fiat", "Uno"),
    ("Peugeot", "206"), ("Chery", "Tiggo")]

YEARS = {(mk, md): gens for mk, models in MAKES.items() for md, gens in models}

# variantes del nombre que escribe un vendedor (el resto se deriva del nombre plantilla)
MESSY = {
    "pastillas_freno_del": ["PASTILLA FRENO DEL.", "PASTILLAS FRENO DELANTERAS", "Pastilla de freno delantera"],
    "pastillas_freno_tras": ["PASTILLA FRENO TRAS.", "PASTILLAS FRENO TRASERAS", "Pastilla freno trasera"],
    "disco_freno_del": ["DISCO FRENO DEL.", "DISCO FRENO DELANTERO", "Disco de freno del."],
    "disco_freno_tras": ["DISCO FRENO TRAS.", "DISCO FRENO TRASERO"],
    "amortiguador_del": ["AMORTIG. DEL.", "AMORTIGUADOR DELANTERO", "AMORTIGUADOR DEL"],
    "amortiguador_tras": ["AMORTIG. TRAS.", "AMORTIGUADOR TRASERO", "Amortiguador tras."],
    "bomba_agua": ["BOMBA AGUA", "BOMBA DE AGUA", "BBA AGUA"],
    "bomba_gasolina": ["BOMBA GASOLINA", "BOMBA COMBUSTIBLE", "BBA GASOLINA"],
    "bomba_direccion": ["BOMBA DIRECCION HIDRAULICA", "BOMBA DIRECCION", "BBA DIRECCION"],
    "bomba_freno": ["BOMBA FRENO", "BOMBA DE FRENOS", "BBA FRENO"],
    "bomba_clutch": ["BOMBA CLUTCH", "BOMBA EMBRAGUE", "BOMBA DE CLUTCH"],
    "kit_clutch": ["KIT CLUTCH", "KIT EMBRAGUE", "KIT DE CLUTCH 3 PIEZAS"],
    "kit_distribucion": ["KIT DISTRIBUCION", "KIT TIMING", "KIT DE DISTRIBUCION"],
    "filtro_aire": ["FILTRO AIRE", "FILTRO DE AIRE MOTOR", "Filtro aire"],
    "filtro_aceite": ["FILTRO ACEITE", "FILTRO DE ACEITE", "FILTRO ACEITE MOTOR"],
    "filtro_combustible": ["FILTRO GASOLINA", "FILTRO COMBUSTIBLE", "FILTRO DE COMBUSTIBLE"],
    "filtro_cabina": ["FILTRO AIRE ACONDICIONADO", "FILTRO CABINA", "FILTRO POLEN"],
    "rotula": ["ROTULA SUSPENSION", "ROTULA INFERIOR", "Rotula"],
    "buje_tijera": ["BUJE TIJERA", "BUJES TIJERA", "BUJE BRAZO INFERIOR"],
    "terminal_direccion": ["TERMINAL DIRECCION", "TERMINAL EXTERIOR", "Terminal direccion"],
    "cremallera": ["CREMALLERA DIRECCION", "CREMALLERA HIDRAULICA", "Cremallera dir."],
    "rodamiento_rueda": ["RODAMIENTO RUEDA", "RODAMIENTO RUEDA DEL.", "Balinera rueda"],
    "junta_homocinetica": ["JUNTA HOMOCINETICA", "TRIPOIDE", "Homocinetica"],
    "electroventilador": ["ELECTROVENTILADOR", "VENTILADOR RADIADOR", "Electroventilador radiador"],
    "exploradora": ["EXPLORADORA", "FARO NIEBLA", "Exploradoras"],
    "farola": ["FAROLA DELANTERA", "FAROLA DEL.", "Farola"],
    "stop_trasero": ["STOP TRASERO", "STOP", "Stop trasero"],
    "calavera": ["CALAVERA TRASERA", "CALAVERA", "Calavera"],
    "silenciador": ["SILENCIADOR", "MOFLE", "Silenciador escape"],
    "espejo_retrovisor": ["ESPEJO RETROVISOR", "ESPEJO", "Retrovisor"],
    "parachoques_del": ["PARACHOQUES DEL.", "BUMPER DELANTERO", "Parachoques delantero"],
    "motor_arranque": ["MOTOR ARRANQUE", "ARRANQUE", "Motor de arranque"],
    "empaque_culata": ["EMPAQUE CULATA", "JUNTA CULATA", "Empaque de culata"],
    "bujia": ["BUJIA", "BUJIAS", "Bujia"],
    "plumillas": ["PLUMILLAS", "LIMPIA PARABRISAS", "Plumillas limpiabrisas"],
}


def messy_name(part):
    forms = MESSY.get(part)
    if forms:
        return rng.choice(forms)
    base = noacc(PARTS[part][0])
    return rng.choice([base.upper(), base, base.upper().replace(" DE ", " ")])


def years_label(yf, yt):
    return rng.choice(["%d-%d" % (yf, yt), "%d-%d" % (yf, yt), "%d a %d" % (yf, yt), "%d/%d" % (yf, yt)])


def make_label(make):
    return rng.choice(MAKE_ABBR.get(make, [make.upper()]))


class Product:
    pass


products = []
_seen = set()
_counter = [0]


def add(name, part, mfr, vehicles, group, store=None, universal=False, spec=None, rules=True, pn=None, attrs=None,
        desc=None, key=None, price=None, pos=None, unique=True):
    """vehicles: lista de (marca, modelo, anio_desde|None, anio_hasta|None); None = todos los anios."""
    if unique:
        k = (name, mfr, tuple(vehicles), universal)
        if k in _seen:
            return None
        _seen.add(k)
    _counter[0] += 1
    p = Product()
    p.n = _counter[0]
    p.id = uid(K_PROD, p.n)
    p.name = name
    p.part = part
    meta = PARTS.get(part)
    p.cat = meta[1] if meta else None
    p.pos = pos if pos is not None else (meta[2] if meta else None)
    p.mfr = mfr
    p.vehicles = list(vehicles)
    p.universal = universal
    p.spec = spec
    p.rules = rules and not universal
    p.group = group
    p.store = store if store is not None else rng.randrange(len(STORES))
    p.pn = pn
    p.sku = "AP-%06d" % (100000 + p.n * 7)
    p.attrs = attrs or {}
    p.desc = desc
    base = meta[5] if meta else (price or 50000)
    p.price = int(round(base * rng.uniform(0.8, 1.3) / 100.0)) * 100
    products.append(p)
    return p


def oem_ref():
    style = rng.randrange(4)
    if style == 0:
        return "%05d-%05d" % (rng.randint(4000, 99999), rng.randint(10000, 99999))
    if style == 1:
        return "%s%d-%s-%s%02d" % (rng.choice("ABCDEFGHJKLMNPRSTUVWXYZ"), rng.randint(10000, 99999),
                                  rng.choice(["RNA", "SAA", "ZV5", "EA0"]), rng.choice("ABCDEFG"), rng.randint(0, 99))
    if style == 2:
        return "%s %d %d" % (rng.choice(["W", "C", "F", "P", "A"]), rng.randint(100, 999), rng.randint(10, 99))
    return "%d%s%d" % (rng.randint(10, 99), rng.choice(["V", "X", "K", "G"]), rng.randint(1000, 9999))


def gen_version(make, model, j):
    gens = YEARS[(make, model)]
    return gens[j % len(gens)]


def build():
    # ---- (a) estilo plantilla del catalogo real ----
    for i, part in enumerate(A_PARTS):
        disp, cat, pos, pool, pref, _ = PARTS[part]
        count = 4 if i < 30 else 3
        for j in range(count):
            make, model = POPULAR[(i * 7 + j * 11) % len(POPULAR)]
            mfr = pool[(i + j) % len(pool)]
            yf = yt = None
            if (i + j) % 3 == 0:
                yf, yt = gen_version(make, model, j)
            add("%s %s para %s %s" % (disp, mfr, make, model), part, mfr, [(make, model, yf, yt)], "a")
    for part, name, cat, mfr, spec, attrs, price in UNIVERSAL:
        add(name, part, mfr, [], "a", universal=True, spec=spec, attrs=attrs, price=price, pos=None)
        products[-1].cat = cat

    # ---- (b) como los escribe un vendedor ----
    # b1: mayusculas, abreviaturas, anios en el nombre, un vehiculo
    b1_parts = ["bomba_agua", "kit_clutch", "amortiguador_del", "amortiguador_tras", "pastillas_freno_del",
                "kit_distribucion", "filtro_aire", "filtro_aceite", "rotula", "terminal_direccion", "bobina",
                "sensor_oxigeno", "brazo_control", "radiador", "disco_freno_del", "bomba_direccion",
                "bomba_gasolina", "alternador", "silenciador", "farola"]
    for i in range(44):
        part = b1_parts[i % len(b1_parts)]
        make, model = POPULAR[(i * 5 + 3) % len(POPULAR)]
        gen = gen_version(make, model, i)
        mfr = rng.choice(PARTS[part][3])
        label = "%s %s" % (make_label(make), model.upper())
        yr = (" " + years_label(*gen)) if i % 3 != 2 else ""
        suffix = (" " + mfr.upper()) if i % 4 == 0 else ""
        add("%s %s%s%s" % (messy_name(part), label, yr, suffix), part, mfr, [(make, model, gen[0], gen[1])], "b1")

    # b2: varios vehiculos en el nombre
    multi = [
        [("Chevrolet", "Spark"), ("Chevrolet", "Sail"), ("Chevrolet", "Aveo")],
        [("Mazda", "Mazda 2"), ("Mazda", "Mazda 3")],
        [("Renault", "Logan"), ("Renault", "Sandero"), ("Renault", "Duster")],
        [("Kia", "Rio"), ("Kia", "Picanto")],
        [("Toyota", "Corolla"), ("Toyota", "Yaris")],
        [("Nissan", "Versa"), ("Nissan", "March")],
        [("Volkswagen", "Gol"), ("Volkswagen", "Jetta"), ("Volkswagen", "Polo")],
        [("Hyundai", "Accent"), ("Kia", "Rio")],
        [("Ford", "Fiesta"), ("Ford", "Escape")],
        [("Suzuki", "Swift"), ("Suzuki", "Alto")],
    ]
    b2_parts = ["filtro_aire", "pastillas_freno_del", "bujia", "filtro_aceite", "bomba_agua", "rotula", "termostato",
                "amortiguador_del", "kit_distribucion", "bomba_gasolina", "filtro_combustible", "terminal_direccion"]
    for i in range(26):
        vs = multi[i % len(multi)]
        part = b2_parts[i % len(b2_parts)]
        mfr = rng.choice(PARTS[part][3])
        sep = rng.choice(["/", " - ", " Y ", ", "])
        models = sep.join(("%s %s" % (mk, md) if mk != vs[0][0] else md).upper() for mk, md in vs)
        name = "%s %s" % (messy_name(part), models)
        if i % 3 == 0:
            name += " " + mfr.upper()
        add(name, part, mfr, [(mk, md, None, None) for mk, md in vs], "b2")

    # b3: referencias / numeros de parte (vehiculo solo en las reglas o con la referencia)
    b3_parts = ["pastillas_freno_del", "filtro_aceite", "kit_clutch", "bujia", "alternador", "rodamiento_rueda",
                "bomba_agua", "amortiguador_del", "disco_freno_del", "junta_homocinetica", "bobina", "radiador"]
    for i in range(26):
        part = b3_parts[i % len(b3_parts)]
        make, model = POPULAR[(i * 3 + 1) % len(POPULAR)]
        pn = oem_ref()
        while any(p.pn == pn for p in products):
            pn = oem_ref()
        mfr = rng.choice(PARTS[part][3])
        form = i % 3
        if form == 0:
            name = "%s REF %s" % (messy_name(part), pn)
        elif form == 1:
            name = "%s %s %s" % (pn, messy_name(part), mfr.upper())
        else:
            name = "%s %s" % (messy_name(part), mfr.upper())
        add(name, part, mfr, [(make, model, None, None)], "b3", pn=pn)

    # b4: sin vehiculo en el nombre, con compatibilidad cargada
    b4_parts = ["kit_clutch", "bomba_agua", "pastillas_freno_del", "amortiguador_tras", "kit_distribucion",
                "filtro_aire", "radiador", "cremallera", "bomba_direccion", "catalizador", "farola", "stop_trasero",
                "disco_freno_tras", "electroventilador", "motor_arranque"]
    for i in range(30):
        part = b4_parts[i % len(b4_parts)]
        make, model = POPULAR[(i * 9 + 5) % len(POPULAR)]
        gen = gen_version(make, model, i)
        mfr = rng.choice(PARTS[part][3])
        yf, yt = (gen if i % 2 == 0 else (None, None))
        extra = rng.choice(["", " ORIGINAL", " " + mfr.upper(), " NUEVO", " 4 PIEZAS" if part == "kit_clutch" else ""])
        add("%s%s" % (messy_name(part), extra), part, mfr, [(make, model, yf, yt)], "b4")

    # b5: universales con nombre de vendedor
    uni = [
        ("aceite_20w50", "ACEITE MOTOR 20W50 MULTIGRADO 1L", "ACEITES_LUBRICANTES", "Castrol", "20w50", {"viscosidad": "20W-50", "volumen": "1L"}),
        ("aceite_5w30", "ACEITE SINTETICO 5W30 GALON", "ACEITES_LUBRICANTES", "Mobil", "5w30", {"viscosidad": "5W-30"}),
        ("aceite_5w30", "Aceite 5w-30 sintético 946ml", "ACEITES_LUBRICANTES", "Motul", "5w30", {"viscosidad": "5W-30", "volumen": "946ml"}),
        ("aceite_10w40", "ACEITE 10W40 SEMISINTETICO 1/4 GALON", "ACEITES_LUBRICANTES", "Mobil", "10w40", {"viscosidad": "10W-40"}),
        ("aceite_10w40", "Aceite motor 10W-40 garrafa 5L", "ACEITES_LUBRICANTES", "Castrol", "10w40", {"viscosidad": "10W-40", "volumen": "5L"}),
        ("aceite_15w40", "ACEITE DIESEL 15W40 GALON", "ACEITES_LUBRICANTES", "Mobil", "15w40", {"viscosidad": "15W-40"}),
        ("bombillo_h4", "BOMBILLO H4 12V 60/55W", "ILUMINACION", "Osram", "h4", {"tipo": "H4"}),
        ("bombillo_h4", "Bombillo H4 LED 6000K par", "ILUMINACION", "Philips", "h4", {"tipo": "H4", "tecnologia": "LED"}),
        ("bombillo_h7", "BOMBILLO H7 55W HALOGENO", "ILUMINACION", "Philips", "h7", {"tipo": "H7"}),
        ("bombillo_h7", "Bombillo H7 LED 8000lm", "ILUMINACION", "Osram", "h7", {"tipo": "H7", "tecnologia": "LED"}),
        ("bombillo_h11", "BOMBILLO H11 12V", "ILUMINACION", "Osram", "h11", {"tipo": "H11"}),
        ("plumillas", "LIMPIA PARABRISAS 22\" UNIVERSAL", "CARROCERIA", "Bosch", None, {"medida": "22 pulgadas"}),
        ("plumillas", "PLUMILLAS 18 PULGADAS PAR", "CARROCERIA", "Valeo", None, {"medida": "18 pulgadas"}),
        ("liquido_frenos", "LIQUIDO FRENOS DOT3", "FRENOS", "Bosch", None, {"norma": "DOT 3"}),
        ("refrigerante", "REFRIGERANTE ROSADO LARGA VIDA GALON", "REFRIGERACION", "Valeo", None, {}),
        ("tapa_radiador", "TAPA RADIADOR UNIVERSAL 1.1 BAR", "REFRIGERACION", "Gates", None, {}),
        ("bujia", "BUJIA NGK UNIVERSAL IRIDIO", "ELECTRICO", "NGK", None, {}),
        ("filtro_aire", "FILTRO AIRE DEPORTIVO LAVABLE UNIVERSAL", "FILTROS", "Fram", None, {}),
        ("exploradora", "EXPLORADORAS LED UNIVERSALES PAR", "ILUMINACION", "Hella", None, {}),
        ("espejo_retrovisor", "ESPEJO CONVEXO ADHESIVO UNIVERSAL", "CARROCERIA", "Hella", None, {}),
    ]
    for part, name, cat, mfr, spec, attrs in uni:
        add(name, part, mfr, [], "b5", universal=True, spec=spec, attrs=attrs)
        products[-1].cat = cat

    # b6: vehiculo en el nombre pero sin compatibilidad cargada (hay catalogo asi: ~6 %)
    b6 = [("pastillas_freno_del", "Chevrolet", "Spark GT"), ("kit_clutch", "Suzuki", "Vitara"),
          ("bomba_agua", "Renault", "Duster"), ("amortiguador_del", "Mazda", "CX-5"),
          ("filtro_aire", "Toyota", "Hilux"), ("radiador", "Kia", "Sportage"), ("rotula", "Nissan", "Frontier"),
          ("farola", "Hyundai", "Tucson")]
    for part, make, model in b6:
        mfr = rng.choice(PARTS[part][3])
        add("%s %s %s" % (messy_name(part), make.upper(), model.upper()), part, mfr, [(make, model, None, None)], "b6",
            rules=False)

    # ---- (c) distractores cercanos: familias casi iguales para los mismos vehiculos ----
    family = ["bomba_agua", "bomba_gasolina", "bomba_direccion", "bomba_freno", "bomba_clutch", "filtro_aire",
              "filtro_aceite", "filtro_combustible", "filtro_cabina", "pastillas_freno_del", "pastillas_freno_tras",
              "amortiguador_del", "amortiguador_tras", "disco_freno_del", "kit_clutch"]
    for make, model in [("Chevrolet", "Spark"), ("Mazda", "Mazda 3"), ("Renault", "Logan"), ("Suzuki", "Vitara")]:
        for k, part in enumerate(family):
            disp, cat, pos, pool, pref, _ = PARTS[part]
            mfr = pool[(k + len(model)) % len(pool)]
            add("%s %s para %s %s" % (disp, mfr, make, model), part, mfr, [(make, model, None, None)], "c")
            if k % 3 == 0:
                other = pool[(k + len(model) + 1) % len(pool)]
                add("%s %s para %s %s" % (disp, other, make, model), part, other, [(make, model, None, None)], "c")
    # mismo nombre vendido por otro almacen (precios distintos)
    dup = [p for p in products if p.group == "a" and not p.universal][:10]
    for p in dup:
        other_store = (p.store + 1 + (p.n % 3)) % len(STORES)
        q = add(p.name, p.part, p.mfr, p.vehicles, "c", store=other_store, unique=False)
        q.pn = p.pn
    return products
