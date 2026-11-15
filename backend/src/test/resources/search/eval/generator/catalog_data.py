# -*- coding: utf-8 -*-
"""Datos inventados y deterministas del fixture de evaluacion de busqueda (nada copiado de la BD real)."""


def uid(kind, n):
    return "%08x-0000-4000-8000-%012x" % (kind, n)


K_MAKE, K_MODEL, K_CAT, K_MFR, K_STORE, K_BRANCH, K_LOC, K_PROD, K_INV, K_VER, K_STATE, K_RULE, K_REV = (
    0x10, 0x11, 0x12, 0x13, 0x20, 0x21, 0x22, 0x30, 0x31, 0x14, 0x40, 0x32, 0x33)

# --- marcas y modelos: (modelo, [(anio_desde, anio_hasta), ...]) ---
MAKES = {
    "Chevrolet": [("Spark", [(2010, 2015), (2016, 2022)]), ("Spark GT", [(2011, 2016)]), ("Sail", [(2011, 2018)]),
                  ("Aveo", [(2008, 2014)]), ("Tracker", [(2014, 2019), (2020, 2024)]), ("Onix", [(2020, 2024)])],
    "Renault": [("Logan", [(2008, 2013), (2014, 2020)]), ("Duster", [(2012, 2019), (2020, 2024)]),
                ("Sandero", [(2009, 2014), (2015, 2021)]), ("Clio", [(2005, 2012)]), ("Kwid", [(2018, 2024)]),
                ("Stepway", [(2012, 2019)])],
    "Mazda": [("Mazda 2", [(2008, 2014), (2015, 2023)]), ("Mazda 3", [(2008, 2013), (2014, 2018), (2019, 2024)]),
              ("CX-5", [(2013, 2016), (2017, 2024)]), ("CX-30", [(2020, 2024)]), ("BT-50", [(2012, 2020)])],
    "Kia": [("Picanto", [(2012, 2017), (2018, 2024)]), ("Rio", [(2012, 2017), (2018, 2024)]),
            ("Sportage", [(2011, 2015), (2016, 2021)]), ("Cerato", [(2010, 2016)])],
    "Hyundai": [("Accent", [(2010, 2017)]), ("Tucson", [(2010, 2015), (2016, 2021)]), ("i10", [(2012, 2019)]),
                ("Creta", [(2017, 2024)])],
    "Nissan": [("Versa", [(2012, 2019), (2020, 2024)]), ("Sentra", [(2007, 2012), (2013, 2019)]),
               ("March", [(2012, 2020)]), ("Frontier", [(2009, 2015), (2016, 2024)]), ("Kicks", [(2017, 2024)])],
    "Toyota": [("Corolla", [(2009, 2013), (2014, 2019)]), ("Hilux", [(2005, 2015), (2016, 2024)]),
               ("Prado", [(2010, 2017)]), ("Yaris", [(2013, 2020)])],
    "Suzuki": [("Swift", [(2008, 2016), (2017, 2024)]), ("Vitara", [(2015, 2024)]), ("Alto", [(2009, 2015)]),
               ("Jimny", [(2019, 2024)])],
    "Ford": [("Fiesta", [(2008, 2013), (2014, 2019)]), ("Escape", [(2013, 2019)]), ("Ranger", [(2012, 2023)]),
             ("Explorer", [(2011, 2019)])],
    "Volkswagen": [("Gol", [(2009, 2016)]), ("Jetta", [(2011, 2018)]), ("Polo", [(2018, 2024)])],
    "Honda": [("Civic", [(2006, 2011), (2012, 2015)]), ("CR-V", [(2012, 2016), (2017, 2022)]), ("Fit", [(2009, 2014)])],
    "Mitsubishi": [("L200", [(2008, 2015), (2016, 2024)]), ("Outlander", [(2013, 2020)])],
    "Fiat": [("Uno", [(2011, 2020)]), ("Palio", [(2008, 2016)])],
    "Peugeot": [("206", [(2003, 2010)]), ("208", [(2013, 2024)])],
    "Chery": [("Tiggo", [(2012, 2020)])],
}
MAKE_ABBR = {"Chevrolet": ["CHEV", "CHEVROLET"], "Renault": ["RENAULT", "REN"], "Volkswagen": ["VW", "VOLKSWAGEN"],
             "Mitsubishi": ["MITSUBISHI", "MITSU"], "Hyundai": ["HYUNDAI", "HYUN"]}

