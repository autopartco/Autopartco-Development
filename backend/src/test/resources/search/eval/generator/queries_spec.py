# -*- coding: utf-8 -*-
"""Consultas doradas. Lo relevante se define por la intencion del usuario sobre los atributos estructurados de cada
producto (pieza, fabricante, vehiculo, posicion, especificacion, referencia), nunca por lo que devuelva la busqueda.

Reglas de relevancia:
- Pieza: el producto es de esa clave de pieza (cualquier forma de escribirla: 'clutch' y 'embrague' son la misma).
- Vehiculo (marca [+ modelo] [+ anio o rango]): el producto es universal o alguno de sus vehiculos (en las reglas o,
  si no hay reglas cargadas, en el nombre) coincide; un anio exige que el rango del vehiculo lo cubra (sin rango =
  todos los anios); un rango de anios, que se solape.
"""


from catalog_data import PARTS  # noqa


def R(products, part=None, cat=None, mfr=None, make=None, model=None, pos=None, spec=None, year=None,
      years=None, pn=None, sku=None, uni=True, name=None, nonuni=False):
    parts = {part} if isinstance(part, str) else (set(part) if part else None)
    out = []
    for p in products:
        if parts and p.part not in parts:
            continue
        if cat and p.cat != cat:
            continue
        if mfr and p.mfr != mfr:
            continue
        if pos and p.pos != pos:
            continue
        if spec and p.spec != spec:
            continue
        if pn and p.pn != pn:
            continue
        if sku and p.sku != sku:
            continue
        if name and p.name != name:
            continue
        if nonuni and p.universal:
            continue
        if make:
            if p.universal:
                if not uni:
                    continue
            else:
                ok = False
                for (mk, md, yf, yt) in p.vehicles:
                    if mk != make or (model and md != model):
                        continue
                    if year is not None and yf is not None and not (yf <= year <= yt):
                        continue
                    if years is not None and yf is not None and (yt < years[0] or yf > years[1]):
                        continue
                    ok = True
                if not ok:
                    continue
        out.append(p)
    return out


FILTROS = ["filtro_aire", "filtro_aceite", "filtro_combustible", "filtro_cabina"]
BOMBAS = ["bomba_agua", "bomba_gasolina", "bomba_direccion", "bomba_freno", "bomba_clutch"]
CLUTCH = ["kit_clutch", "bomba_clutch", "rodamiento_clutch"]
BOMBILLOS = ["bombillo_h4", "bombillo_h7", "bombillo_h11"]
ACEITES = ["aceite_5w30", "aceite_10w40", "aceite_20w50", "aceite_15w40", "aceite_transmision"]


