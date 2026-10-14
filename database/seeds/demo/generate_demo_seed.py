# %% [markdown]
# # Autopartco: semilla de datos de demostración (B4)
#
# Este notebook descarga **datos abiertos reales de Colombia** y genera `demo_seed.sql`: un script idempotente, en una
# sola transacción, que puebla el **esquema migrado** de Autopartco (`database/init.sql`, después todas las `V*` de
# `database/migrations/` y luego las `R__`).
#
# | Conjunto (datos.gov.co, API Socrata pública) | Id | Uso | Licencia |
# |---|---|---|---|
# | Parque automotor de Risaralda | `x9pp-pcn5` | marcas, líneas, años y cilindrajes reales | CC BY-SA 4.0 |
# | Parque automotor activo de Boyacá (opcional) | `em3d-hmim` | complemento | CC BY-SA 4.0 |
# | DIVIPOLA del DANE | `gdxc-w37w` | municipios y coordenadas reales | CC BY-SA 4.0 |
#
# - **Privacidad (Ley 1581 de 2012):** el notebook nunca pide la placa. Solo hace consultas agregadas en el servidor
#   (`$select` + `$group` + `count(*)`).
# - **Sintético:** repuestos (referencias `APC-`, nunca números OEM), almacenes, personas, pedidos, pagos, navegación.
#   Las personas y correos (`@demo.autopartco.test`) son ficticios y **las cuentas no permiten iniciar sesión**.
# - **Determinismo:** semilla fija y UUID v5 con namespace fijo. Con la misma caché de datos, dos ejecuciones generan
#   el mismo archivo byte a byte. Las fechas son relativas a la medianoche de Bogotá del día en que se aplica el seed.
# - Este archivo (`generate_demo_seed.py`) es la fuente; el `.ipynb` se genera a partir de él (ver README).

# %%
# Parámetros (se pueden cambiar aquí o con variables de entorno APC_*)
import os
from pathlib import Path


def _env_int(name, default):
    return int(os.environ.get(name, default))


RANDOM_SEED = _env_int('APC_RANDOM_SEED', 20261014)
SEED_VERSION = 'B4-v2'

# Vehículos reales
N_CAR_LINES = _env_int('APC_N_CAR_LINES', 40)
N_MOTO_LINES = _env_int('APC_N_MOTO_LINES', 6)
MAX_VERSIONS_PER_MODEL = _env_int('APC_MAX_VERSIONS', 8)
MIN_UNITS_PER_VERSION = _env_int('APC_MIN_UNITS_PER_VERSION', 3)
MIN_YEAR = _env_int('APC_MIN_YEAR', 2005)
MAX_YEAR = _env_int('APC_MAX_YEAR', 2027)
INCLUDE_BOYACA = os.environ.get('APC_INCLUDE_BOYACA', '0') == '1'

# Volumen de actividad
N_BUYERS = _env_int('APC_N_BUYERS', 80)
N_ORDERS = _env_int('APC_N_ORDERS', 300)
HISTORY_DAYS = _env_int('APC_HISTORY_DAYS', 180)
SNAPSHOT_DAYS = _env_int('APC_SNAPSHOT_DAYS', 14)          # inventory_daily_snapshots (días hacia atrás)
N_ANON_SESSIONS = _env_int('APC_N_ANON_SESSIONS', 250)     # sesiones de visitantes sin cuenta
N_PURCHASE_ORDERS_PER_STORE = _env_int('APC_N_PO_PER_STORE', 6)

# 'DEMO' marca almacenes y pedidos como demostración. Las funciones analytics.refresh_* solo agregan
# data_origin = 'REAL'; usa 'REAL' únicamente en una base desechable si necesitas ver los hechos poblados.
DATA_ORIGIN = os.environ.get('APC_DATA_ORIGIN', 'DEMO')
assert DATA_ORIGIN in ('REAL', 'DEMO', 'SYNTHETIC')

CACHE_DIR = Path(os.environ.get('APC_CACHE_DIR', 'cache_datos_gov'))
OUT_PATH = Path(os.environ.get('APC_OUT_PATH', 'demo_seed.sql'))
OFFLINE = os.environ.get('APC_OFFLINE', '0') == '1'   # 1 = solo caché, sin red

SOCRATA_BASE = 'https://www.datos.gov.co'
DS_RISARALDA = 'x9pp-pcn5'
DS_BOYACA = 'em3d-hmim'
DS_DIVIPOLA = 'gdxc-w37w'

# %%
# Utilidades: semilla, UUID deterministas, dinero en Decimal y descarga con caché
import hashlib
import json
import math
import random
import re
import time
import unicodedata
import uuid
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from decimal import Decimal, ROUND_HALF_UP

UUID_NAMESPACE = uuid.uuid5(uuid.NAMESPACE_URL, 'urn:autopartco:seeds:demo:v1')
rng = random.Random(RANDOM_SEED)


def uid(kind, *parts):
    """UUID v5 estable: el mismo objeto lógico tiene el mismo id en cada ejecución."""
    return uuid.uuid5(UUID_NAMESPACE, kind + ':' + '|'.join(str(p) for p in parts))


def money(value):
    return Decimal(value).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)


def cost4(value):
    return Decimal(value).quantize(Decimal('0.0001'), rounding=ROUND_HALF_UP)


def round_cop(value, step=100):
    step = Decimal(step)
    return money((Decimal(value) / step).quantize(Decimal('1'), rounding=ROUND_HALF_UP) * step)


def pct(lo, hi):
    """Factor Decimal aleatorio entre lo y hi (milésimas)."""
    return Decimal(rng.randint(int(lo * 1000), int(hi * 1000))) / Decimal(1000)


def rand_hex(nbytes=32):
    """Hex pseudoaleatorio usado como 'hash' de un token que no existe (no tiene preimagen conocida)."""
    return ''.join(rng.choice('0123456789abcdef') for _ in range(nbytes * 2))


def strip_accents(text):
    return ''.join(c for c in unicodedata.normalize('NFKD', text) if not unicodedata.combining(c))


def norm(text):
    text = strip_accents(str(text or '')).upper()
    text = re.sub(r'[^A-Z0-9\-\./ ]+', ' ', text)
    return re.sub(r'\s+', ' ', text).strip()


def http_json(url, attempts=4):
    last = None
    for attempt in range(attempts):
        try:
            request = urllib.request.Request(url, headers={'User-Agent': 'autopartco-demo-seed/1.0',
                                                           'Accept': 'application/json'})
            with urllib.request.urlopen(request, timeout=180) as response:
                return json.loads(response.read().decode('utf-8'))
        except (urllib.error.URLError, TimeoutError) as error:
            last = error
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f'No se pudo descargar {url}: {last}')


def cached(name, producer):
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    path = CACHE_DIR / f'{name}.json'
    if path.exists():
        return json.loads(path.read_text(encoding='utf-8'))
    if OFFLINE:
        raise RuntimeError(f'APC_OFFLINE=1 y no existe la caché {path}')
    data = producer()
    path.write_text(json.dumps(data, ensure_ascii=False, sort_keys=True, indent=1), encoding='utf-8')
    return data


def dataset_meta(dataset_id):
    """Metadatos del conjunto (nombre, fecha de actualización, columnas). No descarga filas."""
    def producer():
        meta = http_json(f'{SOCRATA_BASE}/api/views/{dataset_id}.json')
        return {'id': dataset_id, 'name': meta.get('name'), 'rowsUpdatedAt': meta.get('rowsUpdatedAt'),
                'license': (meta.get('license') or {}).get('name') or meta.get('licenseId'),
                'columns': sorted(c.get('fieldName') for c in meta.get('columns', []) if c.get('fieldName'))}
    return cached(f'{dataset_id}.meta', producer)


def socrata_rows(dataset_id, params, page_size=50000):
    rows, offset = [], 0
    while True:
        query = dict(params, **{'$limit': page_size, '$offset': offset})
        page = http_json(f'{SOCRATA_BASE}/resource/{dataset_id}.json?' + urllib.parse.urlencode(query))
        rows.extend(page)
        if len(page) < page_size:
            return rows
        offset += page_size

# %%
# Parque automotor: descarga agregada en el servidor (sin placas)
VEHICLE_COLUMNS = {
    'clase': ['clase', 'clase_vehiculo', 'clase_de_vehiculo'],
    'marca': ['marca'],
    'linea': ['linea', 'l_nea', 'linea_vehiculo'],
    'modelo': ['modelo', 'a_o_modelo', 'anio_modelo', 'ano_modelo'],
    'cilindraje': ['cilindraje', 'cilindrada'],
}


def vehicle_aggregate(dataset_id):
    meta = dataset_meta(dataset_id)
    available = set(meta['columns'])
    mapping = {}
    for key, candidates in VEHICLE_COLUMNS.items():
        found = next((c for c in candidates if c in available), None)
        if found is None:
            raise RuntimeError(f'{dataset_id}: no se encontró la columna {key}; columnas: {sorted(available)}')
        mapping[key] = found
    assert all('placa' not in column for column in mapping.values()), 'Nunca se consulta la placa'

    def producer():
        group = ','.join(mapping[k] for k in VEHICLE_COLUMNS)
        select = ','.join(f'{mapping[k]} AS {k}' for k in VEHICLE_COLUMNS) + ',count(*) AS n'
        return socrata_rows(dataset_id, {'$select': select, '$group': group, '$order': group})
    rows = cached(f'{dataset_id}.agg', producer)
    for row in rows:
        assert set(row) <= set(VEHICLE_COLUMNS) | {'n'}, 'La respuesta agregada solo trae las columnas pedidas'
    return meta, rows


risaralda_meta, vehicle_rows = vehicle_aggregate(DS_RISARALDA)
SOURCES = [risaralda_meta]
if INCLUDE_BOYACA:
    boyaca_meta, boyaca_rows = vehicle_aggregate(DS_BOYACA)
    vehicle_rows = vehicle_rows + boyaca_rows
    SOURCES.append(boyaca_meta)
print(f'Grupos agregados: {len(vehicle_rows)}; vehículos representados: '
      f'{sum(int(float(r.get("n", 0))) for r in vehicle_rows):,}')

# %%
# Normalización de marcas y líneas; selección de las líneas más comunes
KIND_BY_CLASS = {'AUTOMOVIL': 'car', 'CAMIONETA': 'suv', 'CAMPERO': 'suv', 'MOTOCICLETA': 'moto'}
BAD_VALUES = {'', 'SIN LINEA', 'SIN INFORMACION', 'NO REGISTRA', 'NO APLICA', 'OTRA', 'OTRO', 'OTRAS', 'N/A', 'NA',
              'SIN MARCA', 'NINGUNA', 'DESCONOCIDA', 'SIN DATO'}
MAKE_ALIASES = {'AKT MOTOS': 'AKT', 'AUTECO KAWASAKI': 'KAWASAKI', 'MERCEDES-BENZ': 'MERCEDES BENZ',
                'MERCEDEZ BENZ': 'MERCEDES BENZ', 'GENERAL MOTORS': 'CHEVROLET', 'GM': 'CHEVROLET',
                'GENERAL MOTORS CHEVROLET': 'CHEVROLET', 'CHEVROLET GM': 'CHEVROLET', 'VOLKSWAGEN VW': 'VOLKSWAGEN',
                'VW': 'VOLKSWAGEN', 'SSANG YONG': 'SSANGYONG', 'HERO MOTOCORP': 'HERO', 'BAJAJ AUTO': 'BAJAJ'}
MAKE_DISPLAY = {'MERCEDES BENZ': 'Mercedes-Benz', 'KIA': 'Kia', 'BMW': 'BMW', 'AKT': 'AKT', 'KTM': 'KTM', 'TVS': 'TVS',
                'BYD': 'BYD', 'JAC': 'JAC', 'DFSK': 'DFSK', 'MG': 'MG', 'SSANGYONG': 'SsangYong', 'JMC': 'JMC',
                'GAC': 'GAC', 'UM': 'UM', 'SYM': 'SYM', 'CFMOTO': 'CFMoto'}
TWO_WORD_PREFIXES = {'GRAND', 'GRAN', 'LAND', 'RANGE', 'SANTA', 'NEW', 'ALTO', 'XL'}
WORD_DISPLAY = {'GOL': 'Gol', 'RIO': 'Rio', 'UNO': 'Uno', 'KA': 'Ka', 'ONE': 'One', 'ION': 'Ion', 'FIT': 'Fit', 'D-MAX': 'D-Max'}


def display_word(word):
    if word in WORD_DISPLAY:
        return WORD_DISPLAY[word]
    if any(ch.isdigit() for ch in word) or len(word) <= 3:
        return word
    if '-' in word:
        return '-'.join(display_word(part) for part in word.split('-'))
    return word.capitalize()


def display_make(make):
    return MAKE_DISPLAY.get(make, ' '.join(w.capitalize() if len(w) > 3 else w for w in make.split()))


def model_family(make, linea, kind):
    tokens = linea.split()
    make_tokens = set(make.split())
    while tokens and (tokens[0] in make_tokens or tokens[0] in {'NUEVO', 'NUEVA'}):
        tokens.pop(0)
    if not tokens:
        return None
    if kind == 'moto':
        family = tokens[:2]
    elif tokens[0] in TWO_WORD_PREFIXES and len(tokens) > 1:
        family = tokens[:2]
    else:
        family = tokens[:1]
    words = [display_word(w) for w in family]
    if len(words) == 1 and words[0].isdigit():
        return display_make(make) + ' ' + words[0]          # "MAZDA 3" -> "Mazda 3"
    return ' '.join(words)


def round_cc(cc, kind):
    step = 5 if kind == 'moto' else 100
    return int((Decimal(cc) / step).quantize(Decimal('1'), rounding=ROUND_HALF_UP)) * step


def version_label(year, cc, kind):
    return f'{year} {cc} cc' if kind == 'moto' else f'{year} {cc // 1000}.{(cc % 1000) // 100} L'


families, discarded = {}, Counter()
for row in vehicle_rows:
    kind = KIND_BY_CLASS.get(norm(row.get('clase')))
    if kind is None:
        discarded['clase'] += 1
        continue
    make = MAKE_ALIASES.get(norm(row.get('marca')), norm(row.get('marca')))
    linea = norm(row.get('linea'))
    if make in BAD_VALUES or linea in BAD_VALUES or linea.startswith('SIN ') or make.startswith('SIN '):
        discarded['sin_dato'] += 1
        continue
    try:
        year = int(float(str(row.get('modelo')).replace(',', '.')))
        cc = int(float(str(row.get('cilindraje')).replace(',', '.')))
        units = int(float(row.get('n', 0)))
    except (TypeError, ValueError):
        discarded['numero'] += 1
        continue
    low, high = (50, 1500) if kind == 'moto' else (600, 8000)
    if not (MIN_YEAR <= year <= MAX_YEAR and low <= cc <= high and units > 0):
        discarded['rango'] += 1
        continue
    family = model_family(make, linea, kind)
    if not family:
        discarded['linea'] += 1
        continue
    key = ('moto' if kind == 'moto' else 'auto', make, family)
    entry = families.setdefault(key, {'units': 0, 'kinds': Counter(), 'versions': Counter(), 'cc': Counter()})
    entry['units'] += units
    entry['kinds'][kind] += units
    entry['versions'][(year, round_cc(cc, kind))] += units
    entry['cc'][round_cc(cc, kind)] += units


def top_families(group, limit):
    candidates = [(key, value) for key, value in families.items() if key[0] == group]
    candidates.sort(key=lambda item: (-item[1]['units'], item[0]))
    return candidates[:limit]


def weighted_median(counter):
    total, acc = sum(counter.values()), 0
    for value in sorted(counter):
        acc += counter[value]
        if acc * 2 >= total:
            return value
    return 0


def segment_for(kind, cc):
    if kind == 'moto':
        return 'MOTO_S' if cc <= 200 else 'MOTO_L'
    if kind == 'car':
        return 'CAR_S' if cc <= 1400 else ('CAR_M' if cc <= 2000 else 'CAR_L')
    return 'SUV_S' if cc <= 2000 else 'SUV_L'


MODELS = []
for (group, make, family), value in top_families('auto', N_CAR_LINES) + top_families('moto', N_MOTO_LINES):
    if group == 'moto':
        kind = 'moto'
    else:
        kind = 'car' if value['kinds'].get('car', 0) >= value['kinds'].get('suv', 0) else 'suv'
    versions = [(vk, n) for vk, n in value['versions'].items() if n >= MIN_UNITS_PER_VERSION]
    versions.sort(key=lambda item: (-item[1], item[0]))
    versions = sorted(versions[:MAX_VERSIONS_PER_MODEL], key=lambda item: item[0])
    if not versions:
        continue
    MODELS.append({'make': display_make(make), 'name': family, 'kind': kind, 'units': value['units'],
                   'segment': segment_for(kind, weighted_median(value['cc'])),
                   'versions': [(version_label(y, c, kind), y, c) for (y, c), _ in versions]})
MODELS.sort(key=lambda m: (m['make'], m['name']))
MAKES = sorted({m['make'] for m in MODELS})
print(f'Descartados: {dict(discarded)}')
print(f'Marcas: {len(MAKES)} | líneas: {len(MODELS)} | versiones: {sum(len(m["versions"]) for m in MODELS)}')
for m in sorted(MODELS, key=lambda m: -m['units']):
    print(f"  {m['make']:<14} {m['name']:<18} {m['kind']:<5} {m['segment']:<7} {m['units']:>7,}  "
          f"{', '.join(v[0] for v in m['versions'][:3])}…")

# %%
# Geografía real: DIVIPOLA (DANE)
STORE_MUNICIPALITIES = ['11001', '25754', '05001', '05266', '76001', '66001', '66170']
BUYER_MUNICIPALITIES = ['11001', '05001', '76001', '66001', '08001', '68001', '17001', '13001', '73001', '15001',
                        '63001', '54001', '52001', '05360', '25175', '76520', '66170']
SUPPLIER_MUNICIPALITIES = ['11001', '05001', '76001', '66001']
ALL_MUNICIPALITIES = sorted(set(STORE_MUNICIPALITIES + BUYER_MUNICIPALITIES + SUPPLIER_MUNICIPALITIES))
SMALL_WORDS = {'DE', 'DEL', 'LA', 'LAS', 'LOS', 'Y', 'EL'}