CATEGORIES = ["FILTROS", "FRENOS", "SUSPENSION", "ELECTRICO", "MOTOR", "REFRIGERACION", "TRANSMISION", "DIRECCION",
              "CARROCERIA", "ACEITES_LUBRICANTES", "ESCAPE", "ILUMINACION"]

MANUFACTURERS = ["Gates", "Brembo", "SKF", "Mobil", "Philips", "Bosch", "NGK", "Denso", "Monroe", "Sachs", "Valeo",
                 "Mann", "Fram", "Castrol", "Motul", "Dayco", "TRW", "Delphi", "Luk", "Moog", "Hella", "Osram",
                 "Ferodo"]

# clave -> (nombre plantilla, categoria, posicion 'del'|'tras'|None, fabricantes, prefijo de referencia, precio base)
PARTS = {
    "filtro_aire": ("Filtro de aire", "FILTROS", None, ["Mann", "Fram", "Bosch"], "FA", 38000),
    "filtro_aceite": ("Filtro de aceite", "FILTROS", None, ["Mann", "Fram", "Bosch"], "FO", 24000),
    "filtro_combustible": ("Filtro de combustible", "FILTROS", None, ["Mann", "Bosch", "Delphi"], "FC", 42000),
    "filtro_cabina": ("Filtro de cabina", "FILTROS", None, ["Mann", "Fram", "Valeo"], "FB", 36000),
    "pastillas_freno_del": ("Pastillas de freno delanteras", "FRENOS", "del", ["Brembo", "Ferodo", "TRW", "Bosch"], "PF", 95000),
    "pastillas_freno_tras": ("Pastillas de freno traseras", "FRENOS", "tras", ["Brembo", "Ferodo", "TRW"], "PT", 78000),
    "disco_freno_del": ("Disco de freno delantero", "FRENOS", "del", ["Brembo", "TRW", "Bosch"], "DF", 185000),
    "bomba_freno": ("Bomba de freno", "FRENOS", None, ["TRW", "Bosch", "Delphi"], "BF", 210000),
    "amortiguador_del": ("Amortiguador delantero", "SUSPENSION", "del", ["Monroe", "Sachs", "Moog"], "AD", 230000),
    "amortiguador_tras": ("Amortiguador trasero", "SUSPENSION", "tras", ["Monroe", "Sachs", "Moog"], "AT", 190000),
    "rotula": ("Rótula de suspensión", "SUSPENSION", None, ["Moog", "TRW", "Delphi"], "RO", 88000),
    "buje_tijera": ("Buje de tijera", "SUSPENSION", None, ["Moog", "Sachs"], "BT", 54000),
    "rodamiento_rueda": ("Rodamiento de rueda", "SUSPENSION", None, ["SKF", "Dayco"], "RR", 120000),
    "bujia": ("Bujía", "ELECTRICO", None, ["NGK", "Denso", "Bosch"], "BJ", 18000),
    "alternador": ("Alternador", "ELECTRICO", None, ["Bosch", "Denso", "Valeo"], "AL", 520000),
    "motor_arranque": ("Motor de arranque", "ELECTRICO", None, ["Bosch", "Denso", "Valeo"], "MA", 480000),
    "bomba_agua": ("Bomba de agua", "MOTOR", None, ["Gates", "SKF", "Dayco"], "BA", 125000),
    "bomba_gasolina": ("Bomba de gasolina", "MOTOR", None, ["Bosch", "Delphi", "Denso"], "BG", 235000),
    "kit_distribucion": ("Kit de distribución", "MOTOR", None, ["Gates", "Dayco", "SKF"], "KD", 340000),
    "empaque_culata": ("Empaque de culata", "MOTOR", None, ["Bosch", "Delphi"], "EC", 160000),
    "radiador": ("Radiador", "REFRIGERACION", None, ["Valeo", "Denso", "Hella"], "RA", 390000),
    "termostato": ("Termostato", "REFRIGERACION", None, ["Gates", "Valeo", "Dayco"], "TE", 45000),
    "electroventilador": ("Electroventilador", "REFRIGERACION", None, ["Valeo", "Denso", "Hella"], "EV", 410000),
    "kit_clutch": ("Kit de clutch", "TRANSMISION", None, ["Luk", "Sachs", "Valeo"], "KC", 460000),
    "bomba_clutch": ("Bomba de clutch", "TRANSMISION", None, ["Luk", "Sachs", "TRW"], "BC", 150000),
    "junta_homocinetica": ("Junta homocinética", "TRANSMISION", None, ["SKF", "Moog", "Delphi"], "JH", 210000),
    "bomba_direccion": ("Bomba de dirección", "DIRECCION", None, ["Bosch", "TRW", "Delphi"], "BD", 480000),
    "terminal_direccion": ("Terminal de dirección", "DIRECCION", None, ["Moog", "TRW", "Delphi"], "TD", 76000),
    "cremallera": ("Cremallera de dirección", "DIRECCION", None, ["TRW", "Bosch", "Delphi"], "CR", 890000),
    "espejo_retrovisor": ("Espejo retrovisor", "CARROCERIA", None, ["Hella", "Valeo"], "ER", 98000),
    "parachoques_del": ("Parachoques delantero", "CARROCERIA", "del", ["Hella", "Valeo"], "PD", 360000),
    "guardafango": ("Guardafango", "CARROCERIA", None, ["Hella", "Valeo"], "GF", 175000),
    "silenciador": ("Silenciador", "ESCAPE", None, ["Bosch", "Delphi", "Denso"], "SI", 190000),
    "catalizador": ("Catalizador", "ESCAPE", None, ["Bosch", "Denso", "Delphi"], "CA", 780000),
    "flexible_escape": ("Flexible de escape", "ESCAPE", None, ["Bosch", "Delphi"], "FE", 95000),
    "farola": ("Farola delantera", "ILUMINACION", "del", ["Hella", "Valeo", "Osram"], "FD", 270000),
    "stop_trasero": ("Stop trasero", "ILUMINACION", "tras", ["Hella", "Valeo"], "ST", 165000),
    "exploradora": ("Exploradora", "ILUMINACION", None, ["Hella", "Osram", "Philips"], "EX", 130000),
    # solo en el grupo (b)
    "bobina": ("Bobina de encendido", "ELECTRICO", None, ["Bosch", "Denso", "NGK"], "BO", 120000),
    "sensor_oxigeno": ("Sensor de oxígeno", "ELECTRICO", None, ["Bosch", "Denso", "NGK"], "SO", 230000),
    "brazo_control": ("Brazo de control", "SUSPENSION", None, ["Moog", "TRW"], "BR", 210000),
    "resorte_tras": ("Resorte trasero", "SUSPENSION", "tras", ["Sachs", "Moog"], "RT", 140000),
    "tapa_radiador": ("Tapa de radiador", "REFRIGERACION", None, ["Gates", "Valeo"], "TR", 22000),
    "manguera_radiador": ("Manguera de radiador", "REFRIGERACION", None, ["Gates", "Dayco"], "MR", 48000),
    "correa_accesorios": ("Correa de accesorios", "MOTOR", None, ["Gates", "Dayco"], "CO", 52000),
    "soporte_motor": ("Soporte de motor", "MOTOR", None, ["Moog", "Sachs"], "SM", 135000),
    "rodamiento_clutch": ("Rodamiento de clutch", "TRANSMISION", None, ["SKF", "Luk"], "RC", 98000),
    "plumillas": ("Plumillas limpiaparabrisas", "CARROCERIA", None, ["Bosch", "Valeo"], "PL", 42000),
    "manija_puerta": ("Manija de puerta", "CARROCERIA", None, ["Hella", "Valeo"], "MP", 62000),
    "tubo_escape": ("Tubo de escape", "ESCAPE", None, ["Bosch", "Delphi"], "TU", 145000),
    "calavera": ("Calavera trasera", "ILUMINACION", "tras", ["Hella", "Valeo"], "CL", 185000),
    "zapatas_freno": ("Zapatas de freno traseras", "FRENOS", "tras", ["Ferodo", "TRW"], "ZP", 69000),
    "disco_freno_tras": ("Disco de freno trasero", "FRENOS", "tras", ["Brembo", "TRW"], "DT", 160000),
    "cilindro_rueda": ("Cilindro de rueda", "FRENOS", "tras", ["TRW", "Bosch"], "CI", 58000),
}