def build(products):
    P = products
    rows = []

    def add(kind, text, exp, notes=""):
        rows.append((kind, text, exp, notes))

    first = lambda part, group="a": next(p for p in P if p.part == part and p.group == group and not p.universal)

    # ---------------- exact: nombre casi literal ----------------
    for part in ["bomba_agua", "pastillas_freno_del", "rodamiento_rueda", "amortiguador_tras", "filtro_aceite",
                 "kit_distribucion", "radiador", "terminal_direccion"]:
        p = first(part)
        mk, md = p.vehicles[0][0], p.vehicles[0][1]
        add("exact", p.name, R(P, part=part, mfr=p.mfr, make=mk, model=md, uni=False),
            "nombre de plantilla literal")
    for nm in ["Aceite de motor sintético 5W-30 4L Mobil", "Bombillo farola H4 Philips"]:
        add("exact", nm, R(P, name=nm), "universal con nombre literal")
    p = next(p for p in P if p.group == "b1" and p.part == "kit_clutch")
    add("exact", p.name.lower(), R(P, part="kit_clutch", mfr=p.mfr, make=p.vehicles[0][0], model=p.vehicles[0][1], uni=False),
        "nombre de vendedor en minúsculas")
    p = next(p for p in P if p.group == "b6" and p.part == "farola")
    add("exact", p.name.title(), R(P, part="farola", mfr=p.mfr, make=p.vehicles[0][0], model=p.vehicles[0][1], uni=False),
        "nombre sin compatibilidad cargada")

    # ---------------- natural: conectores, «para», «del» ----------------
    nat = [
        ("pastillas de freno delanteras para el chevrolet tracker", dict(part="pastillas_freno_del", make="Chevrolet", model="Tracker")),
        ("necesito un filtro de aire para mi mazda 3", dict(part="filtro_aire", make="Mazda", model="Mazda 3")),
        ("amortiguadores traseros del renault logan", dict(part="amortiguador_tras", make="Renault", model="Logan")),
        ("bomba de agua para el toyota corolla", dict(part="bomba_agua", make="Toyota", model="Corolla")),
        ("filtro de aceite del nissan versa", dict(part="filtro_aceite", make="Nissan", model="Versa")),
        ("kit de distribución para hyundai tucson", dict(part="kit_distribucion", make="Hyundai", model="Tucson")),
        ("quiero la bomba de gasolina de mi suzuki vitara", dict(part="bomba_gasolina", make="Suzuki", model="Vitara")),
        ("radiador para nissan march", dict(part="radiador", make="Nissan", model="March")),
        ("disco de freno delantero de una mazda 3", dict(part="disco_freno_del", make="Mazda", model="Mazda 3")),
        ("rótula de suspensión para el chevrolet tracker", dict(part="rotula", make="Chevrolet", model="Tracker")),
        ("farola delantera para renault logan", dict(part="farola", make="Renault", model="Logan")),
        ("kit de clutch para el suzuki vitara", dict(part="kit_clutch", make="Suzuki", model="Vitara")),
    ]
    for text, kw in nat:
        add("natural", text, R(P, **kw), "conectores y artículos")

    # ---------------- typo: errores en repuesto, marca o modelo ----------------
    typo = [
        ("pastilas de freno delanteras mazda 3", dict(part="pastillas_freno_del", make="Mazda", model="Mazda 3"), "una l de menos"),
        ("bomba de agua chebrolet spark", dict(part="bomba_agua", make="Chevrolet", model="Spark"), "b/v en la marca"),
        ("amortiguador delantero renaul logan", dict(part="amortiguador_del", make="Renault", model="Logan"), "marca truncada"),
        ("filtro de aire suzuky vitara", dict(part="filtro_aire", make="Suzuki", model="Vitara"), "y/i"),
        ("susuki vitara bomba de agua", dict(part="bomba_agua", make="Suzuki", model="Vitara"), "z/s en la marca (transposición)"),
        ("pastiyas de freno brembo", dict(part=["pastillas_freno_del", "pastillas_freno_tras"], mfr="Brembo"), "ll/y"),
        ("bomvillo h4", dict(part="bombillo_h4"), "b/v y falta de l"),
        ("bombiyo farola h7", dict(part="bombillo_h7"), "ll/y"),
        ("bomba de agua toyta corolla", dict(part="bomba_agua", make="Toyota", model="Corolla"), "marca con una letra menos"),
        ("kit de distribucion hyunday tucson", dict(part="kit_distribucion", make="Hyundai", model="Tucson"), "y/i"),
        ("radidor valeo", dict(part="radiador", mfr="Valeo"), "letra omitida en el repuesto"),
        ("amortiguador trasero mazda tres", dict(part="amortiguador_tras", make="Mazda", model="Mazda 3"), "modelo escrito con letras"),
        ("terminal de direcion kia rio", dict(part="terminal_direccion", make="Kia", model="Rio"), "letra omitida en direccion"),
    ]
    for text, kw, note in typo:
        add("typo", text, R(P, **kw), note)

    # ---------------- vehicle_form: formas de escribir el vehículo ----------------
    vf = [
        ("mazda3", dict(make="Mazda", model="Mazda 3"), "marca y modelo pegados"),
        ("cx5", dict(make="Mazda", model="CX-5"), "modelo sin guion"),
        ("spark gt 2015", dict(make="Chevrolet", model="Spark GT", year=2015), "modelo con sufijo y año"),
        ("filtro de aire mazda3", dict(part="filtro_aire", make="Mazda", model="Mazda 3"), "pegado con repuesto"),
        ("terminal de direccion cx5", dict(part="terminal_direccion", make="Mazda", model="CX-5"), "modelo sin guion"),
        ("bomba de agua tracker 2016-2018", dict(part="bomba_agua", make="Chevrolet", model="Tracker", years=(2016, 2018)), "rango de años"),
        ("kit clutch logan 2010-2015", dict(part="kit_clutch", make="Renault", model="Logan", years=(2010, 2015)), "rango de años"),
        ("kit distribucion spark gt 2015", dict(part="kit_distribucion", make="Chevrolet", model="Spark GT", year=2015), "año suelto"),
        ("mazda 3 2016 filtro aceite", dict(part="filtro_aceite", make="Mazda", model="Mazda 3", year=2016), "año antes del repuesto"),
        ("farola logan 2018", dict(part="farola", make="Renault", model="Logan", year=2018), "sin marca"),
        ("radiador sentra 2010", dict(part="radiador", make="Nissan", model="Sentra", year=2010), "sin marca"),
        ("bomba de gasolina mazda 2 08-13", dict(part="bomba_gasolina", make="Mazda", model="Mazda 2", years=(2008, 2013)), "años de dos dígitos"),
        ("disco de freno cr-v 2019", dict(part="disco_freno_del", make="Honda", model="CR-V", year=2019), "guion en el modelo"),
    ]
    for text, kw, note in vf:
        add("vehicle_form", text, R(P, **kw), note)

    # ---------------- reference: número de parte o SKU ----------------
    b3 = [p for p in P if p.pn and p.group == "b3"]
    for p in b3[::3][:8]:
        add("reference", p.pn, R(P, pn=p.pn), "número de parte exacto")
    for p in b3[1::7][:2]:
        add("reference", "ref " + p.pn.lower(), R(P, pn=p.pn), "número de parte con prefijo y minúsculas")
    for p in [b3[4], b3[11]]:
        add("reference", p.sku, R(P, sku=p.sku), "SKU")
    p = b3[6]
    add("reference", "%s %s" % (PARTS[p.part][0].split()[0].lower(), p.pn), R(P, pn=p.pn), "pieza más referencia")

    # ---------------- manufacturer ----------------
    man = [
        ("pastillas brembo", dict(part=["pastillas_freno_del", "pastillas_freno_tras"], mfr="Brembo")),
        ("filtro mann", dict(part=FILTROS, mfr="Mann")),
        ("bomba de agua gates", dict(part="bomba_agua", mfr="Gates")),
        ("kit clutch luk", dict(part="kit_clutch", mfr="Luk")),
        ("amortiguador monroe", dict(part=["amortiguador_del", "amortiguador_tras"], mfr="Monroe")),
        ("bujia ngk", dict(part="bujia", mfr="NGK")),
        ("radiador valeo", dict(part="radiador", mfr="Valeo")),
        ("aceite mobil", dict(part=ACEITES, mfr="Mobil")),
        ("bombillo osram", dict(part=BOMBILLOS, mfr="Osram")),
        ("disco de freno trw", dict(part=["disco_freno_del", "disco_freno_tras"], mfr="TRW")),
        ("rodamiento skf", dict(part=["rodamiento_rueda", "rodamiento_clutch"], mfr="SKF")),
    ]
    for text, kw in man:
        add("manufacturer", text, R(P, **kw), "fabricante")

    # ---------------- position_spec ----------------
    ps = [
        ("pastillas de freno delanteras", dict(part="pastillas_freno_del")),
        ("pastillas de freno traseras", dict(part="pastillas_freno_tras")),
        ("amortiguador trasero", dict(part="amortiguador_tras")),
        ("aceite 5w30", dict(part="aceite_5w30")),
        ("aceite sintetico 5w-30", dict(part="aceite_5w30")),
        ("aceite 10w40", dict(part="aceite_10w40")),
        ("aceite 20w50", dict(part="aceite_20w50")),
        ("bombillo h4", dict(part="bombillo_h4")),
        ("bombillo h7", dict(part="bombillo_h7")),
        ("h11", dict(part="bombillo_h11")),
        ("aceite 15w40 diesel", dict(part="aceite_15w40")),
        ("disco de freno trasero", dict(part="disco_freno_tras")),
        ("farola delantera", dict(part="farola")),
    ]
    for text, kw in ps:
        add("position_spec", text, R(P, **kw), "posición o especificación")

    # ---------------- synonym_slang: jerga ----------------
    sl = [
        ("clutch", dict(part=CLUTCH), "jerga: clutch/embrague"),
        ("kit de embrague mazda 3", dict(part="kit_clutch", make="Mazda", model="Mazda 3"), "embrague = clutch"),
        ("mofle", dict(part="silenciador"), "mofle = silenciador"),
        ("mofle kia rio", dict(part="silenciador", make="Kia", model="Rio"), "mofle con vehículo"),
        ("exploradoras", dict(part="exploradora"), "faros antiniebla"),
        ("bujes", dict(part="buje_tijera"), "plural"),
        ("shock delantero", dict(part="amortiguador_del"), "shock = amortiguador"),
        ("bandas delanteras mazda 3", dict(part="pastillas_freno_del", make="Mazda", model="Mazda 3"), "bandas = pastillas"),
        ("faros delanteros renault logan", dict(part="farola", make="Renault", model="Logan"), "faro = farola"),
        ("balineras de rueda", dict(part="rodamiento_rueda"), "balinera = rodamiento"),
        ("tripoide", dict(part="junta_homocinetica"), "tripoide = junta homocinética"),
        ("bumper delantero", dict(part="parachoques_del"), "bumper = parachoques"),
    ]
    for text, kw, note in sl:
        add("synonym_slang", text, R(P, **kw), note)

    # ---------------- generic ----------------
    gen = [
        ("filtro", dict(part=FILTROS)),
        ("filtros", dict(part=FILTROS)),
        ("frenos", dict(cat="FRENOS")),
        ("aceite", dict(cat="ACEITES_LUBRICANTES")),
        ("bombillo", dict(part=BOMBILLOS)),
        ("bomba", dict(part=BOMBAS)),
        ("suspension", dict(cat="SUSPENSION")),
        ("amortiguador", dict(part=["amortiguador_del", "amortiguador_tras"])),
        ("radiador", dict(part="radiador")),
    ]
    for text, kw in gen:
        add("generic", text, R(P, **kw), "consulta de una palabra; recall acotado a 10")

    # ---------------- no_match ----------------
    for text in ["pastillas de freno ferrari f40", "turbocargador mclaren 720s", "paneles solares 400w",
                 "ventana corrediza tesla model s", "bomba de agua ford mustang", "llantas 205/55 r16",
                 "kit de herramientas dewalt", "zapatos deportivos nike", "caja de cambios automatica lamborghini"]:
        add("no_match", text, [], "vehículo o pieza que no existe en el catálogo")

    # ---------------- long: 6 o más palabras ----------------
    lg = [
        ("necesito pastillas de freno delanteras para mi chevrolet tracker 2016", dict(part="pastillas_freno_del", make="Chevrolet", model="Tracker", year=2016)),
        ("busco un filtro de aceite para mi mazda 3 del año 2015", dict(part="filtro_aceite", make="Mazda", model="Mazda 3", year=2015)),
        ("me hace falta la bomba de agua de un renault logan 2012", dict(part="bomba_agua", make="Renault", model="Logan", year=2012)),
        ("quiero cambiar los amortiguadores traseros de mi nissan versa", dict(part="amortiguador_tras", make="Nissan", model="Versa")),
        ("kit de distribución completo para chevrolet sail del 2014", dict(part="kit_distribucion", make="Chevrolet", model="Sail", year=2014)),
        ("donde consigo el radiador de una kia sportage 2013", dict(part="radiador", make="Kia", model="Sportage", year=2013)),
        ("bomba de dirección hidráulica para nissan versa del 2022", dict(part="bomba_direccion", make="Nissan", model="Versa", year=2022)),
        ("aceite sintético 5w30 de 4 litros para carro a gasolina", dict(part="aceite_5w30")),
        ("discos de freno delanteros para una mazda 3 modelo 2016", dict(part="disco_freno_del", make="Mazda", model="Mazda 3", year=2016)),
        ("terminal de dirección exterior para kia rio del 2015", dict(part="terminal_direccion", make="Kia", model="Rio", year=2015)),
    ]
    for text, kw in lg:
        add("long", text, R(P, **kw), "frase de 6 o más palabras")

    out = []
    for i, (kind, text, exp, notes) in enumerate(rows, 1):
        out.append(("q%03d" % i, text, kind, exp, notes))
    return out