def place_name(text):
    words = []
    for index, word in enumerate(str(text).strip().split()):
        if index > 0 and word.upper() in SMALL_WORDS:
            words.append(word.lower())
        elif word.upper() in {'D.C.', 'DC'}:
            words.append('D.C.')
        else:
            words.append(word[:1].upper() + word[1:].lower())
    return ' '.join(words)


def parse_coord(value):
    return Decimal(str(value).strip().replace(',', '.')).quantize(Decimal('0.0000001'), rounding=ROUND_HALF_UP)


divipola_meta = dataset_meta(DS_DIVIPOLA)
SOURCES.append(divipola_meta)
divipola_rows = cached(f'{DS_DIVIPOLA}.rows', lambda: socrata_rows(DS_DIVIPOLA, {
    '$select': 'cod_dpto,dpto,cod_mpio,nom_mpio,longitud,latitud', '$order': 'cod_mpio'}, page_size=5000))
MUNICIPALITIES = {}
for row in divipola_rows:
    code = str(row.get('cod_mpio', '')).strip().split('.')[0].zfill(5)
    if code in ALL_MUNICIPALITIES:
        lat, lon = parse_coord(row['latitud']), parse_coord(row['longitud'])
        assert Decimal(-5) < lat < Decimal(14) and Decimal(-82) < lon < Decimal(-66), f'Fuera de Colombia: {row}'
        MUNICIPALITIES[code] = {'code': code, 'dept_code': code[:2], 'city': place_name(row['nom_mpio']),
                                'department': place_name(row['dpto']), 'lat': lat, 'lon': lon}
missing = sorted(set(ALL_MUNICIPALITIES) - set(MUNICIPALITIES))
assert not missing, f'Municipios no encontrados en DIVIPOLA: {missing}'


def distance_km(a, b):
    """Distancia ortodrómica entre centroides (solo para shipments.distance_km)."""
    la1, lo1, la2, lo2 = (math.radians(float(x)) for x in (a['lat'], a['lon'], b['lat'], b['lon']))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    km = 2 * 6371.0 * math.asin(math.sqrt(h))
    return Decimal(f'{km:.2f}')   # se convierte a Decimal antes de llegar al SQL

# %%
# Taxonomía curada de repuestos (sintética), fabricantes reales y especificaciones
CATEGORIES = ['FRENOS', 'SUSPENSION_Y_DIRECCION', 'MOTOR_Y_DISTRIBUCION', 'FILTROS_Y_LUBRICACION',
              'ELECTRICO_Y_ENCENDIDO', 'REFRIGERACION', 'EMBRAGUE_Y_TRANSMISION', 'ESCAPE']
CAT_ABBR = {'FRENOS': 'FRE', 'SUSPENSION_Y_DIRECCION': 'SUS', 'MOTOR_Y_DISTRIBUCION': 'MOT',
            'FILTROS_Y_LUBRICACION': 'FIL', 'ELECTRICO_Y_ENCENDIDO': 'ELE', 'REFRIGERACION': 'REF',
            'EMBRAGUE_Y_TRANSMISION': 'EMB', 'ESCAPE': 'ESC'}
AUTO = ['CAR_S', 'CAR_M', 'CAR_L', 'SUV_S', 'SUV_L']
CARS_SUV_S = ['CAR_S', 'CAR_M', 'CAR_L', 'SUV_S']
MOTO = ['MOTO_S', 'MOTO_L']
SPECIFICATIONS = ['Posición', 'Material', 'Tipo', 'Lado', 'Diámetro', 'Longitud', 'Contenido', 'Viscosidad', 'Norma',
                  'Voltaje', 'Corriente', 'Capacidad', 'Unidades por paquete', 'Canales', 'Incluye', 'Paso de cadena']
UNITS = [('Milímetro', 'mm'), ('Mililitro', 'ml'), ('Litro', 'L'), ('Voltio', 'V'), ('Amperio', 'A'),
         ('Amperio hora', 'Ah'), ('Unidad', 'und')]
UNIT_SPECS = [('Diámetro', 'mm'), ('Longitud', 'mm'), ('Contenido', 'ml'), ('Contenido', 'L'), ('Voltaje', 'V'),
              ('Corriente', 'A'), ('Capacidad', 'Ah'), ('Unidades por paquete', 'und')]


def a(spec, values, unit=None):
    return (spec, values, unit)


PART_TYPES = [
    # código, nombre, categoría, segmentos, fabricantes, rango de precio COP, universal, atributos
    ('PAST_DEL', 'Pastillas de freno delanteras', 'FRENOS', AUTO, ['Bosch', 'Incolbestos', 'Brembo', 'TRW'], (60000, 180000), False,
     [a('Posición', ['Delantera']), a('Material', ['Semimetálica', 'Cerámica', 'Orgánica']), a('Unidades por paquete', ['4'], 'und')]),
    ('PAST_TRAS', 'Pastillas de freno traseras', 'FRENOS', ['CAR_M', 'CAR_L', 'SUV_S', 'SUV_L'], ['Bosch', 'Brembo', 'TRW'], (55000, 150000), False,
     [a('Posición', ['Trasera']), a('Material', ['Semimetálica', 'Cerámica']), a('Unidades por paquete', ['4'], 'und')]),
    ('BANDAS', 'Bandas de freno traseras', 'FRENOS', ['CAR_S', 'CAR_M', 'SUV_S'], ['Incolbestos', 'Bosch', 'TRW'], (50000, 120000), False,
     [a('Posición', ['Trasera']), a('Diámetro', ['180', '200', '203', '220', '228'], 'mm')]),
    ('DISCOS', 'Discos de freno delanteros (par)', 'FRENOS', AUTO, ['Bosch', 'Brembo', 'TRW'], (180000, 520000), False,
     [a('Posición', ['Delantera']), a('Tipo', ['Ventilado', 'Sólido']), a('Diámetro', ['238', '256', '260', '280', '296'], 'mm')]),
    ('LIQ_FRENOS', 'Líquido de frenos DOT 4', 'FRENOS', [], ['Bosch', 'Motul', 'Castrol'], (22000, 45000), True,
     [a('Norma', ['DOT 4']), a('Contenido', ['500'], 'ml')]),
    ('AMORT_DEL', 'Amortiguador delantero', 'SUSPENSION_Y_DIRECCION', AUTO, ['Monroe', 'Gabriel', 'Sachs', 'KYB'], (150000, 420000), False,
     [a('Posición', ['Delantera']), a('Tipo', ['Gas', 'Hidráulico'])]),
    ('AMORT_TRAS', 'Amortiguador trasero', 'SUSPENSION_Y_DIRECCION', AUTO, ['Monroe', 'Gabriel', 'Sachs', 'KYB'], (120000, 360000), False,
     [a('Posición', ['Trasera']), a('Tipo', ['Gas', 'Hidráulico'])]),
    ('TERMINAL', 'Terminal de dirección', 'SUSPENSION_Y_DIRECCION', AUTO, ['Moog', 'TRW'], (45000, 120000), False,
     [a('Lado', ['Izquierdo', 'Derecho'])]),
    ('ROTULA', 'Rótula de suspensión inferior', 'SUSPENSION_Y_DIRECCION', AUTO, ['Moog', 'TRW'], (60000, 160000), False,
     [a('Posición', ['Inferior'])]),
    ('BUJE', 'Buje de tijera delantero', 'SUSPENSION_Y_DIRECCION', ['CAR_S', 'CAR_M', 'SUV_S'], ['Moog', 'TRW'], (25000, 70000), False,
     [a('Material', ['Caucho', 'Poliuretano'])]),
    ('KIT_DIST', 'Kit de distribución', 'MOTOR_Y_DISTRIBUCION', CARS_SUV_S, ['Gates', 'Dayco', 'Continental'], (180000, 480000), False,
     [a('Incluye', ['Correa y tensor', 'Correa, tensor y bomba de agua'])]),
    ('BOMBA_AGUA', 'Bomba de agua', 'MOTOR_Y_DISTRIBUCION', AUTO, ['Gates', 'Aisin', 'Bosch'], (120000, 320000), False,
     [a('Material', ['Aluminio', 'Hierro'])]),
    ('CORREA_ACC', 'Correa de accesorios', 'MOTOR_Y_DISTRIBUCION', AUTO, ['Gates', 'Dayco', 'Continental'], (40000, 110000), False,
     [a('Canales', ['4', '5', '6', '7']), a('Longitud', ['880', '1020', '1195', '1370', '1555'], 'mm')]),
    ('EMPAQUE_TAPA', 'Empaque de tapa de válvulas', 'MOTOR_Y_DISTRIBUCION', AUTO, ['Victor Reinz', 'Elring'], (35000, 110000), False,
     [a('Material', ['Caucho', 'Corcho'])]),
    ('FILTRO_ACEITE', 'Filtro de aceite', 'FILTROS_Y_LUBRICACION', AUTO, ['Mann-Filter', 'Bosch', 'Mahle', 'Wix'], (15000, 45000), False,
     [a('Tipo', ['Roscado', 'Cartucho'])]),
    ('FILTRO_AIRE', 'Filtro de aire de motor', 'FILTROS_Y_LUBRICACION', AUTO, ['Mann-Filter', 'Bosch', 'Mahle', 'Wix'], (30000, 95000), False,
     [a('Tipo', ['Panel', 'Redondo'])]),
    ('FILTRO_COMB', 'Filtro de combustible', 'FILTROS_Y_LUBRICACION', AUTO, ['Mann-Filter', 'Bosch', 'Mahle'], (25000, 90000), False,
     [a('Tipo', ['En línea', 'Cartucho'])]),
    ('FILTRO_CABINA', 'Filtro de cabina', 'FILTROS_Y_LUBRICACION', AUTO, ['Mann-Filter', 'Bosch', 'Mahle'], (28000, 80000), False,
     [a('Tipo', ['Estándar', 'Carbón activado'])]),
    ('ACEITE_1030', 'Aceite de motor 10W-30 semisintético (cuarto)', 'FILTROS_Y_LUBRICACION', [], ['Mobil', 'Castrol', 'Terpel', 'Motul'], (32000, 55000), True,
     [a('Viscosidad', ['10W-30']), a('Tipo', ['Semisintético']), a('Contenido', ['946'], 'ml')]),
    ('ACEITE_530', 'Aceite de motor 5W-30 sintético (galón)', 'FILTROS_Y_LUBRICACION', [], ['Mobil', 'Castrol', 'Motul'], (150000, 230000), True,
     [a('Viscosidad', ['5W-30']), a('Tipo', ['Sintético']), a('Contenido', ['3.78'], 'L')]),
    ('BUJIAS', 'Bujías (juego x4)', 'ELECTRICO_Y_ENCENDIDO', CARS_SUV_S, ['NGK', 'Bosch', 'Denso'], (45000, 190000), False,
     [a('Material', ['Níquel', 'Platino', 'Iridio']), a('Unidades por paquete', ['4'], 'und')]),
    ('BOBINA', 'Bobina de encendido', 'ELECTRICO_Y_ENCENDIDO', AUTO, ['Bosch', 'Denso', 'NGK'], (140000, 380000), False, []),
    ('BATERIA', 'Batería 12 V', 'ELECTRICO_Y_ENCENDIDO', AUTO, ['MAC', 'Bosch', 'Willard'], (280000, 650000), False,
     [a('Voltaje', ['12'], 'V'), a('Capacidad', ['45', '55', '65', '75', '90'], 'Ah')]),
    ('ALTERNADOR', 'Alternador', 'ELECTRICO_Y_ENCENDIDO', AUTO, ['Valeo', 'Denso', 'Bosch'], (650000, 1500000), False,
     [a('Voltaje', ['12'], 'V'), a('Corriente', ['70', '90', '110', '120'], 'A')]),
    ('SENSOR_O2', 'Sensor de oxígeno', 'ELECTRICO_Y_ENCENDIDO', AUTO, ['Bosch', 'NGK', 'Denso'], (150000, 420000), False,
     [a('Posición', ['Antes del catalizador', 'Después del catalizador'])]),
    ('TERMOSTATO', 'Termostato', 'REFRIGERACION', AUTO, ['Gates', 'Wahler', 'Mahle'], (45000, 140000), False, []),
    ('RADIADOR', 'Radiador', 'REFRIGERACION', AUTO, ['Mahle', 'Valeo', 'Nissens'], (380000, 1100000), False,
     [a('Material', ['Aluminio', 'Aluminio y plástico'])]),
    ('REFRIGERANTE', 'Refrigerante listo para usar (galón)', 'REFRIGERACION', [], ['Prestone', 'Valvoline'], (35000, 75000), True,
     [a('Contenido', ['3.78'], 'L')]),
    ('KIT_EMBRAGUE', 'Kit de embrague', 'EMBRAGUE_Y_TRANSMISION', AUTO, ['LuK', 'Valeo', 'Sachs', 'Exedy'], (380000, 1200000), False,
     [a('Diámetro', ['180', '190', '200', '215', '225', '240'], 'mm')]),
    ('COLLARIN', 'Collarín de embrague', 'EMBRAGUE_Y_TRANSMISION', AUTO, ['LuK', 'Valeo', 'Sachs'], (70000, 220000), False, []),
    ('SILENCIADOR', 'Silenciador trasero', 'ESCAPE', ['CAR_S', 'CAR_M', 'SUV_S'], ['Walker', 'Bosal'], (160000, 480000), False,
     [a('Material', ['Acero aluminizado', 'Acero inoxidable'])]),
    ('KIT_ARRASTRE', 'Kit de arrastre (cadena, piñón y corona)', 'EMBRAGUE_Y_TRANSMISION', MOTO, ['D.I.D', 'JT Sprockets', 'Riffel'], (90000, 320000), False,
     [a('Paso de cadena', ['428', '520'])]),
    ('PAST_MOTO', 'Pastillas de freno para moto', 'FRENOS', MOTO, ['Brembo', 'EBC'], (25000, 90000), False,
     [a('Posición', ['Delantera', 'Trasera'])]),
    ('BUJIA_MOTO', 'Bujía para moto', 'ELECTRICO_Y_ENCENDIDO', MOTO, ['NGK', 'Denso'], (12000, 45000), False,
     [a('Material', ['Níquel', 'Iridio']), a('Unidades por paquete', ['1'], 'und')]),
    ('FILTRO_ACEITE_MOTO', 'Filtro de aceite para moto', 'FILTROS_Y_LUBRICACION', MOTO, ['Hiflofiltro', 'Mahle'], (12000, 35000), False, []),
    ('ACEITE_4T', 'Aceite para moto 4T 20W-50 (litro)', 'FILTROS_Y_LUBRICACION', [], ['Motul', 'Mobil', 'Castrol'], (28000, 60000), True,
     [a('Viscosidad', ['20W-50']), a('Contenido', ['1'], 'L')]),
]
MANUFACTURERS = sorted({m for t in PART_TYPES for m in t[4]})
assert {s for t in PART_TYPES for s, _, _ in t[7]} <= set(SPECIFICATIONS)
VAT_RATE = Decimal('0.1900')   # IVA general de Colombia; los precios de lista lo incluyen

# Almacenes (ficticios) en municipios reales
STORES = [
    # código, nombre, estado del almacén, estado de la solicitud, [(sucursal, municipio, dirección)]
    ('BOG', 'Repuestos Andinos (demo)', 'ACTIVE', 'ACTIVE',
     [('Sede Ricaurte', '11001', 'Calle 13 # 27-41'), ('Sede Soacha', '25754', 'Carrera 7 # 15-20')]),
    ('MED', 'Autopartes La Montaña (demo)', 'ACTIVE', 'ACTIVE',
     [('Sede Centro', '05001', 'Carrera 52 # 45-18'), ('Sede Envigado', '05266', 'Calle 38 Sur # 43-12')]),
    ('CLI', 'Frenos y Partes del Pacífico (demo)', 'ACTIVE', 'ACTIVE',
     [('Sede San Nicolás', '76001', 'Calle 21 # 8-33')]),
    ('PEI', 'Repuestos Cafeteros (demo)', 'ACTIVE', 'ACTIVE',
     [('Sede Centro', '66001', 'Carrera 10 # 23-56'), ('Sede Dosquebradas', '66170', 'Avenida Simón Bolívar # 40-11')]),
    # Solicitudes que todavía no operan: sin sucursales, productos ni ventas
    ('BAQ', 'Motopartes del Caribe (demo)', 'PENDING', 'PENDING_REVIEW', []),
    ('BGA', 'Lubricentro Oriente (demo)', 'PENDING', 'CONFIGURATION_PENDING', []),
    ('CTG', 'Repuestos La Muralla (demo)', 'PENDING', 'REJECTED', []),
]
ACTIVE_STORES = [s for s in STORES if s[3] == 'ACTIVE']
STORE_HOME = {'BOG': '11001', 'MED': '05001', 'CLI': '76001', 'PEI': '66001'}
DEMO_PLANS = [
    # código, nombre, precio mensual COP, sucursales, productos, usuarios, capacidades (como PlanService)
    ('DEMO_BUSINESS', 'Negocio (demo)', 149900, 5, 5000, 10,
     ['BASIC_ANALYTICS', 'ADVANCED_REPORTS', 'PROMOTIONS', 'CSV_IMPORT', 'WHATSAPP_NOTIFICATIONS', 'INTEGRATIONS', 'CUSTOM_ROLES']),
    ('DEMO_ENTERPRISE', 'Empresa (demo)', 399900, None, None, None,
     ['BASIC_ANALYTICS', 'ADVANCED_REPORTS', 'PROMOTIONS', 'CSV_IMPORT', 'WHATSAPP_NOTIFICATIONS', 'INTEGRATIONS', 'CUSTOM_ROLES',
      'API_ACCESS', 'WEBHOOKS', 'ERP_INTEGRATIONS', 'CUSTOM_DOMAIN']),
]
ALL_PERMISSIONS = ['STORE_READ', 'STORE_UPDATE', 'BRANCH_READ', 'BRANCH_CREATE', 'BRANCH_UPDATE', 'BRANCH_DELETE',
                   'PRODUCT_READ', 'PRODUCT_CREATE', 'PRODUCT_UPDATE', 'PRODUCT_PUBLISH', 'PRODUCT_DELETE',
                   'PRODUCT_IMAGE_MANAGE', 'PROMOTION_READ', 'PROMOTION_CREATE', 'PROMOTION_UPDATE', 'PROMOTION_DELETE',
                   'ORDER_READ', 'ORDER_UPDATE', 'SHIPMENT_UPDATE', 'COLLABORATOR_READ', 'COLLABORATOR_MANAGE']