# piezas del grupo (a) con vehiculo en el nombre: las 38 primeras
A_PARTS = list(PARTS)[:38]

# universales: (clave, nombre, categoria, fabricante, especificacion, atributos, precio)
UNIVERSAL = [
    ("aceite_5w30", "Aceite de motor sintético 5W-30 4L Mobil", "ACEITES_LUBRICANTES", "Mobil", "5w30", {"viscosidad": "5W-30", "volumen": "4L"}, 185000),
    ("aceite_5w30", "Aceite de motor sintético 5W-30 1L Castrol", "ACEITES_LUBRICANTES", "Castrol", "5w30", {"viscosidad": "5W-30", "volumen": "1L"}, 52000),
    ("aceite_10w40", "Aceite de motor semisintético 10W-40 4L Mobil", "ACEITES_LUBRICANTES", "Mobil", "10w40", {"viscosidad": "10W-40", "volumen": "4L"}, 128000),
    ("aceite_10w40", "Aceite de motor semisintético 10W-40 1L Motul", "ACEITES_LUBRICANTES", "Motul", "10w40", {"viscosidad": "10W-40", "volumen": "1L"}, 38000),
    ("aceite_20w50", "Aceite de motor mineral 20W-50 4L Castrol", "ACEITES_LUBRICANTES", "Castrol", "20w50", {"viscosidad": "20W-50", "volumen": "4L"}, 98000),
    ("aceite_15w40", "Aceite de motor diésel 15W-40 4L Mobil", "ACEITES_LUBRICANTES", "Mobil", "15w40", {"viscosidad": "15W-40", "volumen": "4L"}, 135000),
    ("aceite_transmision", "Aceite de transmisión 75W-90 1L Motul", "ACEITES_LUBRICANTES", "Motul", "75w90", {"viscosidad": "75W-90", "volumen": "1L"}, 64000),
    ("liquido_frenos", "Líquido de frenos DOT 4 500ml Bosch", "FRENOS", "Bosch", None, {"norma": "DOT 4"}, 26000),
    ("bombillo_h4", "Bombillo farola H4 Philips", "ILUMINACION", "Philips", "h4", {"tipo": "H4", "voltaje": "12V"}, 28000),
    ("bombillo_h7", "Bombillo farola H7 Osram", "ILUMINACION", "Osram", "h7", {"tipo": "H7", "voltaje": "12V"}, 34000),
    ("bombillo_h11", "Bombillo farola H11 Philips", "ILUMINACION", "Philips", "h11", {"tipo": "H11", "voltaje": "12V"}, 36000),
    ("refrigerante", "Refrigerante verde 50/50 galón Valeo", "REFRIGERACION", "Valeo", None, {"volumen": "1 galón"}, 54000),
]