ROLE_DEFS = [
    ('OWNER', 'Propietario', ALL_PERMISSIONS),
    ('VENDEDOR', 'Vendedor', ['STORE_READ', 'BRANCH_READ', 'PRODUCT_READ', 'PRODUCT_CREATE', 'PRODUCT_UPDATE',
                              'PRODUCT_PUBLISH', 'PRODUCT_IMAGE_MANAGE', 'PROMOTION_READ', 'ORDER_READ', 'ORDER_UPDATE']),
    ('BODEGA', 'Bodega y despachos', ['STORE_READ', 'BRANCH_READ', 'PRODUCT_READ', 'ORDER_READ', 'SHIPMENT_UPDATE']),
]
SUPPLIER_NAMES = ['Distribuidora Andina de Autopartes (demo)', 'Importadora Repuestos del Centro (demo)',
                  'Comercializadora Motor Sur (demo)']

# %%
# Modelo de filas. Las FK a catálogos se resuelven en SQL por clave natural (funciones pg_temp.demo_*)
DAY, HOUR, MINUTE = 86400, 3600, 60
NOW_MARGIN = -10 * MINUTE   # el ancla es la medianoche de Bogotá de hoy; todo ocurre antes


class Raw:
    """Expresión SQL literal (llamada a una función pg_temp.demo_*)."""
    def __init__(self, sql):
        self.sql = sql


def sql_str(text):
    return "'" + str(text).replace("'", "''") + "'"


def ref(kind, *keys):
    return Raw(f"pg_temp.demo_{kind}({', '.join(sql_str(k) for k in keys)})")


def ts(seconds):
    """TIMESTAMP heredado (UTC) relativo al ancla. Para columnas TIMESTAMP de init.sql."""
    return Raw(f'pg_temp.demo_ts({int(seconds)})')


def tz(seconds):
    """TIMESTAMPTZ relativo al ancla. Para las columnas nuevas de las migraciones."""
    return Raw(f'pg_temp.demo_tstz({int(seconds)})')


def day(days_from_today):
    """DATE de Bogotá relativo a hoy (negativo = pasado)."""
    return Raw(f'pg_temp.demo_date({int(days_from_today)})')


ROWS = defaultdict(list)


def add(table, **row):
    ROWS[table].append(row)
    return row


# Catálogos de referencia
for code in CATEGORIES:
    add('categories', id=uid('category', code), code=code)
for name in MANUFACTURERS:
    add('manufacturers', id=uid('manufacturer', name), name=name)
for name in SPECIFICATIONS:
    add('specifications', id=uid('specification', name), name=name)
for name, code in UNITS:
    add('units', id=uid('unit', code), name=name, code=code)
for spec, unit in UNIT_SPECS:
    add('units_specifications', id=uid('unit_spec', spec, unit), specification_id=ref('specification', spec),
        unit_id=ref('unit', unit))
for code in ALL_MUNICIPALITIES:
    m = MUNICIPALITIES[code]
    add('geo_municipalities', code=code, department_code=m['dept_code'], name=m['city'],
        is_department_capital=code.endswith('001'), latitude=m['lat'], longitude=m['lon'])

# Vehículos reales
for make in MAKES:
    add('make', id=uid('make', make), name=make)
for model in MODELS:
    add('model', id=uid('model', model['make'], model['name']), make_id=ref('make', model['make']), name=model['name'])
    for label, year, cc in model['versions']:
        add('versions', id=uid('version', model['make'], model['name'], label),
            model_id=ref('model', model['make'], model['name']), version=label, year_from=year, year_to=year,
            displacement_cc=cc)
        add('compatibilities', id=uid('compat', model['make'], model['name'], label),
            make_id=ref('make', model['make']), model_id=ref('model', model['make'], model['name']),
            version_id=ref('version', model['make'], model['name'], label))


def add_location(key, code, address, complement, created):
    m = MUNICIPALITIES[code]
    location_id = uid('location', key)
    add('locations', id=location_id, address_line=address, address_complement=complement, city=m['city'],
        department=m['department'], country='Colombia', postal_code=None, latitude=m['lat'], longitude=m['lon'],
        place_id=None, provider='INTERNAL', municipality_code=code,
        metadata={'demo': True, 'fuente': f'DANE DIVIPOLA ({DS_DIVIPOLA})', 'coordenadas': 'centroide del municipio'},
        created_at=ts(created), updated_at=ts(created))
    return location_id

# %%
# Personas, cuentas y privacidad (todo ficticio; ninguna cuenta puede iniciar sesión)
NO_LOGIN_PASSWORD = '!demo-sin-acceso! hash BCrypt invalido a proposito: esta cuenta no permite iniciar sesion'
FIRST_NAMES = ['Andrés', 'Camila', 'Juan', 'Valentina', 'Santiago', 'Mariana', 'Carlos', 'Laura', 'Felipe', 'Daniela',
               'Jorge', 'Paula', 'Luis', 'Natalia', 'Diego', 'Juliana', 'Sebastián', 'Catalina', 'Mateo', 'Sofía',
               'Óscar', 'Ana', 'Julián', 'Carolina', 'Esteban', 'Diana', 'Alejandro', 'Manuela', 'Nicolás', 'Lina']
SURNAMES = ['Gómez', 'Rodríguez', 'Martínez', 'López', 'García', 'Hernández', 'Ramírez', 'Torres', 'Díaz', 'Moreno',
            'Rojas', 'Vargas', 'Castro', 'Ortiz', 'Jiménez', 'Muñoz', 'Suárez', 'Restrepo', 'Ospina', 'Cardona',
            'Quintero', 'Zapata', 'Valencia', 'Arango', 'Mejía', 'Pineda', 'Salazar', 'Ríos', 'Londoño', 'Giraldo']
VIAS = ['Calle', 'Carrera', 'Transversal', 'Diagonal', 'Avenida Calle', 'Avenida Carrera']
USER_AGENTS = ['Mozilla/5.0 (Windows NT 10.0; Win64; x64) Demo', 'Mozilla/5.0 (Linux; Android 14) Demo',
               'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0) Demo']
PEOPLE = {}
person_seq = 0


def fake_phone():
    return '000' + ''.join(rng.choice('0123456789') for _ in range(7))   # 000…: no es un número colombiano válido


def doc_ip():
    return f'{rng.choice(["192.0.2", "198.51.100", "203.0.113"])}.{rng.randint(1, 254)}'   # RFC 5737


def add_person(key, email_local, account_type, state='ACTIVE', created=-HISTORY_DAYS * DAY):
    global person_seq
    person_seq += 1
    person_id, account_id = uid('person', key), uid('account', key)
    name, surname = rng.choice(FIRST_NAMES), rng.choice(SURNAMES) + ' ' + rng.choice(SURNAMES)
    add('persons', id=person_id, name=name, surname=surname, identification=f'DEMO-{person_seq:06d}',
        date_birth=f'{rng.randint(1962, 2004)}-{rng.randint(1, 12):02d}-{rng.randint(1, 28):02d}',
        number_phone=fake_phone(), created_at=ts(created), updated_at=ts(created))
    email = f'{email_local}@demo.autopartco.test'
    add('accounts', id=account_id, id_person=person_id, email=email, password=NO_LOGIN_PASSWORD,
        type_id=ref('account_type', account_type), state_id=ref('state', state),
        configuration={'demo': True, 'login': 'deshabilitado'}, created_at=ts(created), updated_at=ts(created))
    PEOPLE[key] = {'key': key, 'person_id': person_id, 'account_id': account_id, 'email': email, 'state': state,
                   'created': created, 'type': account_type, 'subject_key': uid('subject', key)}
    return PEOPLE[key]


add_person('root', 'root', 'ROOT', created=-(HISTORY_DAYS + 60) * DAY)
for code, _, _, app_status, _ in STORES:
    active = app_status == 'ACTIVE'
    add_person(f'owner:{code}', f'propietario.{code.lower()}', 'STORE_MANAGER' if active else 'CUSTOMER',
               created=-(HISTORY_DAYS + rng.randint(20, 50)) * DAY if active else -rng.randint(3, 25) * DAY)
STAFF = defaultdict(list)
for code, *_ in ACTIVE_STORES:
    for n in range(1, rng.randint(2, 3) + 1):
        key = f'staff:{code}:{n}'
        add_person(key, f'equipo{n}.{code.lower()}', 'STORE_MANAGER', created=-(HISTORY_DAYS - rng.randint(0, 60)) * DAY)
        STAFF[code].append(key)
BUYERS = []
for n in range(1, N_BUYERS + 1):
    state = 'PENDING' if n % 37 == 0 else ('LOCKED' if n % 41 == 0 else 'ACTIVE')
    key = f'buyer:{n}'
    add_person(key, f'comprador{n:03d}', 'CUSTOMER', state=state,
               created=-rng.randint(5, HISTORY_DAYS + 30) * DAY - rng.randint(0, DAY - 1))
    BUYERS.append(key)

BUYER_ADDRESS = {}
for key in BUYERS:
    person = PEOPLE[key]
    for index in range(rng.choice([1, 1, 2])):
        code = rng.choice(BUYER_MUNICIPALITIES)
        address = f'{rng.choice(VIAS)} {rng.randint(1, 120)}{rng.choice(["", "", "A", "B"])} # {rng.randint(1, 99)}-{rng.randint(1, 99)}'
        complement = rng.choice(['Apartamento ' + str(rng.randint(101, 1204)), 'Casa ' + str(rng.randint(1, 60)), None])
        location_id = add_location(f'addr:{key}:{index}', code, address, complement, person['created'] + HOUR)
        add('person_addresses', id=uid('person_address', key, index), person_id=person['person_id'],
            location_id=location_id, label='Casa' if index == 0 else 'Trabajo', is_default=(index == 0),
            created_at=ts(person['created'] + HOUR), updated_at=ts(person['created'] + HOUR))
        if index == 0:
            m = MUNICIPALITIES[code]
            BUYER_ADDRESS[key] = {'address_line': address, 'address_complement': complement, 'city': m['city'],
                                  'department': m['department'], 'lat': m['lat'], 'lon': m['lon'], 'code': code}

# Seudónimos, consentimientos (Ley 1581), tokens, sesiones, intentos de acceso y auditoría
for key in sorted(PEOPLE):
    person = PEOPLE[key]
    created = person['created']
    add('privacy.subject_pseudonyms', person_id=person['person_id'], subject_key=person['subject_key'],
        created_at=tz(created))
    granted_opt = {p: rng.random() < share for p, share in [('ANALYTICS_TRACKING', 0.7), ('PERSONALIZATION', 0.5),
                                                            ('MARKETING_EMAIL', 0.35), ('VEHICLE_DATA', 0.6),
                                                            ('ML_TRAINING', 0.3)]}
    consents = [('TERMS_OF_SERVICE', True), ('PERSONAL_DATA_PROCESSING', True)] + sorted(granted_opt.items())
    for purpose, granted in consents:
        add('person_consents', person_id=person['person_id'], purpose_code=purpose, granted=granted,
            policy_version='2026-01', channel='WEB', evidence={'formulario': 'registro', 'demo': True},
            ip_address=doc_ip(), user_agent=rng.choice(USER_AGENTS), recorded_at=tz(created + 30))
    person['analytics_ok'] = granted_opt['ANALYTICS_TRACKING']
    if granted_opt['MARKETING_EMAIL'] and rng.random() < 0.3:
        at = min(created + rng.randint(5, 90) * DAY, NOW_MARGIN)
        add('person_consents', person_id=person['person_id'], purpose_code='MARKETING_EMAIL', granted=False,
            policy_version='2026-01', channel='EMAIL', evidence={'origen': 'enlace de baja', 'demo': True},
            ip_address=None, user_agent=None, recorded_at=tz(at))
    verified = person['state'] != 'PENDING'
    add('account_tokens', id=uid('token', key, 'verify'), account_id=person['account_id'], purpose='EMAIL_VERIFICATION',
        token=rand_hex(), expiration_at=ts(created + DAY), used=verified, created_at=ts(created),
        updated_at=ts(created + (HOUR if verified else 0)))
    add('audit_logs', id=uid('audit', key, 'registration'), account_id=person['account_id'],
        action='USER_REGISTRATION_REQUESTED', entity_type='ACCOUNT', entity_id=person['account_id'],
        metadata={'email': person['email'], 'accountType': person['type'], 'state': 'PENDING'},
        created_at=ts(created), updated_at=ts(created))
    if not verified:
        continue
    add('audit_logs', id=uid('audit', key, 'verified'), account_id=person['account_id'], action='USER_EMAIL_VERIFIED',
        entity_type='ACCOUNT', entity_id=person['account_id'], metadata={'email': person['email'], 'state': 'ACTIVE'},
        created_at=ts(created + HOUR), updated_at=ts(created + HOUR))
    span = max(-created - 11 * DAY, DAY)
    for n in range(rng.randint(1, 4)):
        at = -11 * DAY - rng.randint(0, span)
        add('login_attempts', id=uid('login', key, n), account_id=person['account_id'], attempted_at=ts(at),
            successful=rng.random() < 0.85, ip_address=doc_ip(), created_at=ts(at), updated_at=ts(at))
        add('account_sessions', id=uid('session', key, n), account_id=person['account_id'],
            refresh_token_hash=rand_hex(), ip_address=doc_ip(), user_agent=rng.choice(USER_AGENTS),
            expired_at=ts(at + 7 * DAY), revoked_at=ts(at + rng.randint(HOUR, 6 * DAY)) if rng.random() < 0.5 else None,
            created_at=ts(at), updated_at=ts(at))
    if rng.random() < 0.15:
        at = -rng.randint(12, 60) * DAY
        add('account_tokens', id=uid('token', key, 'recovery'), account_id=person['account_id'],
            purpose='PASSWORD_RECOVERY', token=rand_hex(), expiration_at=ts(at + HOUR), used=False,
            created_at=ts(at), updated_at=ts(at))
    if person['state'] == 'LOCKED':
        at = -12 * DAY
        for n in range(5):
            add('login_attempts', id=uid('login', key, 'fail', n), account_id=person['account_id'],
                attempted_at=ts(at + n * MINUTE), successful=False, ip_address='203.0.113.77',
                created_at=ts(at + n * MINUTE), updated_at=ts(at + n * MINUTE))
        add('account_tokens', id=uid('token', key, 'unlock'), account_id=person['account_id'], purpose='ACCOUNT_UNLOCK',
            token=rand_hex(), expiration_at=ts(at + DAY), used=False, created_at=ts(at + 300), updated_at=ts(at + 300))
        add('audit_logs', id=uid('audit', key, 'locked'), account_id=person['account_id'], action='ACCOUNT_LOCKED',
            entity_type='ACCOUNT', entity_id=person['account_id'],
            metadata={'email': person['email'], 'ipAddress': '203.0.113.77', 'failedAttempts': 5, 'state': 'LOCKED'},
            created_at=ts(at + 300), updated_at=ts(at + 300))

# %%
# Almacenes, sucursales, roles, colaboradores, invitaciones, planes, solicitudes y pagos de suscripción
for code, name, price, max_b, max_p, max_u, caps in DEMO_PLANS:
    add('seller_plans', id=uid('plan', code), code=code, name=name,
        description='Plan de demostración: no aparece en el catálogo público (enabled = false).',
        monthly_price_cop=price, currency='COP', features=['Plan de demostración'], recommended=False, enabled=False,
        max_branches=max_b, max_products=max_p, max_users=max_u, capabilities=caps)