# Sinonimos de arranque (inventados; la tabla real tiene ~58). Sin 'clutch', 'mofle', 'exploradoras' ni 'bujes' a proposito:
# la jerga que el catalogo real no cubre debe verse como fallo en la linea base.
SYNONYMS = {
    "bandas": ["pastillas"], "shock": ["amortiguador"], "shocks": ["amortiguador"], "bujia": ["bujias"],
    "bujias": ["bujia"], "correa": ["banda"], "banda": ["correa"], "llanta": ["neumatico"],
    "bateria": ["acumulador"], "silenciador": ["muffler"], "radiador": ["panal"],
    "farola": ["faro", "farol"], "faro": ["farola"], "retrovisor": ["espejo"], "espejo": ["retrovisor"],
    "limpiaparabrisas": ["plumillas"], "plumillas": ["limpiaparabrisas"], "guardabarro": ["guardafango"],
    "tijera": ["brazo"], "cauchos": ["bujes"], "filtro": ["filtros"],
}

# Almacenes y sucursales: (almacen, [(sucursal, municipio, lat, lng)])
STORES = [
    ("Repuestos La 80", [("Sede Bogotá Norte", "11001", 4.7110, -74.0721), ("Sede Bogotá Sur", "11001", 4.5981, -74.0760)]),
    ("Autopartes del Valle", [("Sede Cali Centro", "76001", 3.4516, -76.5320), ("Sede Palmira", "76520", 3.5394, -76.3036)]),
    ("Importadora Paisa", [("Sede Medellín", "05001", 6.2442, -75.5812), ("Sede Envigado", "05266", 6.1759, -75.5917)]),
    ("Frenos y Suspensión Caribe", [("Sede Barranquilla", "08001", 10.9685, -74.7813), ("Sede Cartagena", "13001", 10.3910, -75.4794)]),
]

MUNICIPALITIES = [
    ("11001", "11", "Bogotá, D.C.", True, 7900000), ("76001", "76", "Cali", True, 2200000),
    ("76520", "76", "Palmira", False, 310000), ("05001", "05", "Medellín", True, 2500000),
    ("05266", "05", "Envigado", False, 240000), ("08001", "08", "Barranquilla", True, 1280000),
    ("13001", "13", "Cartagena de Indias", True, 1000000), ("68001", "68", "Bucaramanga", True, 600000),
]