STORE_INFO = {}
root = PEOPLE['root']
for index, (code, name, store_state, app_status, branches) in enumerate(STORES):
    owner = PEOPLE[f'owner:{code}']
    store_id, created = uid('store', code), owner['created'] + DAY
    add('store', id=store_id, bussnies_name=name, nit=f'DEMO-{900000001 + index}',
        bussnies_email=f'almacen.{code.lower()}@demo.autopartco.test', state=ref('state', store_state),
        configuration={'demo': True, 'seed': SEED_VERSION}, logo_path=None, banner_path=None,
        data_origin=DATA_ORIGIN, created_at=ts(created), updated_at=ts(created))
    info = {'id': store_id, 'name': name, 'code': code, 'created': created, 'branches': [], 'app_status': app_status,
            'owner': owner}
    for branch_name, mun, address in branches:
        location_id = add_location(f'branch:{code}:{branch_name}', mun, address, 'Dirección ficticia de demostración',
                                   created + HOUR)
        branch_id = uid('branch', code, branch_name)
        add('branches', id=branch_id, store_id=store_id, name=branch_name, location_id=location_id,
            number_phone=fake_phone(), state=ref('state', 'ACTIVE'), created_at=ts(created + HOUR),
            updated_at=ts(created + HOUR))
        info['branches'].append({'id': branch_id, 'name': branch_name, 'mun': mun, 'store': code})
    STORE_INFO[code] = info
    roles = {}
    for role_code, role_name, permissions in (ROLE_DEFS if app_status == 'ACTIVE' else ROLE_DEFS[:1]):
        role_id = uid('role', code, role_code)
        roles[role_code] = role_id
        add('roles', id=role_id, store_id=store_id, code=role_code, name=role_name, created_at=ts(created),
            updated_at=ts(created))
        for permission in permissions:
            add('roles_permissions', id=uid('role_permission', code, role_code, permission), role_id=role_id,
                permission_id=ref('permission', permission), created_at=ts(created), updated_at=ts(created))
    info['roles'] = roles
    owner_collab = uid('collaborator', code, 'owner')
    add('collaborators', id=owner_collab, person_id=owner['person_id'], collab_store_id=store_id,
        state=ref('state', 'ACTIVE'), created_at=ts(created), updated_at=ts(created))
    add('roles_collaborators', collaborator_id=owner_collab, role_id=roles['OWNER'], created_at=ts(created),
        updated_at=ts(created))
    for branch in info['branches']:
        add('branches_collaborators', collaborator_id=owner_collab, branch_id=branch['id'],
            created_at=ts(created + HOUR), updated_at=ts(created + HOUR))
    info['staff_accounts'] = [owner['account_id']]
    for n, staff_key in enumerate(STAFF.get(code, []), start=1):
        staff = PEOPLE[staff_key]
        role_code = 'VENDEDOR' if n == 1 else 'BODEGA'
        invited = staff['created'] - 2 * DAY
        add('seller_collaborator_invitations', id=uid('invitation', code, staff_key), store_id=store_id,
            invited_by_account_id=owner['account_id'], role_id=roles[role_code], email=staff['email'],
            token_hash=rand_hex(), expires_at=ts(invited + 2 * DAY + HOUR), accepted_at=ts(staff['created']),
            revoked_at=None, created_at=ts(invited), updated_at=ts(staff['created']))
        collab_id = uid('collaborator', code, staff_key)
        collab_state = 'INACTIVE' if n == 3 else 'ACTIVE'
        add('collaborators', id=collab_id, person_id=staff['person_id'], collab_store_id=store_id,
            state=ref('state', collab_state), created_at=ts(staff['created']), updated_at=ts(staff['created']))
        add('roles_collaborators', collaborator_id=collab_id, role_id=roles[role_code],
            created_at=ts(staff['created']), updated_at=ts(staff['created']))
        branch = info['branches'][(n - 1) % len(info['branches'])]
        add('branches_collaborators', collaborator_id=collab_id, branch_id=branch['id'],
            created_at=ts(staff['created']), updated_at=ts(staff['created']))
        if collab_state == 'ACTIVE':
            info['staff_accounts'].append(staff['account_id'])
    if app_status == 'ACTIVE':
        add('seller_collaborator_invitations', id=uid('invitation', code, 'revoked'), store_id=store_id,
            invited_by_account_id=owner['account_id'], role_id=roles['BODEGA'],
            email=f'invitado.revocado.{code.lower()}@demo.autopartco.test', token_hash=rand_hex(),
            expires_at=ts(-40 * DAY), accepted_at=None, revoked_at=ts(-41 * DAY), created_at=ts(-42 * DAY),
            updated_at=ts(-41 * DAY))
        add('seller_collaborator_invitations', id=uid('invitation', code, 'expired'), store_id=store_id,
            invited_by_account_id=owner['account_id'], role_id=roles['VENDEDOR'],
            email=f'invitado.vencido.{code.lower()}@demo.autopartco.test', token_hash=rand_hex(),
            expires_at=ts(-12 * DAY), accepted_at=None, revoked_at=None, created_at=ts(-14 * DAY),
            updated_at=ts(-14 * DAY))
    # Solicitud de vendedor, documentos (rutas ficticias: nada se sube a Supabase) y pagos simulados
    app_id = uid('seller_application', code)
    applied = created + HOUR
    plan_code, _, plan_price, *_ = DEMO_PLANS[index % 2]
    legal_type = 'LEGAL_ENTITY' if index % 3 else 'NATURAL_PERSON'
    row = dict(id=app_id, applicant_account_id=owner['account_id'], store_id=store_id, plan_id=ref('plan', 'TRY'),
               selected_plan_id=ref('plan', plan_code), legal_type=legal_type, status=app_status, reviewed_by=None,
               review_notes=None, onboarding_token_hash=None, onboarding_token_expires_at=None,
               configuration_completed_at=None, trial_starts_at=None, trial_ends_at=None, paid_through=None,
               created_at=ts(applied), updated_at=ts(applied))
    if app_status != 'PENDING_REVIEW':
        row.update(reviewed_by=root['account_id'], review_notes={
            'ACTIVE': 'Documentos completos. Aprobado (demo).',
            'CONFIGURATION_PENDING': 'Aprobado; pendiente de configurar el almacén (demo).',
            'REJECTED': 'RUT ilegible; se rechaza la solicitud (demo).'}[app_status])
    if app_status == 'CONFIGURATION_PENDING':
        row.update(onboarding_token_hash=rand_hex(), onboarding_token_expires_at=ts(-DAY), updated_at=ts(-3 * DAY))
    if app_status == 'ACTIVE':
        setup = applied + 2 * DAY
        row.update(plan_id=ref('plan', plan_code), configuration_completed_at=ts(setup), trial_starts_at=ts(setup),
                   trial_ends_at=ts(setup + 30 * DAY), paid_through=ts(rng.randint(200, 330) * DAY),
                   updated_at=ts(setup + 29 * DAY))
        for n, (status, at) in enumerate([('DECLINED', setup + 27 * DAY), ('APPROVED', setup + 28 * DAY)], start=1):
            add('seller_payments', id=uid('seller_payment', code, status), application_id=app_id,
                plan_id=ref('plan', plan_code), reference='APC-' + uid('seller_payment_ref', code, n).hex,
                amount_in_cents=plan_price * 12 * 100, prepaid_months=12, currency='COP', status=status,
                provider_transaction_id=('DEMO-TX-' + uid('seller_tx', code).hex[:20]) if status == 'APPROVED' else None,
                environment='SANDBOX', created_at=ts(at), updated_at=ts(at + HOUR))
    add('seller_applications', **row)
    doc_types = ['IDENTITY_DOCUMENT', 'RUT'] + (['LEGAL_REPRESENTATIVE_ID', 'CERTIFICATE_OF_EXISTENCE']
                                                if legal_type == 'LEGAL_ENTITY' else [])
    for doc in doc_types:
        add('seller_application_documents', id=uid('seller_document', code, doc), application_id=app_id,
            document_type=doc, object_path=f'demo/seller-applications/{app_id}/{doc.lower()}.pdf',
            original_filename=f'{doc.lower()}_demo.pdf', content_type='application/pdf',
            size_bytes=rng.randint(80_000, 2_400_000), created_at=ts(applied), updated_at=ts(applied))
    add('audit_logs', id=uid('audit', 'store', code), account_id=owner['account_id'], action='STORE_APPLICATION_SUBMITTED',
        entity_type='STORE', entity_id=store_id, metadata={'store': name, 'status': app_status, 'demo': True},
        created_at=ts(applied), updated_at=ts(applied))

# %%
# Productos (sintéticos), compatibilidades por reglas, especificaciones, inventario, precios y reorden
MODELS_BY_SEGMENT = defaultdict(list)
for model in MODELS:
    MODELS_BY_SEGMENT[model['segment']].append(model)
PRODUCTS, INVENTORY, PRODUCT_BY_ID = [], [], {}
part_seq = Counter()
for type_code, type_name, category, segments, manufacturers, (pmin, pmax), universal, attrs in PART_TYPES:
    available_segments = [s for s in segments if MODELS_BY_SEGMENT.get(s)]
    if not universal and not available_segments:
        continue
    for variant in range(2 if universal else rng.choice([2, 2, 3])):
        part_seq[category] += 1
        part_number = f'APC-{CAT_ABBR[category]}-{part_seq[category]:04d}'
        manufacturer = manufacturers[variant] if variant < len(manufacturers) else rng.choice(manufacturers)
        fits = []
        if not universal:
            pool = MODELS_BY_SEGMENT[rng.choice(available_segments)]
            fits = sorted(rng.sample(pool, min(len(pool), rng.choice([1, 2, 2, 3]))), key=lambda m: (m['make'], m['name']))
        values = {spec: rng.choice(options) for spec, options, _ in attrs}
        unit_of = {spec: unit for spec, _, unit in attrs}
        attributes = {spec: (f'{value} {unit_of[spec]}' if unit_of[spec] else value) for spec, value in values.items()}
        attributes['Origen'] = 'Datos de demostración'
        fit_text = ' / '.join(f"{m['make']} {m['name']}" for m in fits)
        name = (f'{type_name} {manufacturer}' + (f' para {fit_text}' if fit_text else ''))[:200]
        base_price = round_cop(rng.randint(pmin // 100, pmax // 100) * 100)
        offered_by = sorted(rng.sample([s[0] for s in ACTIVE_STORES], rng.choice([3, 4] if universal else [1, 2, 2, 3, 3])))
        for store_code in offered_by:
            info = STORE_INFO[store_code]
            product_id = uid('product', store_code, part_number)
            state = 'INACTIVE' if rng.random() < 0.03 else 'ACTIVE'
            created = info['created'] + rng.randint(3, 25) * DAY
            sku = f'DEMO-{store_code}-{part_number}'
            product = {'id': product_id, 'store': store_code, 'part': part_number, 'name': name, 'state': state,
                       'fits': fits, 'universal': universal, 'category': category, 'manufacturer': manufacturer,
                       'created': created, 'type': type_code, 'sku': sku, 'attributes': attributes}
            PRODUCTS.append(product)
            PRODUCT_BY_ID[product_id] = product
            add('products', id=product_id, store_id=info['id'], name=name,
                description=(f'{type_name} marca {manufacturer}. Referencia de catálogo {part_number} (ficticia, no es '
                             f'un número OEM). Compatibilidad asignada por reglas de demostración: confírmala con el '
                             f'manual de tu vehículo. Producto de demostración de Autopartco.'),
                sku=sku, part_number=part_number, manufacturer_id=ref('manufacturer', manufacturer),
                category_id=ref('category', category), state_id=ref('state', state), attributes=attributes,
                created_at=ts(created), updated_at=ts(created))
            for model in fits:
                for label, year, _ in model['versions']:
                    add('products_compatibilies', product_id=product_id,
                        compatibility_id=ref('compat', model['make'], model['name'], label),
                        evidence_status='SELLER_DECLARED', source='SELLER', year_from=year, year_to=year,
                        confirmed_by_account_id=None, confirmed_at=None, created_at=tz(created), updated_at=tz(created))
            for spec, value in values.items():
                unit = unit_of[spec]
                add('products_specifications', id=uid('product_spec', product_id, spec), product_id=product_id,
                    specification_id=ref('specification', spec),
                    specification_unit_id=ref('unit_spec', spec, unit) if unit else None, value=value,
                    created_at=ts(created), updated_at=ts(created))
            store_factor = pct(0.92, 1.10)
            for branch in info['branches']:
                price = round_cop(base_price * store_factor * pct(0.98, 1.03))
                inv_created = created + HOUR
                prices = [(inv_created, price)]
                if rng.random() < 0.25:   # un cambio de precio en el periodo
                    change_at = rng.randint(inv_created + 10 * DAY, NOW_MARGIN - DAY) if inv_created + 10 * DAY < NOW_MARGIN - DAY else None
                    if change_at:
                        prices = [(inv_created, round_cop(price * pct(0.90, 0.97))), (change_at, price)]
                roll = rng.random()
                target = 0 if roll < 0.06 else (rng.randint(1, 2) if roll < 0.16 else rng.randint(3, 80 if universal else 40))
                INVENTORY.append({'id': uid('inventory', branch['id'], product_id), 'branch': branch,
                                  'store': store_code, 'product': product, 'prices': prices, 'price': prices[-1][1],
                                  'unit_cost': cost4(price * pct(0.55, 0.75) / (1 + VAT_RATE)),
                                  'target': target, 'created': inv_created, 'events': []})
INVENTORY_BY_ID = {row['id']: row for row in INVENTORY}
print(f'Productos: {len(PRODUCTS)} | referencias APC: {sum(part_seq.values())} | inventario: {len(INVENTORY)}')


def price_at(row, at):
    current = row['prices'][0][1]
    for since, value in row['prices']:
        if since <= at:
            current = value
    return current


# Promociones (fechas relativas al ancla)
PROMOTIONS = []


def add_promotion(key, scope, store_code, name, target_type, target, discount_type, value, start, end, enabled=True,
                  attribute=None):
    promo = {'id': uid('promotion', key), 'scope': scope, 'store': store_code, 'target_type': target_type,
             'target': target, 'discount_type': discount_type, 'value': money(value), 'start': start, 'end': end,
             'enabled': enabled, 'attribute': attribute, 'name': name}
    PROMOTIONS.append(promo)
    target_sql = {'CATEGORY': lambda t: ref('category', t), 'MANUFACTURER': lambda t: ref('manufacturer', t),
                  'PRODUCT': lambda t: t, 'STORE': lambda t: t}.get(target_type, lambda t: None)(target)
    add('promotions', id=promo['id'], scope=scope, store_id=STORE_INFO[store_code]['id'] if store_code else None,
        name=name, target_type=target_type, target_id=target_sql,
        attribute_key=attribute[0] if attribute else None, attribute_value=attribute[1] if attribute else None,
        attribute_operator='EQUALS' if attribute else None, discount_type=discount_type, discount_value=money(value),
        starts_at=tz(start), ends_at=tz(end), enabled=enabled, created_at=ts(start - DAY), updated_at=ts(start - DAY))


med_product = next(p for p in PRODUCTS if p['store'] == 'MED' and p['state'] == 'ACTIVE')
add_promotion('platform-frenos', 'PLATFORM', None, 'Semana del freno (demo)', 'CATEGORY', 'FRENOS', 'PERCENTAGE', 10,
              -5 * DAY, 25 * DAY)
add_promotion('bog-bosch', 'STORE', 'BOG', 'Bosch en Repuestos Andinos (demo)', 'MANUFACTURER', 'Bosch', 'PERCENTAGE', 15,
              -40 * DAY, 50 * DAY)
add_promotion('med-producto', 'STORE', 'MED', 'Precio especial de temporada (demo)', 'PRODUCT', med_product['id'],
              'FIXED_AMOUNT', 5000, -20 * DAY, 30 * DAY)
add_promotion('cli-aceite', 'STORE', 'CLI', 'Combo cambio de aceite (demo)', 'ATTRIBUTE', None, 'PERCENTAGE', 8,
              -60 * DAY, 60 * DAY, attribute=('Viscosidad', '10W-30'))
add_promotion('pei-aniversario', 'STORE', 'PEI', 'Aniversario (demo, vencida)', 'STORE', None, 'PERCENTAGE', 5,
              -90 * DAY, -60 * DAY)
add_promotion('pei-pausada', 'STORE', 'PEI', 'Promoción pausada (demo)', 'STORE', None, 'PERCENTAGE', 7, -2 * DAY,
              20 * DAY, enabled=False)


def best_promotion(product, list_price, at):
    best = (money(0), None)
    for promo in PROMOTIONS:
        if not promo['enabled'] or not (promo['start'] <= at < promo['end']):
            continue
        if promo['scope'] == 'STORE' and promo['store'] != product['store']:
            continue
        target_type, target = promo['target_type'], promo['target']
        applies = (target_type == 'ALL' or (target_type == 'STORE' and (target is None or target == STORE_INFO[product['store']]['id']))
                   or (target_type == 'CATEGORY' and target == product['category'])
                   or (target_type == 'MANUFACTURER' and target == product['manufacturer'])
                   or (target_type == 'PRODUCT' and target == product['id'])
                   or (target_type == 'ATTRIBUTE' and product['attributes'].get(promo['attribute'][0]) == promo['attribute'][1]))
        if not applies:
            continue
        discount = (money(list_price * promo['value'] / 100) if promo['discount_type'] == 'PERCENTAGE'
                    else min(promo['value'], list_price))
        if discount > best[0]:
            best = (discount, promo)
    return best

# %%
# Garaje de clientes, sesiones de navegación y carritos
CUSTOMER_VEHICLES = defaultdict(list)
for key in BUYERS:
    person = PEOPLE[key]
    if person['state'] != 'ACTIVE' or rng.random() > 0.6:
        continue
    for n in range(rng.choice([1, 1, 2])):
        model = rng.choice([m for m in MODELS if m['kind'] != 'moto'] if rng.random() < 0.85 else MODELS)
        label, year, cc = rng.choice(model['versions'])
        cv_id = uid('customer_vehicle', key, n)
        created = person['created'] + rng.randint(1, 20) * DAY
        created = min(created, NOW_MARGIN - DAY)
        add('customer_vehicles', id=cv_id, person_id=person['person_id'], make_id=ref('make', model['make']),
            model_id=ref('model', model['make'], model['name']),
            version_id=ref('version', model['make'], model['name'], label), model_year=year, vin=None,
            nickname=rng.choice(['El de diario', 'Carro de la casa', 'Moto del trabajo', None]),
            usage_type=rng.choice(['PERSONAL', 'PERSONAL', 'RIDE_HAILING', 'TAXI', 'COMMERCIAL']), fuel_type=None,
            declared_monthly_km=rng.choice([800, 1200, 1500, 2500, 4000]), acquired_on=day(-rng.randint(200, 3000)),
            is_primary=(n == 0), created_at=tz(created), updated_at=tz(created))
        km = rng.randint(15_000, 160_000)
        for r in range(rng.randint(1, 3)):
            at = min(created + r * rng.randint(20, 60) * DAY, NOW_MARGIN)
            add('vehicle_odometer_readings', customer_vehicle_id=cv_id, odometer_km=km, read_at=tz(at),
                source=rng.choice(['USER', 'CHECKOUT']), is_suspect=False, created_at=tz(at))
            km += rng.randint(1500, 6000)
        CUSTOMER_VEHICLES[key].append({'id': cv_id, 'model': model, 'version': label})

SESSIONS = []
DEVICES = [('DESKTOP', 'Windows', 'Chrome'), ('MOBILE', 'Android', 'Chrome'), ('MOBILE', 'iOS', 'Safari'),
           ('TABLET', 'Android', 'Chrome')]
UTMS = [(None, None, None, None), ('google', 'cpc', 'repuestos-demo', 'www.google.com'),
        ('facebook', 'social', 'semana-freno-demo', 'm.facebook.com'), (None, None, None, 'www.bing.com'),
        ('newsletter', 'email', 'boletin-demo', None)]


def new_session(person_key, start, municipality):
    person = PEOPLE.get(person_key) if person_key else None
    index = len(SESSIONS)
    device, os_family, browser = rng.choice(DEVICES)
    utm_source, utm_medium, utm_campaign, referrer = rng.choice(UTMS)
    tracked = person is None or person.get('analytics_ok')
    session = {'id': uid('tracking_session', index), 'anon': uid('anonymous', person_key or index), 'start': start,
               'last': start, 'person': person_key, 'subject': person['subject_key'] if person and tracked else None,
               'device': device, 'utm': (utm_source, utm_medium, utm_campaign), 'referrer': referrer,
               'mun': municipality, 'os': os_family, 'browser': browser}
    SESSIONS.append(session)
    return session


QUERIES = ['pastillas de freno', 'filtro de aceite', 'kit de distribucion', 'amortiguador delantero', 'bujias iridio',
           'bateria 12v', 'aceite 10w30', 'radiador', 'kit de embrague', 'correa de accesorios', 'liquido de frenos',
           'termostato', 'sensor de oxigeno', 'kit de arrastre', 'rotula', 'discos de freno']


def query_hash(text):
    return hashlib.sha256(norm(text).lower().encode('utf-8')).hexdigest()

# %%
# Pedidos históricos coherentes: ventas, líneas, pagos, envíos, historial, reservas y ledger
PAYMENT_METHODS = ['SIMULATED_CARD', 'SIMULATED_CARD', 'SIMULATED_TRANSFER', 'SIMULATED_CASH']
CARRIERS = ['Servientrega', 'Coordinadora', 'Interrapidísimo', 'Envía']
CANCEL_REASONS = [('CUSTOMER_CHANGED_MIND', 'CUSTOMER'), ('CUSTOMER_FOUND_CHEAPER', 'CUSTOMER'),
                  ('CUSTOMER_WRONG_PART', 'CUSTOMER'), ('STORE_OUT_OF_STOCK', 'STORE'), ('STORE_PRICE_ERROR', 'STORE')]
sellable = [row for row in INVENTORY if row['product']['state'] == 'ACTIVE']
by_store = defaultdict(list)
for row in sellable:
    by_store[row['store']].append(row)
active_buyers = [k for k in BUYERS if PEOPLE[k]['state'] == 'ACTIVE']
STORE_CODES = [s[0] for s in ACTIVE_STORES]


def target_status(days_ago):
    weights = ({'PENDING': 50, 'CONFIRMED': 25, 'PREPARING': 15, 'CANCELLED': 10} if days_ago < 2 else
               {'PENDING': 8, 'CONFIRMED': 15, 'PREPARING': 25, 'SHIPPED': 42, 'CANCELLED': 10} if days_ago < 7 else
               {'PREPARING': 4, 'SHIPPED': 24, 'DELIVERED': 64, 'CANCELLED': 8} if days_ago < 15 else
               {'SHIPPED': 2, 'DELIVERED': 90, 'CANCELLED': 8})
    return rng.choices(list(weights), weights=list(weights.values()))[0]


def sale_timeline(start, wanted):
    """Transiciones válidas según SaleStatusService y ShipmentService, sin pasar del ancla."""
    t, sale_steps, ship_steps = start, [('PENDING', start)], []
    if wanted == 'CANCELLED':
        if rng.random() < 0.5:
            t += rng.randint(HOUR // 2, 20 * HOUR)
            sale_steps.append(('CONFIRMED', t))
        t += rng.randint(HOUR, 30 * HOUR)
        sale_steps.append(('CANCELLED', t))
    else:
        order = ['CONFIRMED', 'PREPARING', 'SHIP_PREPARING', 'DISPATCHED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED']
        stop = {'PENDING': 0, 'CONFIRMED': 1, 'PREPARING': rng.choice([2, 3]), 'SHIPPED': rng.choice([4, 5, 6]),
                'DELIVERED': 7}[wanted]
        gaps = {'CONFIRMED': (HOUR // 2, 20 * HOUR), 'PREPARING': (HOUR, 24 * HOUR), 'SHIP_PREPARING': (HOUR // 2, 6 * HOUR),
                'DISPATCHED': (2 * HOUR, 30 * HOUR), 'IN_TRANSIT': (2 * HOUR, 24 * HOUR),
                'OUT_FOR_DELIVERY': (2 * HOUR, 20 * HOUR), 'DELIVERED': (3 * HOUR, 30 * HOUR)}
        for step in order[:stop]:
            if step in ('IN_TRANSIT', 'OUT_FOR_DELIVERY') and wanted == 'DELIVERED' and rng.random() < 0.35:
                continue
            t += rng.randint(*gaps[step])
            if step in ('CONFIRMED', 'PREPARING'):
                sale_steps.append((step, t))
            else:
                code = 'PREPARING' if step == 'SHIP_PREPARING' else step
                ship_steps.append((code, t))
                if code == 'DISPATCHED':
                    sale_steps.append(('SHIPPED', t))
                elif code == 'DELIVERED':
                    sale_steps.append(('DELIVERED', t))
    sale_steps = [(s, x) for s, x in sale_steps if x <= NOW_MARGIN]
    ship_steps = [(s, x) for s, x in ship_steps if x <= NOW_MARGIN]
    if not any(s == 'PREPARING' for s, _ in sale_steps):
        ship_steps = []
    return sale_steps, ship_steps


order_starts = sorted(-rng.randint(2 * HOUR, HISTORY_DAYS * DAY) for _ in range(N_ORDERS))
ORDERS = []
for number, start in enumerate(order_starts, start=1):
    buyer = rng.choice(active_buyers)
    person = PEOPLE[buyer]
    if person['created'] + DAY > start:
        start = person['created'] + DAY + rng.randint(0, 5 * DAY)
        if start > NOW_MARGIN - 2 * HOUR:
            continue
    home = rng.choice(STORE_CODES)
    chosen, used = [], set()
    for _ in range(rng.choices([1, 2, 3], weights=[60, 28, 12])[0]):
        pool = by_store[home] if rng.random() < 0.7 or not chosen else by_store[rng.choice(STORE_CODES)]
        candidates = [r for r in pool if r['created'] < start - HOUR and r['product']['id'] not in used]
        if not candidates:
            continue
        row = rng.choice(candidates)
        used.add(row['product']['id'])
        chosen.append((row, rng.choices([1, 2, 3, 4], weights=[70, 20, 6, 4])[0]))
    if not chosen:
        continue
    ORDERS.append({'number': number, 'id': uid('order', number), 'buyer': buyer, 'start': start, 'lines': chosen})
ORDERS.sort(key=lambda o: (o['start'], o['number']))


def add_event(inventory_row, at, kind, quantity, reason, source, reference_type=None, reference_id=None, detail_id=None,
              actor=None, unit_cost=None, key=None, notes=None):
    inventory_row['events'].append({'at': at, 'kind': kind, 'qty': quantity, 'reason': reason, 'source': source,
                                    'ref_type': reference_type, 'ref_id': reference_id, 'detail': detail_id,
                                    'actor': actor, 'cost': unit_cost, 'key': key, 'notes': notes,
                                    'id': uid('movement', inventory_row['id'], key)})


def staff_actor(store_code):
    return rng.choice(STORE_INFO[store_code]['staff_accounts'])


SALE_ROWS = []
for order in ORDERS:
    person = PEOPLE[order['buyer']]
    start = order['start']
    days_ago = -start / DAY
    session = new_session(order['buyer'], start - rng.randint(5 * MINUTE, 50 * MINUTE), BUYER_ADDRESS[order['buyer']]['code'])
    order['session'] = session
    vehicles = CUSTOMER_VEHICLES.get(order['buyer'], [])
    by_branch = defaultdict(list)
    for row, units in order['lines']:
        by_branch[row['branch']['id']].append((row, units))
    order['sales'] = []
    for branch_id in sorted(by_branch, key=str):
        lines = by_branch[branch_id]
        store_code = lines[0][0]['store']
        sale_steps, ship_steps = sale_timeline(start, target_status(days_ago))
        sale = {'id': uid('sale', order['number'], branch_id), 'branch_id': branch_id, 'store': store_code,
                'branch': lines[0][0]['branch'], 'steps': sale_steps, 'ship': ship_steps,
                'status': sale_steps[-1][0], 'details': []}
        for line_no, (row, units) in enumerate(lines, start=1):
            product = row['product']
            list_price = price_at(row, start)
            discount, promo = best_promotion(product, list_price, start)
            unit_price = money(list_price - discount)
            vehicle = next((v for v in vehicles if any(v['model'] is m for m in product['fits'])), None)
            detail = {'id': uid('order_detail', order['number'], branch_id, line_no), 'row': row, 'units': units,
                      'list': list_price, 'unit_price': unit_price, 'discount': money(discount * units),
                      'promo': promo['id'] if promo else None, 'vehicle': vehicle['id'] if vehicle else None,
                      'compat': ('SELLER_DECLARED' if vehicle else 'UNKNOWN'), 'unit_cost': row['unit_cost']}
            sale['details'].append(detail)
        sale['subtotal'] = money(sum(d['unit_price'] * d['units'] for d in sale['details']))
        sale['discount'] = money(sum(d['discount'] for d in sale['details']))
        sale['shipping'] = money(0)
        sale['total'] = sale['subtotal'] + sale['shipping']
        cancel = next(((s, x) for s, x in sale_steps if s == 'CANCELLED'), None)
        sale['cancel_reason'] = rng.choice(CANCEL_REASONS) if cancel else None
        order['sales'].append(sale)
        SALE_ROWS.append((order, sale))

# Carritos: los pedidos con carrito convertido, más carritos abandonados, vencidos y activos
CARTS = []
for order in ORDERS:
    if rng.random() < 0.65:
        CARTS.append({'id': uid('cart', 'order', order['number']), 'person': order['buyer'], 'status': 'CONVERTED',
                      'order': order, 'start': order['session']['start'] + 2 * MINUTE,
                      'items': [(d['row'], d['units']) for s in order['sales'] for d in s['details']],
                      'session': order['session']})
for n in range(max(10, N_ORDERS // 6)):
    buyer = rng.choice(active_buyers)
    start = -rng.randint(3 * DAY, HISTORY_DAYS * DAY)
    if PEOPLE[buyer]['created'] + DAY > start:
        continue
    items = [(r, rng.randint(1, 2)) for r in rng.sample(sellable, rng.randint(1, 3)) if r['created'] < start]
    if items:
        session = new_session(buyer, start - 3 * MINUTE, BUYER_ADDRESS[buyer]['code'])
        CARTS.append({'id': uid('cart', 'abandoned', n), 'person': buyer,
                      'status': 'ABANDONED' if n % 4 else 'EXPIRED', 'order': None, 'start': start, 'items': items,
                      'session': session})
active_cart_people = set()
for n in range(max(5, N_BUYERS // 6)):
    buyer = rng.choice(active_buyers)
    if buyer in active_cart_people:
        continue
    start = -rng.randint(2 * HOUR, 6 * DAY)
    items = [(r, 1) for r in rng.sample(sellable, rng.randint(1, 2)) if r['created'] < start]
    if items and PEOPLE[buyer]['created'] + DAY < start:
        active_cart_people.add(buyer)
        session = new_session(buyer, start - 3 * MINUTE, BUYER_ADDRESS[buyer]['code'])
        CARTS.append({'id': uid('cart', 'active', n), 'person': buyer, 'status': 'ACTIVE', 'order': None,
                      'start': start, 'items': items, 'session': session})

for cart in CARTS:
    person = PEOPLE[cart['person']]
    order = cart['order']
    last = order['start'] if order else cart['start'] + rng.randint(2 * MINUTE, 40 * MINUTE)
    add('carts', id=cart['id'], person_id=person['person_id'], anonymous_id=cart['session']['anon'],
        session_id=cart['session']['id'], status=cart['status'], currency='COP',
        converted_order_id=order['id'] if order else None, last_activity_at=tz(last),
        abandoned_at=tz(last + DAY) if cart['status'] in ('ABANDONED', 'EXPIRED') else None,
        converted_at=tz(order['start']) if order else None, created_at=tz(cart['start']), updated_at=tz(last))
    for index, (row, units) in enumerate(cart['items']):
        item_id = uid('cart_item', cart['id'], index)
        added = cart['start'] + index * MINUTE
        add('cart_items', id=item_id, cart_id=cart['id'], inventory_id=row['id'], quantity=units,
            captured_unit_price=price_at(row, added), customer_vehicle_id=None, added_at=tz(added), removed_at=None,
            created_at=tz(added), updated_at=tz(added))
        if cart['status'] in ('ABANDONED', 'EXPIRED'):
            add('stock_reservations', id=uid('reservation', 'cart', item_id), inventory_id=row['id'],
                order_detail_id=None, cart_id=cart['id'], quantity=units,
                status='EXPIRED' if cart['status'] == 'EXPIRED' else 'RELEASED', expires_at=tz(added + 30 * MINUTE),
                created_at=tz(added), updated_at=tz(added + 30 * MINUTE))

# Filas de pedidos, ventas, líneas, historial, pagos, envíos, notificaciones y movimientos de venta
for order in ORDERS:
    person, start = PEOPLE[order['buyer']], order['start']
    statuses = [s['status'] for s in order['sales']]
    order_state = ('CANCELLED' if all(s == 'CANCELLED' for s in statuses) else
                   'DELIVERED' if all(s == 'DELIVERED' for s in statuses) else 'CONFIRMED')
    all_times = [x for s in order['sales'] for _, x in s['steps'] + s['ship']]
    last_event = max([start] + all_times)
    cancel_at = max((x for s in order['sales'] for st, x in s['steps'] if st == 'CANCELLED'), default=None)
    subtotal = money(sum(s['subtotal'] for s in order['sales']))
    discount_total = money(sum(s['discount'] for s in order['sales']))
    total = subtotal
    order.update(state=order_state, total=total, cancel_at=cancel_at)
    first_cancel_reason = next((s['cancel_reason'][0] for s in order['sales'] if s['cancel_reason']), None)
    session = order['session']
    add('orders', id=order['id'], person_id=person['person_id'], state_id=ref('state', order_state),
        channel='MOBILE_WEB' if session['device'] == 'MOBILE' else 'WEB', session_id=session['id'],
        data_origin=DATA_ORIGIN, currency='COP', subtotal=subtotal, discount_total=discount_total,
        shipping_total=money(0), total=total, placed_at=tz(start),
        cancelled_at=tz(cancel_at) if order_state == 'CANCELLED' else None,
        cancellation_reason_code=first_cancel_reason if order_state == 'CANCELLED' else None,
        created_at=ts(start), updated_at=ts(last_event))
    add('order_status_history', order_id=order['id'], from_status=None, to_status='CONFIRMED', changed_at=tz(start),
        actor_type='CUSTOMER', actor_account_id=person['account_id'], reason_code=None, notes='Pago simulado aprobado')
    if order_state != 'CONFIRMED':
        add('order_status_history', order_id=order['id'], from_status='CONFIRMED', to_status=order_state,
            changed_at=tz(last_event), actor_type='SYSTEM', actor_account_id=None,
            reason_code=first_cancel_reason if order_state == 'CANCELLED' else None,
            notes='Todas las ventas del pedido quedaron en ' + order_state)
    addr = BUYER_ADDRESS[order['buyer']]
    add('order_addresses', id=uid('order_address', order['number']), order_id=order['id'],
        address_line=addr['address_line'], address_complement=addr['address_complement'], city=addr['city'],
        department=addr['department'], country='Colombia', postal_code=None, latitude=addr['lat'],
        longitude=addr['lon'], municipality_code=addr['code'], created_at=ts(start), updated_at=ts(start))
    utm_source, utm_medium, utm_campaign = session['utm']
    add('order_attributions', order_id=order['id'], session_id=session['id'], anonymous_id=session['anon'],
        first_touch_at=tz(session['start']), first_utm_source=utm_source, first_utm_medium=utm_medium,
        first_utm_campaign=utm_campaign, first_utm_term=None, first_utm_content=None,
        first_referrer_host=session['referrer'], first_landing_path='/', last_touch_at=tz(start),
        last_utm_source=utm_source, last_utm_medium=utm_medium, last_utm_campaign=utm_campaign, last_utm_term=None,
        last_utm_content=None, last_referrer_host=session['referrer'], device_type=session['device'],
        os_family=session['os'], browser_family=session['browser'], touches_count=1, attribution_window_days=30,
        created_at=tz(start))
    refunded_amount = money(0)
    for sale in order['sales']:
        steps = sale['steps']
        times = {s: x for s, x in steps}
        sale_last = max([start] + [x for _, x in steps + sale['ship']])
        cancel_code, cancel_actor = sale['cancel_reason'] or (None, None)
        ready = next((x for s, x in steps if s == 'SHIPPED'), None)
        add('sales', id=sale['id'], order_id=order['id'], branch_id=sale['branch_id'],
            state_id=ref('state', sale['status']), subtotal=sale['subtotal'], shipping_cost=sale['shipping'],
            total=sale['total'], fulfillment_type='SHIPPING',
            confirmed_at=tz(times['CONFIRMED']) if 'CONFIRMED' in times else None,
            ready_at=tz(ready) if ready is not None and 'PREPARING' in times else None,
            cancelled_at=tz(times['CANCELLED']) if 'CANCELLED' in times else None,
            cancellation_reason_code=cancel_code, promised_ship_by=tz(start + 2 * DAY),
            promised_delivery_by=tz(start + 6 * DAY), discount_total=sale['discount'],
            created_at=ts(start), updated_at=ts(sale_last))
        previous = None
        for status, at in steps:
            actor_type = ('CUSTOMER' if status == 'PENDING' else
                          (cancel_actor if status == 'CANCELLED' else 'STORE'))
            actor = (person['account_id'] if actor_type == 'CUSTOMER' else
                     staff_actor(sale['store']) if actor_type == 'STORE' else None)
            add('sale_status_history', sale_id=sale['id'], store_id=STORE_INFO[sale['store']]['id'],
                from_status=previous, to_status=status, changed_at=tz(at), actor_type=actor_type,
                actor_account_id=actor, reason_code=cancel_code if status == 'CANCELLED' else None, notes=None)
            previous = status
            if status in ('CONFIRMED', 'PREPARING', 'CANCELLED'):
                add('notifications', id=uid('notification', sale['id'], 'sale', status), account_id=person['account_id'],
                    title='Pedido actualizado', message=f'Tu pedido cambió al estado {status}.',
                    read_at=ts(at + rng.randint(HOUR, 3 * DAY)) if at < -4 * DAY else None, created_at=ts(at),
                    updated_at=ts(at))
        add('notifications', id=uid('notification', sale['id'], 'new'),
            account_id=STORE_INFO[sale['store']]['owner']['account_id'], title='Nueva venta',
            message=f'Recibiste una venta en {sale["branch"]["name"]}.',
            read_at=ts(start + rng.randint(MINUTE, 6 * HOUR)) if start < -DAY else None, created_at=ts(start),
            updated_at=ts(start))
        cancel_at = times.get('CANCELLED')
        if cancel_at is not None:
            refunded_amount += sale['total']
        for detail in sale['details']:
            row, product = detail['row'], detail['row']['product']
            add('order_details', id=detail['id'], order_id=order['id'], sale_id=sale['id'], inventory_id=row['id'],
                units=detail['units'], unit_price=detail['unit_price'], product_id=product['id'],
                product_name_snapshot=product['name'], sku_snapshot=product['sku'],
                category_id_snapshot=ref('category', product['category']), list_unit_price=detail['list'],
                discount_amount=detail['discount'], promotion_id=detail['promo'], unit_cost_snapshot=detail['unit_cost'],
                vat_rate=VAT_RATE, compatibility_status_shown=detail['compat'], customer_vehicle_id=detail['vehicle'],
                created_at=ts(start), updated_at=ts(start))
            add_event(row, start, 'OUT', detail['units'], 'MARKETPLACE_SALE', 'ORDER', 'ORDER_DETAIL', detail['id'],
                      detail['id'], key=f"sale:{detail['id']}")
            add('stock_reservations', id=uid('reservation', 'detail', detail['id']), inventory_id=row['id'],
                order_detail_id=detail['id'], cart_id=None, quantity=detail['units'],
                status='RELEASED' if cancel_at is not None else 'CONSUMED', expires_at=tz(start + 15 * MINUTE),
                created_at=tz(start - MINUTE), updated_at=tz(start))
            if cancel_at is not None:
                add_event(row, cancel_at, 'IN', detail['units'], 'SALE_CANCELLED_RESTOCK', 'ORDER', 'ORDER_DETAIL',
                          detail['id'], detail['id'], key=f"cancel:{detail['id']}")
        if sale['ship']:
            shipment_id = uid('shipment', sale['id'])
            first = sale['ship'][0][1]
            shipped = next((x for s, x in sale['ship'] if s == 'DISPATCHED'), None)
            delivered = next((x for s, x in sale['ship'] if s == 'DELIVERED'), None)
            origin, destination = sale['branch']['mun'], BUYER_ADDRESS[order['buyer']]['code']
            add('shipments', id=shipment_id, sale_id=sale['id'],
                shipment_status_id=ref('shipment_status', sale['ship'][-1][0]), carrier=rng.choice(CARRIERS),
                tracking_number=f'DEMO-TRK-{order["number"]:05d}-{str(sale["id"])[:8].upper()}',
                shipped_at=ts(shipped) if shipped is not None else None,
                delivered_at=ts(delivered) if delivered is not None else None,
                estimated_delivery_at=tz(first + 4 * DAY), origin_municipality_code=origin,
                destination_municipality_code=destination,
                distance_km=distance_km(MUNICIPALITIES[origin], MUNICIPALITIES[destination]),
                delivery_attempts=1 if delivered is not None else 0, package_weight_grams=rng.randint(300, 9000),
                created_at=ts(first), updated_at=ts(sale['ship'][-1][1]))
            previous = None
            for status, at in sale['ship']:
                add('shipment_events', id=uid('shipment_event', shipment_id, status), shipment_id=shipment_id,
                    shipment_status_id=ref('shipment_status', status),
                    from_status_id=ref('shipment_status', previous) if previous else None, source='STORE',
                    actor_account_id=staff_actor(sale['store']),
                    municipality_code=destination if status in ('OUT_FOR_DELIVERY', 'DELIVERED') else origin,
                    notes='Envío registrado' if status == 'PREPARING' else None, event_at=ts(at), created_at=ts(at),
                    updated_at=ts(at))
                previous = status
                title, message = (('Envío registrado', 'Se registró el envío de tu pedido.') if status == 'PREPARING'
                                  else ('Estado del envío actualizado', f'Tu envío cambió al estado {status}.'))
                add('notifications', id=uid('notification', sale['id'], 'ship', status), account_id=person['account_id'],
                    title=title, message=message,
                    read_at=ts(at + rng.randint(HOUR, 3 * DAY)) if at < -4 * DAY else None, created_at=ts(at),
                    updated_at=ts(at))
    order['refunded'] = refunded_amount

# %%
# Pagos simulados, evaluación de riesgo, recibos, reseñas, reclamos y devoluciones
RETURNS_BY_ORDER = {}
delivered_sales = [(o, s) for o, s in SALE_ROWS if s['status'] == 'DELIVERED']
for index, (order, sale) in enumerate(sorted(rng.sample(delivered_sales, min(len(delivered_sales), max(4, N_ORDERS // 25))),
                                             key=lambda x: (x[0]['number'], str(x[1]['id']))), start=1):
    delivered_at = next(x for s, x in sale['steps'] if s == 'DELIVERED')
    detail = sale['details'][0]
    kind = rng.choice(['RETURN', 'RETURN', 'WARRANTY'])
    reason = rng.choice(['WRONG_FITMENT', 'DEFECTIVE_ON_ARRIVAL', 'NOT_AS_DESCRIBED', 'ORDERED_BY_MISTAKE']
                        if kind == 'RETURN' else ['FAILED_IN_USE', 'DEFECTIVE_ON_ARRIVAL'])
    requested = delivered_at + rng.randint(DAY, 6 * DAY)
    flow = rng.choice([['REQUESTED'], ['REQUESTED', 'REJECTED'], ['REQUESTED', 'APPROVED', 'IN_TRANSIT'],
                       ['REQUESTED', 'APPROVED', 'IN_TRANSIT', 'RECEIVED', 'RESOLVED']])
    times, t = [], requested
    for status in flow:
        times.append((status, t))
        t += rng.randint(6 * HOUR, 3 * DAY)
    times = [(s, x) for s, x in times if x <= NOW_MARGIN]
    if not times:
        continue
    RETURNS_BY_ORDER.setdefault(order['number'], []).append(
        {'order': order, 'sale': sale, 'detail': detail, 'kind': kind, 'reason': reason, 'times': times,
         'id': uid('return_request', index)})

for order in ORDERS:
    person, start = PEOPLE[order['buyer']], order['start']
    payment_id = uid('payment', order['number'])
    refunded = order['refunded']
    for ret in RETURNS_BY_ORDER.get(order['number'], []):
        if ret['times'][-1][0] == 'RESOLVED':
            ret['refund'] = money(ret['detail']['unit_price'] * ret['detail']['units'])
            refunded += ret['refund']
    full_refund = order['state'] == 'CANCELLED'
    add('payments', id=payment_id, order_id=order['id'],
        payment_method_id=ref('payment_method', rng.choice(PAYMENT_METHODS)),
        payment_status_id=ref('payment_status', 'REFUNDED' if full_refund else 'APPROVED'), provider='SIMULATED',
        provider_reference=str(uid('client_request', order['number'])), amount=order['total'], currency='COP',
        paid_at=ts(start), idempotency_key=f'DEMO-{uid("client_request", order["number"])}', attempt_number=1,
        environment='SANDBOX', failure_code=None, failure_message=None, failed_at=None, refunded_amount=refunded,
        created_at=ts(start), updated_at=ts(order['cancel_at'] if full_refund else start))
    add('payment_events', id=uid('payment_event', order['number'], 'APPROVED'), payment_id=payment_id,
        payment_status_id=ref('payment_status', 'APPROVED'), from_status_id=None, source='CHECKOUT',
        provider_event_id=None, provider_status='APPROVED', message='Pago simulado aprobado', occurred_at=tz(start),
        created_at=ts(start), updated_at=ts(start))
    if full_refund:
        add('payment_events', id=uid('payment_event', order['number'], 'REFUNDED'), payment_id=payment_id,
            payment_status_id=ref('payment_status', 'REFUNDED'), from_status_id=ref('payment_status', 'APPROVED'),
            source='ADMIN', provider_event_id=None, provider_status='REFUNDED',
            message='Reembolso simulado por cancelación', occurred_at=tz(order['cancel_at']),
            created_at=ts(order['cancel_at']), updated_at=ts(order['cancel_at']))
    score = Decimal(rng.randint(1, 450)) / Decimal(10000)
    decision = 'REVIEW' if rng.random() < 0.04 else 'ALLOW'
    add('payment_risk_assessments', payment_id=payment_id, order_id=order['id'], assessed_at=tz(start - 5),
        stage='PRE_AUTHORIZATION', risk_score=score, provider_risk_score=None, decision=decision,
        rules_triggered=Raw("ARRAY['DEMO_VELOCITY_OK']::text[]") if decision == 'ALLOW' else Raw("ARRAY['DEMO_NEW_DEVICE']::text[]"),
        signals={'demo': True, 'items': sum(len(s['details']) for s in order['sales'])}, model_version_id=None,
        ip_address=doc_ip(), device_hash=rand_hex(), outcome='LEGIT' if start < -30 * DAY else None,
        outcome_at=tz(start + 30 * DAY) if start < -30 * DAY else None,
        outcome_source='PROVIDER' if start < -30 * DAY else None,
        reviewed_by_account_id=root['account_id'] if decision == 'REVIEW' else None)
    add('receipts', id=uid('receipt', order['number']), order_id=order['id'], payment_id=payment_id,
        receipt_number=f'DEMO-R-{order["number"]:06d}', issued_at=ts(start + MINUTE), total=order['total'],
        created_at=ts(start + MINUTE), updated_at=ts(start + MINUTE))
    reviewed = set()
    for sale in order['sales']:
        delivered_at = next((x for s, x in sale['steps'] if s == 'DELIVERED'), None)
        if delivered_at is None or sale['store'] in reviewed or rng.random() > 0.45:
            continue
        reviewed.add(sale['store'])
        at = min(delivered_at + rng.randint(HOUR, 5 * DAY), NOW_MARGIN)
        rating = rng.choices([5, 4, 3, 2, 1], weights=[52, 30, 10, 5, 3])[0]
        add('store_reviews', id=uid('review', sale['store'], order['number']), store_id=STORE_INFO[sale['store']]['id'],
            person_id=person['person_id'], order_id=order['id'], rating=rating,
            comment={5: 'Excelente atención y el repuesto llegó a tiempo.', 4: 'Buen producto, la entrega tardó un poco.',
                     3: 'Cumplieron, aunque el empaque llegó maltratado.', 2: 'Demoraron más de lo prometido.',
                     1: 'Tuve que pedir cambio porque no era la referencia.'}[rating],
            created_at=ts(at), updated_at=ts(at))

# Reclamos (algunos enlazados a devoluciones)
CLAIMS = {}
claimable = sorted([(o, s) for o, s in SALE_ROWS if s['status'] in ('DELIVERED', 'CANCELLED')],
                   key=lambda x: (x[0]['number'], str(x[1]['id'])))
claim_targets = rng.sample(claimable, min(10, len(claimable)))
for rets in RETURNS_BY_ORDER.values():
    for ret in rets[:1]:
        if rng.random() < 0.5:
            claim_targets.append((ret['order'], ret['sale']))
for index, (order, sale) in enumerate(sorted(set((o['number'], str(s['id'])) for o, s in claim_targets)), start=1):
    order_obj = next(o for o in ORDERS if o['number'] == order)
    sale_obj = next(s for s in order_obj['sales'] if str(s['id']) == sale)
    at = min(max(x for _, x in sale_obj['steps']) + rng.randint(HOUR, 4 * DAY), NOW_MARGIN)
    reason, description = rng.choice([
        ('Producto no corresponde', 'La referencia recibida no coincide con la de mi vehículo.'),
        ('Demora en la entrega', 'El pedido tardó más de lo indicado por la tienda.'),
        ('Producto defectuoso', 'El repuesto llegó con un defecto de fábrica.'),
        ('Reembolso pendiente', 'La venta fue cancelada y quiero confirmar el reembolso.')])
    claim_id = uid('claim', index)
    CLAIMS[(order, sale)] = claim_id
    add('claims', id=claim_id, person_id=PEOPLE[order_obj['buyer']]['person_id'], order_id=order_obj['id'],
        store_id=STORE_INFO[sale_obj['store']]['id'], reason=reason, description=description,
        state_id=ref('state', rng.choice(['PENDING', 'CONFIRMED', 'REJECTED'])), created_at=ts(at), updated_at=ts(at))

# Devoluciones y garantías
for number in sorted(RETURNS_BY_ORDER):
    for ret in RETURNS_BY_ORDER[number]:
        order, sale, detail = ret['order'], ret['sale'], ret['detail']
        person = PEOPLE[order['buyer']]
        status_now, last_at = ret['times'][-1]
        times = dict(ret['times'])
        resolved = status_now == 'RESOLVED'
        rejected = status_now == 'REJECTED'
        add('return_requests', id=ret['id'], order_id=order['id'], sale_id=sale['id'],
            store_id=STORE_INFO[sale['store']]['id'], person_id=person['person_id'], request_type=ret['kind'],
            reason_code=ret['reason'], status=status_now,
            resolution='REFUND' if resolved else ('REJECTED' if rejected else None),
            refund_amount=ret.get('refund') if resolved else None, customer_vehicle_id=detail['vehicle'],
            odometer_km_at_failure=rng.randint(20_000, 150_000) if ret['kind'] == 'WARRANTY' else None,
            installed_on=None, failed_on=None, customer_comment='Solicitud de demostración.',
            claim_id=CLAIMS.get((number, str(sale['id']))), requested_at=tz(times['REQUESTED']),
            approved_at=tz(times['APPROVED']) if 'APPROVED' in times else None,
            received_at=tz(times['RECEIVED']) if 'RECEIVED' in times else None,
            resolved_at=tz(times['RESOLVED']) if resolved else None,
            resolved_by_account_id=staff_actor(sale['store']) if resolved else None,
            created_at=tz(times['REQUESTED']), updated_at=tz(last_at))
        item_id = uid('return_item', ret['id'])
        restock = 'RECEIVED' in times and ret['reason'] in ('ORDERED_BY_MISTAKE', 'NOT_AS_DESCRIBED', 'WRONG_FITMENT')
        movement_key = f'return:{item_id}'
        if restock:
            add_event(detail['row'], times['RECEIVED'], 'IN', detail['units'], 'CUSTOMER_RETURN', 'USER', 'RETURN_ITEM',
                      item_id, None, actor=staff_actor(sale['store']), key=movement_key)
        add('return_request_items', id=item_id, return_request_id=ret['id'], order_detail_id=detail['id'],
            units=detail['units'], item_condition='SEALED' if restock else ('DEFECTIVE' if 'DEFECT' in ret['reason'] else 'OPENED'),
            restocked=restock, inventory_movement_id=uid('movement', detail['row']['id'], movement_key) if restock else None,
            created_at=tz(times['REQUESTED']), updated_at=tz(last_at))
        previous = None
        for status, at in ret['times']:
            add('return_status_history', return_request_id=ret['id'], from_status=previous, to_status=status,
                changed_at=tz(at), actor_type='CUSTOMER' if status == 'REQUESTED' else 'STORE',
                actor_account_id=person['account_id'] if status == 'REQUESTED' else staff_actor(sale['store']),
                notes=None)
            previous = status

# %%
# Compras a proveedores: proveedores, productos por proveedor, órdenes de compra y recepciones
for code in STORE_CODES:
    info = STORE_INFO[code]
    store_products = [p for p in PRODUCTS if p['store'] == code]
    suppliers = []
    for n, supplier_name in enumerate(SUPPLIER_NAMES[:rng.randint(2, 3)], start=1):
        supplier_id = uid('supplier', code, n)
        created = info['created'] + 2 * DAY
        add('suppliers', id=supplier_id, store_id=info['id'], name=supplier_name, nit=f'DEMO-80000{n}{len(code)}',
            contact_email=f'proveedor{n}.{code.lower()}@demo.autopartco.test', contact_phone=fake_phone(),
            municipality_code=rng.choice(SUPPLIER_MUNICIPALITIES), default_lead_time_days=rng.randint(2, 10),
            payment_terms_days=rng.choice([0, 15, 30, 45]), active=True, created_at=tz(created), updated_at=tz(created))
        suppliers.append(supplier_id)
    supplier_of = {}
    for product in store_products:
        supplier_id = suppliers[int(product['id'].hex[:8], 16) % len(suppliers)]
        supplier_of[product['id']] = supplier_id
        add('supplier_products', id=uid('supplier_product', supplier_id, product['id']), supplier_id=supplier_id,
            product_id=product['id'], supplier_sku=f'PRV-{product["part"]}', last_unit_cost=None,
            min_order_qty=rng.choice([1, 2, 5, 10]), pack_size=rng.choice([1, 1, 2, 4]),
            declared_lead_time_days=rng.randint(2, 12), created_at=tz(product['created']), updated_at=tz(product['created']))
    inventory_rows = [r for r in INVENTORY if r['store'] == code]
    statuses = ['RECEIVED', 'RECEIVED', 'RECEIVED', 'PARTIALLY_RECEIVED', 'SENT', 'DRAFT', 'CANCELLED']
    for n in range(N_PURCHASE_ORDERS_PER_STORE):
        status = statuses[n % len(statuses)]
        branch = info['branches'][n % len(info['branches'])]
        supplier_id = suppliers[n % len(suppliers)]
        candidates = sorted((r for r in inventory_rows if r['branch']['id'] == branch['id']
                             and supplier_of[r['product']['id']] == supplier_id), key=lambda r: str(r['id']))
        if not candidates:
            continue
        created = -rng.randint(10, HISTORY_DAYS - 10) * DAY if status in ('RECEIVED', 'CANCELLED') else -rng.randint(2, 15) * DAY
        lines = rng.sample(candidates, min(len(candidates), rng.randint(2, 5)))
        lines = [r for r in lines if r['created'] < created]
        if not lines:
            continue
        po_id = uid('purchase_order', code, n)
        ordered = created + HOUR if status not in ('DRAFT', 'CANCELLED') else None
        expected = (ordered + rng.randint(3, 10) * DAY) if ordered is not None else None
        receipts = []
        if status in ('RECEIVED', 'PARTIALLY_RECEIVED'):
            first_receipt = ordered + rng.randint(2, 6) * DAY
            if first_receipt > NOW_MARGIN:
                status, receipts = 'SENT', []
            else:
                receipts = [first_receipt]
        items = []
        for line in lines:
            qty = rng.randint(5, 30)
            received = (qty if status == 'RECEIVED' else qty // 2) if receipts else 0
            items.append((line, qty, received, cost4(line['unit_cost'] * pct(0.97, 1.03))))
        subtotal = money(sum(Decimal(q) * c for _, q, _, c in items))
        add('purchase_orders', id=po_id, store_id=info['id'], branch_id=branch['id'], supplier_id=supplier_id,
            po_number=f'DEMO-OC-{code}-{n + 1:03d}', status=status, source='MANUAL', prediction_id=None, currency='COP',
            subtotal=subtotal, ordered_at=tz(ordered) if ordered is not None else None,
            expected_at=tz(expected) if expected is not None else None,
            first_received_at=tz(receipts[0]) if receipts else None,
            fully_received_at=tz(receipts[0]) if receipts and status == 'RECEIVED' else None,
            cancelled_at=tz(created + DAY) if status == 'CANCELLED' else None,
            created_by_account_id=info['owner']['account_id'], created_at=tz(created),
            updated_at=tz(receipts[0] if receipts else created))
        receipt_id = uid('purchase_receipt', po_id, 0) if receipts else None
        if receipts:
            add('purchase_receipts', id=receipt_id, purchase_order_id=po_id, branch_id=branch['id'],
                received_at=tz(receipts[0]), received_by_account_id=staff_actor(code),
                supplier_document_ref=f'FV-DEMO-{n + 1:04d}', created_at=tz(receipts[0]))
        for line, qty, received, unit_cost in items:
            item_id = uid('po_item', po_id, line['id'])
            add('purchase_order_items', id=item_id, purchase_order_id=po_id, product_id=line['product']['id'],
                inventory_id=line['id'], qty_ordered=qty, qty_received=received, unit_cost=unit_cost,
                created_at=tz(created), updated_at=tz(receipts[0] if receipts else created))
            if receipts and received > 0:
                receipt_item_id = uid('receipt_item', receipt_id, item_id)
                movement_key = f'purchase:{receipt_item_id}'
                add_event(line, receipts[0], 'IN', received, 'PURCHASE_RECEIPT', 'USER', 'PURCHASE_RECEIPT_ITEM',
                          receipt_item_id, None, actor=staff_actor(code), unit_cost=unit_cost, key=movement_key)
                add('purchase_receipt_items', id=receipt_item_id, purchase_receipt_id=receipt_id,
                    purchase_order_item_id=item_id, qty_received=received, qty_rejected=0, unit_cost=unit_cost,
                    inventory_movement_id=uid('movement', line['id'], movement_key), created_at=tz(receipts[0]))

# %%
# Ledger: saldo inicial, movimientos en orden y stock final que cuadra; historial de precios, reorden y snapshots
LOW_STOCK, ZERO_STOCK = [], []
for row in INVENTORY:
    row['events'].sort(key=lambda e: (e['at'], 0 if e['kind'] == 'IN' and e['reason'] == 'INITIAL_LOAD' else 1, e['key']))
    net, low = 0, 0
    for event in row['events']:
        net += event['qty'] if event['kind'] == 'IN' else -event['qty']
        low = min(low, net)
    initial = max(row['target'] - net, -low, 0)
    events = row['events']
    if initial > 0:
        events.insert(0, {'at': row['created'], 'kind': 'IN', 'qty': initial, 'reason': 'INITIAL_LOAD', 'source': 'SYSTEM',
                          'ref_type': None, 'ref_id': None, 'detail': None, 'actor': None, 'cost': row['unit_cost'],
                          'key': 'opening', 'notes': 'Saldo inicial de demostración',
                          'id': uid('movement', row['id'], 'opening')})
    stock, last_at = 0, None
    for event in events:
        at = event['at'] if last_at is None or event['at'] > last_at else last_at + 1   # instantes estrictamente crecientes
        event['at'] = last_at = at
        new = stock + event['qty'] if event['kind'] == 'IN' else stock - event['qty']
        assert new >= 0, f'Stock negativo en {row["id"]}'
        event['prev'], event['new'] = stock, new
        stock = new
    row['final'] = stock
    if row['product']['state'] == 'ACTIVE':
        (LOW_STOCK if stock in (1, 2) else ZERO_STOCK if stock == 0 else []).append(row)

for row in INVENTORY:
    add('inventory', id=row['id'], branch_id=row['branch']['id'], product_id=row['product']['id'], stock=row['final'],
        price=row['price'], reserved_stock=0, unit_cost=row['unit_cost'], last_counted_at=None,
        created_at=ts(row['created']), updated_at=ts(row['events'][-1]['at'] if row['events'] else row['created']))
    for event in row['events']:
        add('inventory_movements', id=event['id'], inventory_id=row['id'], order_detail_id=event['detail'],
            movement_type=event['kind'], quantity=event['qty'], previous_stock=event['prev'], new_stock=event['new'],
            reason_code=event['reason'], source=event['source'], actor_account_id=event['actor'],
            reference_type=event['ref_type'], reference_id=event['ref_id'], unit_cost=event['cost'],
            occurred_at=tz(event['at']), idempotency_key=f"DEMO:{event['id']}", notes=event['notes'],
            created_at=ts(event['at']))
    store_id = STORE_INFO[row['store']]['id']
    for index, (since, value) in enumerate(row['prices']):
        until = row['prices'][index + 1][0] if index + 1 < len(row['prices']) else None
        add('inventory_price_history', inventory_id=row['id'], store_id=store_id, branch_id=row['branch']['id'],
            product_id=row['product']['id'], list_price=value, unit_cost=row['unit_cost'], currency='COP',
            vat_rate=VAT_RATE, price_includes_vat=True, valid_from=tz(since),
            valid_to=tz(until) if until is not None else None,
            change_source='INITIAL_LOAD' if index == 0 else 'MANUAL',
            changed_by_account_id=None if index == 0 else staff_actor(row['store']),
            change_reason='Precio inicial de demostración' if index == 0 else 'Ajuste de precio (demo)',
            prediction_id=None, created_at=tz(since))
    reorder_point = rng.randint(2, 6)
    policies = [(row['created'] + DAY, None, reorder_point)]
    if rng.random() < 0.2 and row['created'] + 20 * DAY < NOW_MARGIN:
        change = rng.randint(row['created'] + 10 * DAY, NOW_MARGIN - DAY)
        policies = [(row['created'] + DAY, change, max(1, reorder_point - 1)), (change, None, reorder_point)]
    for index, (since, until, point) in enumerate(policies):
        add('inventory_reorder_policies', id=uid('reorder_policy', row['id'], index), inventory_id=row['id'],
            store_id=store_id, reorder_point=point, safety_stock=max(0, point - 2), reorder_quantity=rng.choice([6, 10, 12, 20]),
            max_stock=point + 40, review_period_days=7, source='RULE', prediction_id=None, model_version_id=None,
            valid_from=tz(since), valid_to=tz(until) if until is not None else None,
            created_by_account_id=STORE_INFO[row['store']]['owner']['account_id'], created_at=tz(since),
            updated_at=tz(until if until is not None else since))
    row['reorder_point'] = reorder_point

# Snapshot diario (día k = [ancla - k días, ancla - (k-1) días), fecha de Bogotá hoy - k)
for row in INVENTORY:
    product = row['product']
    for k in range(SNAPSHOT_DAYS, 0, -1):
        day_start, day_end = -k * DAY, -(k - 1) * DAY
        if row['created'] >= day_end:
            continue
        stock_start = 0
        for event in row['events']:
            if event['at'] < day_start:
                stock_start = event['new']
        level, minimum, zero_since, zero_minutes = stock_start, stock_start, (day_start if stock_start == 0 else None), 0
        units_in = units_out = units_sold = 0
        for event in row['events']:
            if day_start <= event['at'] < day_end:
                if event['kind'] == 'IN':
                    units_in += event['qty']
                else:
                    units_out += event['qty']
                    units_sold += event['qty'] if event['reason'] == 'MARKETPLACE_SALE' else 0
                level = event['new']
                minimum = min(minimum, level)
                if level == 0 and zero_since is None:
                    zero_since = event['at']
                elif level > 0 and zero_since is not None:
                    zero_minutes += (event['at'] - zero_since) // MINUTE
                    zero_since = None
        if zero_since is not None:
            zero_minutes += (day_end - max(zero_since, day_start)) // MINUTE
        list_price = price_at(row, day_end - 1)
        discount, _ = best_promotion(product, list_price, day_end - 1)
        add('inventory_daily_snapshots', snapshot_date=day(-k), inventory_id=row['id'],
            store_id=STORE_INFO[row['store']]['id'], branch_id=row['branch']['id'], product_id=product['id'],
            on_hand=level, reserved=0, in_transit=None, min_on_hand=minimum, stockout_minutes=min(1440, zero_minutes),
            list_price=list_price, effective_price=money(list_price - discount), unit_cost=row['unit_cost'],
            is_published=product['state'] == 'ACTIVE', reorder_point=row['reorder_point'], units_in=units_in,
            units_out=units_out, units_sold=units_sold, captured_at=tz(day_end + 5 * MINUTE if day_end + 5 * MINUTE <= NOW_MARGIN else NOW_MARGIN))

# %%
# Navegación (tracking): sesiones, búsquedas, eventos de producto, eventos genéricos y vínculos de identidad
for n in range(N_ANON_SESSIONS):
    new_session(None, -rng.randint(HOUR, HISTORY_DAYS * DAY), rng.choice(BUYER_MUNICIPALITIES))
published_rows = [r for r in sellable]
for session in SESSIONS:
    start = session['start']
    t = start
    events = []
    for s_index in range(rng.randint(1, 3)):
        t += rng.randint(20, 240)
        if t > NOW_MARGIN:
            break
        search_id = uid('search_event', session['id'], s_index)
        vehicle_search = rng.random() < 0.35
        if vehicle_search:
            model = rng.choice(MODELS)
            label, year, _ = rng.choice(model['versions'])
            matching = [r for r in published_rows if any(m is model for m in r['product']['fits'])]
            query, search_type = None, 'VEHICLE'
        else:
            query = rng.choice(QUERIES)
            words = norm(query).split()[:2]
            matching = [r for r in published_rows if all(w[:5] in norm(r['product']['name']) for w in words)]
            search_type = 'TEXT'
        results = len({r['product']['id'] for r in matching})
        add('tracking.search_events', event_id=search_id, occurred_at=tz(t), received_at=tz(t + 1),
            session_id=session['id'], anonymous_id=session['anon'], subject_key=session['subject'],
            surface='MARKETPLACE', search_type=search_type, query_text=query,
            query_hash=query_hash(query) if query else None,
            make_id=ref('make', model['make']) if vehicle_search else None,
            model_id=ref('model', model['make'], model['name']) if vehicle_search else None,
            version_id=ref('version', model['make'], model['name'], label) if vehicle_search else None,
            vehicle_year=year if vehicle_search else None, category_id=None,
            geo_municipality_code=session['mun'], filters={}, sort='relevance', page=1, results_count=results,
            compatible_results_count=results if vehicle_search else None, latency_ms=rng.randint(40, 600))
        for position, row in enumerate(rng.sample(matching, min(len(matching), rng.randint(0, 2))), start=1):
            t += rng.randint(5, 90)
            if t > NOW_MARGIN:
                break
            for event_type in ('SEARCH_RESULT_CLICK', 'PRODUCT_VIEW'):
                list_price = price_at(row, t)
                discount, promo = best_promotion(row['product'], list_price, t)
                add('tracking.product_events', event_id=uid('product_event', search_id, position, event_type),
                    occurred_at=tz(t), received_at=tz(t + 1), session_id=session['id'], anonymous_id=session['anon'],
                    subject_key=session['subject'], event_type=event_type, product_id=row['product']['id'],
                    inventory_id=row['id'], store_id=STORE_INFO[row['store']]['id'], branch_id=row['branch']['id'],
                    quantity=None, displayed_price=money(list_price - discount), displayed_list_price=list_price,
                    promotion_id=promo['id'] if promo else None, availability_shown='IN_STOCK',
                    compatibility_status_shown='SELLER_DECLARED' if vehicle_search else 'UNKNOWN',
                    search_event_id=search_id, result_position=position, cart_id=None)
                t += 1
    add('tracking.events', event_id=uid('event', session['id'], 'page_view'), occurred_at=tz(start),
        received_at=tz(start + 1), session_id=session['id'], anonymous_id=session['anon'], subject_key=session['subject'],
        event_name='page_view', surface='MARKETPLACE', store_id=None, product_id=None, order_id=None,
        properties={'path_template': '/', 'referrer_host': session['referrer'] or 'directo'})
    session['last'] = max(t, start)

for order in ORDERS:
    session, start = order['session'], order['start']
    items = sum(len(s['details']) for s in order['sales'])
    for name, at, props in [('checkout_started', start - 2 * MINUTE, {'items_count': items, 'cart_value_band': 'demo'}),
                            ('checkout_completed', start, {'items_count': items})]:
        add('tracking.events', event_id=uid('event', order['id'], name), occurred_at=tz(at), received_at=tz(at + 1),
            session_id=session['id'], anonymous_id=session['anon'], subject_key=session['subject'], event_name=name,
            surface='MARKETPLACE', store_id=None, product_id=None, order_id=order['id'] if name == 'checkout_completed' else None,
            properties=props)
    session['last'] = max(session['last'], start)
for cart in CARTS:
    session = cart['session']
    for index, (row, units) in enumerate(cart['items']):
        at = cart['start'] + index * MINUTE
        list_price = price_at(row, at)
        add('tracking.product_events', event_id=uid('product_event', cart['id'], index, 'ADD_TO_CART'), occurred_at=tz(at),
            received_at=tz(at + 1), session_id=session['id'], anonymous_id=session['anon'], subject_key=session['subject'],
            event_type='ADD_TO_CART', product_id=row['product']['id'], inventory_id=row['id'],
            store_id=STORE_INFO[row['store']]['id'], branch_id=row['branch']['id'], quantity=units,
            displayed_price=list_price, displayed_list_price=list_price, promotion_id=None, availability_shown='IN_STOCK',
            compatibility_status_shown='UNKNOWN', search_event_id=None, result_position=None, cart_id=cart['id'])
        session['last'] = max(session['last'], at)

for session in SESSIONS:
    m = MUNICIPALITIES[session['mun']]
    utm_source, utm_medium, utm_campaign = session['utm']
    add('tracking.sessions', session_id=session['id'], started_at=tz(session['start']),
        last_seen_at=tz(max(session['last'], session['start'])), anonymous_id=session['anon'],
        subject_key=session['subject'], surface='MARKETPLACE', device_type=session['device'], os_family=session['os'],
        browser_family=session['browser'], app_version=None, utm_source=utm_source, utm_medium=utm_medium,
        utm_campaign=utm_campaign, utm_term=None, utm_content=None, referrer_host=session['referrer'],
        landing_path='/', geo_department_code=m['dept_code'], geo_municipality_code=session['mun'], is_bot=False)
links = {}
for session in SESSIONS:
    if session['subject']:
        key = (session['anon'], session['subject'])
        first, last = links.get(key, (session['start'], session['start']))
        links[key] = (min(first, session['start']), max(last, session['start']))
for (anon, subject), (first, last) in sorted(links.items(), key=lambda x: (str(x[0][0]), str(x[0][1]))):
    add('tracking.identity_links', anonymous_id=anon, subject_key=subject, first_linked_at=tz(first),
        last_linked_at=tz(last))

add('audit_logs', id=uid('audit', 'seed-marker'), account_id=None, action='DEMO_SEED_APPLIED', entity_type='SEED',
    entity_id=uid('seed', SEED_VERSION), metadata={'seed': SEED_VERSION, 'data_origin': DATA_ORIGIN},
    created_at=ts(NOW_MARGIN), updated_at=ts(NOW_MARGIN))
SEED_MARKER_ID = uid('audit', 'seed-marker')
print({table: len(rows) for table, rows in sorted(ROWS.items())})

# %%
# Generación del SQL
TABLE_ORDER = [
    'geo_municipalities', 'categories', 'manufacturers', 'specifications', 'units', 'units_specifications', 'make',
    'model', 'versions', 'compatibilities', 'locations', 'persons', 'accounts', 'privacy.subject_pseudonyms',
    'person_consents', 'seller_plans', 'store', 'branches', 'roles', 'roles_permissions', 'collaborators',
    'roles_collaborators', 'branches_collaborators', 'seller_collaborator_invitations', 'seller_applications',
    'seller_application_documents', 'seller_payments', 'person_addresses', 'customer_vehicles',
    'vehicle_odometer_readings', 'products', 'products_compatibilies', 'products_specifications', 'inventory',
    'inventory_price_history', 'inventory_reorder_policies', 'promotions', 'orders', 'order_status_history',
    'order_addresses', 'order_attributions', 'sales', 'sale_status_history', 'order_details', 'carts', 'cart_items',
    'stock_reservations', 'payments', 'payment_events', 'payment_risk_assessments', 'receipts', 'shipments',
    'shipment_events', 'store_reviews', 'claims', 'return_requests', 'suppliers', 'supplier_products',
    'purchase_orders', 'purchase_order_items', 'purchase_receipts', 'inventory_movements', 'purchase_receipt_items',
    'return_request_items', 'return_status_history', 'inventory_daily_snapshots', 'notifications', 'account_tokens',
    'account_sessions', 'login_attempts', 'tracking.sessions', 'tracking.search_events', 'tracking.product_events',
    'tracking.events', 'tracking.identity_links', 'audit_logs']
assert set(ROWS) <= set(TABLE_ORDER), set(ROWS) - set(TABLE_ORDER)
PARTITIONED = ['tracking.sessions', 'tracking.search_events', 'tracking.product_events', 'tracking.events',
               'public.inventory_daily_snapshots']

LOOKUPS = [
    # nombre, parámetros, tabla, condición
    ('state', ['p_code'], 'states', 'code = p_code AND deleted_at IS NULL'),
    ('account_type', ['p_code'], 'types_accounts', 'code = p_code'),
    ('permission', ['p_code'], 'permissions', 'code = p_code'),
    ('payment_method', ['p_code'], 'payment_methods', 'code = p_code'),
    ('payment_status', ['p_code'], 'payment_statuses', 'code = p_code'),
    ('shipment_status', ['p_code'], 'shipment_statuses', 'code = p_code'),
    ('plan', ['p_code'], 'seller_plans', 'upper(code) = upper(p_code)'),
    ('category', ['p_code'], 'categories', 'lower(code) = lower(p_code)'),
    ('manufacturer', ['p_name'], 'manufacturers', 'lower(name) = lower(p_name)'),
    ('specification', ['p_name'], 'specifications', 'name = p_name'),
    ('unit', ['p_code'], 'units', 'code = p_code'),
    ('make', ['p_make'], 'make', 'name = p_make'),
]


def lookup_function(name, params, table, condition):
    args = ', '.join(f'{p} text' for p in params)
    keys = " || '/' || ".join(params)
    return (f"CREATE FUNCTION pg_temp.demo_{name}({args}) RETURNS uuid LANGUAGE plpgsql STABLE AS $f$\n"
            f"DECLARE r uuid;\nBEGIN\n    SELECT id INTO r FROM {table} WHERE {condition};\n"
            f"    IF r IS NULL THEN RAISE EXCEPTION 'demo seed: falta {table} %', {keys}; END IF;\n    RETURN r;\nEND $f$;")


HELPERS = """-- Ancla temporal: medianoche de hoy en Bogotá. Todo el historial ocurre antes del ancla.
CREATE FUNCTION pg_temp.demo_tstz(p_seconds bigint) RETURNS timestamptz LANGUAGE sql STABLE AS $f$
    SELECT (date_trunc('day', now() AT TIME ZONE 'America/Bogota') AT TIME ZONE 'America/Bogota')
           + make_interval(secs => p_seconds)
$f$;
-- Columnas TIMESTAMP heredadas de init.sql: hora UTC (lo que asume public.legacy_ts()).
CREATE FUNCTION pg_temp.demo_ts(p_seconds bigint) RETURNS timestamp LANGUAGE sql STABLE AS $f$
    SELECT pg_temp.demo_tstz(p_seconds) AT TIME ZONE 'UTC'
$f$;
CREATE FUNCTION pg_temp.demo_date(p_days integer) RETURNS date LANGUAGE sql STABLE AS $f$
    SELECT (now() AT TIME ZONE 'America/Bogota')::date + p_days
$f$;
CREATE FUNCTION pg_temp.demo_model(p_make text, p_model text) RETURNS uuid LANGUAGE plpgsql STABLE AS $f$
DECLARE r uuid;
BEGIN
    SELECT mo.id INTO r FROM model mo JOIN make mk ON mk.id = mo.make_id WHERE mk.name = p_make AND mo.name = p_model;
    IF r IS NULL THEN RAISE EXCEPTION 'demo seed: falta model %/%', p_make, p_model; END IF;
    RETURN r;
END $f$;
CREATE FUNCTION pg_temp.demo_version(p_make text, p_model text, p_version text) RETURNS uuid LANGUAGE plpgsql STABLE AS $f$
DECLARE r uuid;
BEGIN
    SELECT v.id INTO r FROM versions v JOIN model mo ON mo.id = v.model_id JOIN make mk ON mk.id = mo.make_id
    WHERE mk.name = p_make AND mo.name = p_model AND v.version = p_version;
    IF r IS NULL THEN RAISE EXCEPTION 'demo seed: falta versions %/%/%', p_make, p_model, p_version; END IF;
    RETURN r;
END $f$;
CREATE FUNCTION pg_temp.demo_compat(p_make text, p_model text, p_version text) RETURNS uuid LANGUAGE plpgsql STABLE AS $f$
DECLARE r uuid;
BEGIN
    SELECT c.id INTO r FROM compatibilities c
    JOIN make mk ON mk.id = c.make_id JOIN model mo ON mo.id = c.model_id JOIN versions v ON v.id = c.version_id
    WHERE mk.name = p_make AND mo.name = p_model AND v.version = p_version;
    IF r IS NULL THEN RAISE EXCEPTION 'demo seed: falta compatibilities %/%/%', p_make, p_model, p_version; END IF;
    RETURN r;
END $f$;
CREATE FUNCTION pg_temp.demo_unit_spec(p_spec text, p_unit text) RETURNS uuid LANGUAGE plpgsql STABLE AS $f$
DECLARE r uuid;
BEGIN
    SELECT us.id INTO r FROM units_specifications us
    JOIN specifications s ON s.id = us.specification_id JOIN units u ON u.id = us.unit_id
    WHERE s.name = p_spec AND u.code = p_unit;
    IF r IS NULL THEN RAISE EXCEPTION 'demo seed: falta units_specifications %/%', p_spec, p_unit; END IF;
    RETURN r;
END $f$;"""


def sql_value(value):
    if value is None:
        return 'NULL'
    if isinstance(value, Raw):
        return value.sql
    if isinstance(value, bool):
        return 'TRUE' if value else 'FALSE'
    if isinstance(value, int):
        return str(value)
    if isinstance(value, Decimal):
        return format(value, 'f')
    if isinstance(value, uuid.UUID):
        return f"'{value}'"
    if isinstance(value, (dict, list)):
        return sql_str(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')))
    if isinstance(value, str):
        return sql_str(value)
    raise TypeError(f'Tipo no permitido en el seed (¿float?): {type(value)} {value!r}')


def insert_sql(table, rows, chunk=250):
    columns = list(rows[0])
    for r in rows:
        assert list(r) == columns, f'{table}: columnas inconsistentes {list(r)} vs {columns}'
    parts = []
    for start in range(0, len(rows), chunk):
        values = ',\n'.join('    (' + ', '.join(sql_value(r[c]) for c in columns) + ')' for r in rows[start:start + chunk])
        parts.append(f'INSERT INTO {table} ({", ".join(columns)}) VALUES\n{values}\nON CONFLICT DO NOTHING;')
    return '\n'.join(parts)


def partitions_sql():
    months = (HISTORY_DAYS + 60) // 30 + 3
    lines = [f"SELECT public.ensure_monthly_partitions('{t}', pg_temp.demo_date({-(HISTORY_DAYS + 45)}), {months});"
             for t in PARTITIONED]
    return '-- Particiones mensuales que cubren todo el historial (ninguna fila cae en la default).\n' + '\n'.join(lines)


def owner_block():
    store_ids = ', '.join(sql_value(STORE_INFO[s[0]]['id']) for s in ACTIVE_STORES)
    return f"""-- ------------------------------------------------------------------------------------------------
-- Dueño real opcional: enlaza una cuenta YA REGISTRADA en la app como OWNER de los almacenes demo activos.
--   psql ... -v owner_email=tu-correo@dominio.com -f demo_seed.sql
-- Sin la variable no se toca ninguna cuenta real. Se puede repetir con otro correo.
-- ------------------------------------------------------------------------------------------------
\\if :{{?owner_email}}
SELECT set_config('autopartco.demo_owner_email', :'owner_email', true);
\\else
\\echo 'owner_email no definido: no se enlaza ninguna cuenta real a los almacenes demo.'
SELECT set_config('autopartco.demo_owner_email', '', true);
\\endif

DO $owner$
DECLARE
    v_email text := lower(trim(current_setting('autopartco.demo_owner_email', true)));
    v_account uuid;
    v_person uuid;
    v_type text;
    v_store uuid;
    v_collab uuid;
BEGIN
    IF v_email IS NULL OR v_email = '' THEN
        RETURN;
    END IF;
    SELECT a.id, a.id_person, t.code INTO v_account, v_person, v_type
    FROM accounts a JOIN types_accounts t ON t.id = a.type_id
    WHERE lower(a.email) = v_email AND a.deleted_at IS NULL;
    IF v_account IS NULL THEN
        RAISE EXCEPTION 'demo seed: no existe una cuenta registrada con el correo %', v_email;
    END IF;
    FOREACH v_store IN ARRAY ARRAY[{store_ids}]::uuid[] LOOP
        INSERT INTO collaborators (person_id, collab_store_id, state)
        VALUES (v_person, v_store, pg_temp.demo_state('ACTIVE'))
        ON CONFLICT (person_id, collab_store_id) DO NOTHING;
        SELECT id INTO v_collab FROM collaborators WHERE person_id = v_person AND collab_store_id = v_store;
        INSERT INTO roles_collaborators (collaborator_id, role_id)
        SELECT v_collab, r.id FROM roles r WHERE r.store_id = v_store AND r.code = 'OWNER'
        ON CONFLICT DO NOTHING;
        INSERT INTO branches_collaborators (collaborator_id, branch_id)
        SELECT v_collab, b.id FROM branches b WHERE b.store_id = v_store
        ON CONFLICT DO NOTHING;
    END LOOP;
    -- Igual que CompleteStoreSetup: una cuenta CUSTOMER pasa a STORE_MANAGER. ROOT no se toca.
    IF v_type = 'CUSTOMER' THEN
        UPDATE accounts SET type_id = pg_temp.demo_account_type('STORE_MANAGER') WHERE id = v_account;
    END IF;
    RAISE NOTICE 'demo seed: % es OWNER de los almacenes demo activos (cierra sesión y vuelve a entrar).', v_email;
END $owner$;

-- Convertir en ROOT una cuenta YA REGISTRADA (requiere aprobación; descomenta y cambia el correo).
-- Después hay que cerrar sesión y volver a entrar para que el JWT lleve ROLE_ROOT.
-- UPDATE accounts
-- SET type_id = (SELECT id FROM types_accounts WHERE code = 'ROOT')
-- WHERE lower(email) = lower('tu-correo@dominio.com');"""


def assertions_block():
    demo_inventory = "SELECT i.* FROM inventory i JOIN products p ON p.id = i.product_id WHERE p.sku LIKE 'DEMO-%'"
    counts = {
        'products': ("SELECT count(*) FROM products WHERE sku LIKE 'DEMO-%'", len(ROWS['products'])),
        'inventory': (f"SELECT count(*) FROM ({demo_inventory}) x", len(ROWS['inventory'])),
        'persons': ("SELECT count(*) FROM persons WHERE identification LIKE 'DEMO-%'", len(ROWS['persons'])),
        'orders': ("SELECT count(*) FROM orders o JOIN persons p ON p.id = o.person_id WHERE p.identification LIKE 'DEMO-%'",
                   len(ROWS['orders'])),
        'order_details': ("SELECT count(*) FROM order_details d JOIN orders o ON o.id = d.order_id JOIN persons p "
                          "ON p.id = o.person_id WHERE p.identification LIKE 'DEMO-%'", len(ROWS['order_details'])),
        'ledger_descuadrado': (f"""SELECT count(*) FROM ({demo_inventory}) i
        LEFT JOIN LATERAL (SELECT new_stock FROM inventory_movements WHERE inventory_id = i.id
                           ORDER BY occurred_at DESC, created_at DESC LIMIT 1) m ON true
        WHERE i.stock IS DISTINCT FROM coalesce(m.new_stock, 0)""", 0),
    }
    for t in PARTITIONED:
        counts[f'{t}_default'] = (f'SELECT count(*) FROM {t}_default', 0)
    checks = '\n'.join(
        f"    SELECT ({sql}) INTO n;\n    IF n <> {expected} THEN RAISE EXCEPTION 'demo seed: {name} = % (se esperaba {expected})', n; END IF;"
        for name, (sql, expected) in counts.items())
    return f"DO $check$\nDECLARE n bigint;\nBEGIN\n{checks}\nEND $check$;"


def sources_header():
    lines = []
    for meta in SOURCES:
        updated = meta.get('rowsUpdatedAt')
        updated_text = time.strftime('%Y-%m-%d', time.gmtime(int(updated))) if updated else 'desconocida'
        lines.append(f"--   - {meta.get('name')} ({SOCRATA_BASE}/d/{meta['id']}), datos del {updated_text}, "
                     f"licencia {meta.get('license') or 'CC BY-SA 4.0'}.")
    return '\n'.join(lines)


def data_fingerprint():
    digest = hashlib.sha256()
    for path in sorted(CACHE_DIR.glob('*.json')):
        digest.update(path.name.encode())
        digest.update(path.read_bytes().replace(b'\r\n', b'\n'))
    return digest.hexdigest()[:16]


def stock_lines(rows):
    rows = sorted(rows, key=lambda r: (r['store'], r['product']['part'], r['branch']['name']))[:8]
    return '\n'.join(f"--   {r['store']} · {r['branch']['name']} · {r['product']['sku']} · stock {r['final']}" for r in rows)


def build_sql():
    header = f"""-- =============================================================================================
-- AUTOPARTCO - DATOS DE DEMOSTRACIÓN (B4) · {SEED_VERSION} · data_origin = {DATA_ORIGIN}
-- Generado por database/seeds/demo/generate_demo_seed.py / .ipynb. No editar a mano.
-- Huella de los datos de origen: {data_fingerprint()} · semilla aleatoria {RANDOM_SEED}
--
-- Fuentes (datos abiertos de Colombia, datos.gov.co, licencia CC BY-SA 4.0; ver README.md, sección NOTICE):
{sources_header()}
--   Vehículos: marcas, líneas, años y cilindrajes reales, agregados en el servidor (sin placas, Ley 1581 de 2012).
--   Geografía: municipios y coordenadas (centroide municipal) de la DIVIPOLA del DANE.
-- Sintético: repuestos (referencias APC-, no son números OEM), almacenes, personas, pedidos, pagos y navegación.
-- Personas y correos (@demo.autopartco.test) ficticios. Sus cuentas NO pueden iniciar sesión.
--
-- Esquema de destino: database/init.sql -> todas las V* de database/migrations -> R__ -> este archivo.
-- Uso (solo bases de desarrollo o demo; nunca en producción):
--   PGCLIENTENCODING=UTF8 psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f database/seeds/demo/demo_seed.sql
--   ... -v owner_email=tu-correo@dominio.com   (opcional: enlaza tu cuenta como OWNER de los almacenes demo)
-- Requiere psql (usa \\if y \\gset). Idempotente: si ya se aplicó, no inserta nada (marcador en audit_logs).
-- Las fechas son relativas a la medianoche de Bogotá del día en que se aplica (pg_temp.demo_tstz).
--
-- Para probar el 409 del checkout (stock 1 o 2 tras los pedidos históricos):
{stock_lines(LOW_STOCK)}
-- Sin stock en esa sucursal (no aparecen en el marketplace por esa sucursal):
{stock_lines(ZERO_STOCK)}
-- =============================================================================================

\\set ON_ERROR_STOP on
SET client_encoding = 'UTF8';
BEGIN;
-- Los historiales (ledger, precios, estados) los escribe este script con sus fechas reales: se apagan los
-- triggers puente de V202610141025 solo en esta transacción (README de migraciones, §5).
SET LOCAL autopartco.capture_managed_by_app = 'on';
SET LOCAL autopartco.actor_type = 'SYSTEM';
"""
    out = [header, '\n'.join(lookup_function(*spec) for spec in LOOKUPS), HELPERS,
           f"SELECT EXISTS (SELECT 1 FROM audit_logs WHERE id = '{SEED_MARKER_ID}') AS demo_seed_applied \\gset",
           "\\if :demo_seed_applied\n\\echo 'El seed demo ya estaba aplicado: no se insertan datos (idempotente).'\n\\else",
           partitions_sql()]
    for table in TABLE_ORDER:
        if ROWS.get(table):
            out.append(f'-- {table}: {len(ROWS[table])} filas')
            out.append(insert_sql(table, ROWS[table]))
    out.append(assertions_block())
    out.append('\\endif')
    out.append(owner_block())
    out.append('COMMIT;')
    out.append(f"""-- Refresco de analytics (fuera de la transacción). Las funciones solo agregan data_origin = 'REAL':
-- con data_origin = {DATA_ORIGIN} los hechos de la demo quedan vacíos a propósito (ver README).
SELECT analytics.refresh_fact_sales_daily(current_date - {HISTORY_DAYS + 5}, current_date - 1);
SELECT analytics.refresh_fact_order_fulfillment(now() - interval '{HISTORY_DAYS + 5} days');
SELECT analytics.refresh_fact_unmet_demand_daily(current_date - {HISTORY_DAYS + 5}, current_date - 1);
SELECT analytics.refresh_fact_vehicle_demand_daily(current_date - {HISTORY_DAYS + 5}, current_date - 1);
SELECT analytics.refresh_fact_search_funnel_daily(current_date - {HISTORY_DAYS + 5}, current_date - 1);
SELECT analytics.refresh_fact_returns_daily(current_date - {HISTORY_DAYS + 5}, current_date - 1);
REFRESH MATERIALIZED VIEW analytics.mv_inventory_rotation_monthly;
REFRESH MATERIALIZED VIEW analytics.mv_customer_cohorts;
REFRESH MATERIALIZED VIEW analytics.mv_customer_rfm;
""")
    return '\n\n'.join(out)


SQL = build_sql()
OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
OUT_PATH.write_text(SQL, encoding='utf-8', newline='\n')
SUMMARY = {table: len(ROWS.get(table, [])) for table in TABLE_ORDER}
SUMMARY_PATH = OUT_PATH.with_suffix('.summary.json')
SUMMARY_PATH.write_text(json.dumps({
    'seed_version': SEED_VERSION, 'data_origin': DATA_ORIGIN, 'makes': len(MAKES), 'models': len(MODELS),
    'versions': sum(len(m['versions']) for m in MODELS), 'products': len(PRODUCTS), 'part_numbers': sum(part_seq.values()),
    'stores': len(STORES), 'active_stores': len(ACTIVE_STORES), 'branches': len(ROWS['branches']),
    'inventory': len(INVENTORY), 'orders': len(ORDERS), 'rows': SUMMARY}, ensure_ascii=False, indent=1, sort_keys=True),
    encoding='utf-8', newline='\n')
print(f'{OUT_PATH}: {len(SQL.encode("utf-8")):,} bytes · sha256 {hashlib.sha256(SQL.encode("utf-8")).hexdigest()}')

# %%
# Descarga (en Colab)
try:
    from google.colab import files  # type: ignore
    files.download(str(OUT_PATH))
except ImportError:
    pass
