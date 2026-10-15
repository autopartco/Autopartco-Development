"""Verificación estática de demo_seed.sql contra el esquema migrado (sin tocar ninguna base de datos).

Uso:
    python database/seeds/demo/verify_demo_seed.py [ruta/demo_seed.sql] [--report reporte.md]

Construye el esquema final leyendo database/init.sql y todas las V*.sql de database/migrations/ en orden
(sin pending-approval/) y comprueba:
  1. Tablas y columnas de cada INSERT; que no se inserte en columnas generadas ni IDENTITY.
  2. Tipo temporal correcto: demo_ts -> TIMESTAMP, demo_tstz -> TIMESTAMPTZ, demo_date -> DATE.
  3. Columnas NOT NULL sin DEFAULT presentes y no nulas.
  4. Integridad referencial: cada FK apunta a una fila del mismo script o a una semilla de init.sql, V* o R__.
  5. Los CHECK relevantes de init.sql y de las V* (y unicidades) sobre las filas generadas.
  6. Invariantes de negocio: totales, pagos, ledger, transiciones de estado, coherencia de almacén.
  7. Informe de filas por tabla de todos los esquemas, con el motivo de las vacías.
Sale con código 1 si encuentra algún error.
"""
import json
import re
import sys
from collections import Counter, defaultdict
from decimal import Decimal
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
INIT = REPO / 'database' / 'init.sql'
MIGRATIONS = REPO / 'database' / 'migrations'
ERRORS, WARNINGS = [], []


def error(message):
    ERRORS.append(message)


# --------------------------------------------------------------------------------------------------------------
# 1. Esquema final: init.sql + V*.sql
# --------------------------------------------------------------------------------------------------------------
def strip_comments(sql):
    return re.sub(r'--[^\n]*', '', sql)


def qualify(name):
    name = name.strip().strip('"').lower()
    return name if '.' in name else 'public.' + name


def matching_paren(text, start):
    depth, i, in_str = 0, start, False
    while i < len(text):
        c = text[i]
        if in_str:
            if c == "'":
                if text[i + 1:i + 2] == "'":
                    i += 1          # comilla escapada ''
                else:
                    in_str = False
        elif c == "'":
            in_str = True
        elif c == '(':
            depth += 1
        elif c == ')':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    raise ValueError('paréntesis sin cerrar')


def split_top(text, sep=','):
    parts, depth, cur, in_str, i = [], 0, [], False, 0
    while i < len(text):
        c = text[i]
        if in_str:
            cur.append(c)
            if c == "'":
                if text[i + 1:i + 2] == "'":
                    cur.append("'")
                    i += 1
                else:
                    in_str = False
        elif c == "'":
            in_str = True
            cur.append(c)
        elif c in '([':
            depth += 1
            cur.append(c)
        elif c in ')]':
            depth -= 1
            cur.append(c)
        elif c == sep and depth == 0:
            parts.append(''.join(cur))
            cur = []
        else:
            cur.append(c)
        i += 1
    if ''.join(cur).strip():
        parts.append(''.join(cur))
    return parts


def norm_type(raw):
    t = raw.lower()
    if 'timestamptz' in t or 'with time zone' in t:
        return 'timestamptz'
    if t.startswith('timestamp'):
        return 'timestamp'
    for prefix in ('varchar', 'character varying'):
        if t.startswith(prefix):
            return 'varchar'
    for key in ('uuid', 'date', 'text[]', 'text', 'jsonb', 'boolean', 'bigint', 'integer', 'smallint', 'numeric',
                'decimal', 'char', 'inet', 'tsvector', 'serial'):
        if t.startswith(key):
            return {'decimal': 'numeric', 'serial': 'integer'}.get(key, key)
    return t.split()[0] if t else t


class Column:
    def __init__(self, name, definition):
        self.name = name
        d = definition.lower()
        self.type = norm_type(definition.strip())
        self.not_null = 'not null' in d or 'primary key' in d
        self.has_default = 'default' in d or 'serial' in d
        self.generated = 'generated always as (' in d.replace('  ', ' ') and 'identity' not in d
        self.identity = 'as identity' in d
        m = re.search(r'references\s+([\w.]+)\s*\(\s*(\w+)\s*\)', d)
        self.fk = (qualify(m.group(1)), m.group(2)) if m else None


SCHEMA = defaultdict(dict)        # tabla -> {columna: Column}
FKS = defaultdict(dict)           # tabla -> {columna: (tabla_ref, columna_ref)}
PARTITIONED = set()
SEEDS = defaultdict(set)          # (tabla, columna) -> valores sembrados por init/V/R


def parse_create_tables(sql):
    for m in re.finditer(r'CREATE TABLE IF NOT EXISTS\s+([\w.]+)\s*\(', sql, re.I):
        table = qualify(m.group(1))
        end = matching_paren(sql, m.end() - 1)
        body = sql[m.end():end]
        tail = sql[end:end + 80].upper()
        if 'PARTITION BY' in tail:
            PARTITIONED.add(table)
        for part in split_top(body):
            part = ' '.join(part.split())
            head = part.split(' ', 1)[0].upper()
            if head in ('CONSTRAINT', 'PRIMARY', 'UNIQUE', 'CHECK', 'FOREIGN', 'EXCLUDE'):
                fk = re.search(r'FOREIGN KEY\s*\((\w+)\)\s*REFERENCES\s+([\w.]+)\s*\(\s*(\w+)\s*\)', part, re.I)
                if fk:
                    FKS[table][fk.group(1).lower()] = (qualify(fk.group(2)), fk.group(3).lower())
                continue
            name, _, definition = part.partition(' ')
            column = Column(name.lower(), definition)
            SCHEMA[table][column.name] = column
            if column.fk:
                FKS[table][column.name] = column.fk
    for m in re.finditer(r'CREATE TABLE IF NOT EXISTS\s+([\w.]+)\s+PARTITION OF', sql, re.I):
        pass


def parse_alters(sql):
    for m in re.finditer(r'ALTER TABLE\s+(?:IF EXISTS\s+)?(?:ONLY\s+)?([\w.%]+)\s+(.*?);', sql, re.I | re.S):
        table, body = m.group(1), m.group(2)
        if '%' in table:
            continue
        table = qualify(table)
        for part in split_top(body):
            part = ' '.join(part.split())
            add = re.match(r'ADD COLUMN\s+(?:IF NOT EXISTS\s+)?(\w+)\s+(.*)$', part, re.I)
            if add:
                column = Column(add.group(1).lower(), add.group(2))
                SCHEMA[table].setdefault(column.name, column)
                if column.fk:
                    FKS[table][column.name] = column.fk
                continue
            alter = re.match(r'ALTER COLUMN\s+(\w+)\s+(?:SET DATA )?TYPE\s+(.*)$', part, re.I)
            if alter and alter.group(1).lower() in SCHEMA[table]:
                SCHEMA[table][alter.group(1).lower()].type = norm_type(alter.group(2))
            dropdef = re.match(r'ALTER COLUMN\s+(\w+)\s+DROP DEFAULT', part, re.I)
            if dropdef and dropdef.group(1).lower() in SCHEMA[table]:
                SCHEMA[table][dropdef.group(1).lower()].has_default = False
            setnn = re.match(r'ALTER COLUMN\s+(\w+)\s+SET NOT NULL', part, re.I)
            if setnn and setnn.group(1).lower() in SCHEMA[table]:
                SCHEMA[table][setnn.group(1).lower()].not_null = True
            fk = re.match(r'ADD CONSTRAINT\s+\w+\s+FOREIGN KEY\s*\((\w+)\)\s*REFERENCES\s+([\w.]+)\s*\(\s*(\w+)\s*\)', part, re.I)
            if fk:
                FKS[table][fk.group(1).lower()] = (qualify(fk.group(2)), fk.group(3).lower())


def parse_common_columns(sql):
    """Bloque DO de init.sql que añade created_at/updated_at/deleted_at/version a una lista de tablas."""
    for m in re.finditer(r'FOREACH t IN ARRAY ARRAY\[(.*?)\]\s*LOOP(.*?)END LOOP', sql, re.S):
        if 'ADD COLUMN' not in m.group(2):
            continue
        tables = re.findall(r"'(\w+)'", m.group(1))
        columns = re.findall(r'ADD COLUMN IF NOT EXISTS (\w+) ([^,\']+)', m.group(2))
        for table in tables:
            for name, definition in columns:
                SCHEMA[qualify(table)].setdefault(name.lower(), Column(name.lower(), definition))


def parse_helper_constraints(sql):
    for m in re.finditer(r"add_constraint\('([\w.]+)',\s*'\w+',\s*'FOREIGN KEY \((\w+)\) REFERENCES ([\w.]+) \((\w+)\)", sql):
        FKS[qualify(m.group(1))][m.group(2).lower()] = (qualify(m.group(3)), m.group(4).lower())
    for m in re.finditer(r"replace_fk\('([\w.]+)',\s*'(\w+)',\s*'([\w.]+)',\s*'(\w+)'", sql):
        FKS[qualify(m.group(1))][m.group(2).lower()] = (qualify(m.group(3)), m.group(4).lower())


def parse_seed_values(sql):
    for m in re.finditer(r'INSERT INTO\s+([\w.]+)\s*\(([^)]*)\)\s*VALUES(.*?)(?:ON CONFLICT|;)', sql, re.I | re.S):
        table, columns = qualify(m.group(1)), [c.strip().lower() for c in m.group(2).split(',')]
        for tuple_text in re.findall(r'\(([^()]*(?:\([^()]*\)[^()]*)*)\)', m.group(3)):
            values = split_top(tuple_text)
            if len(values) != len(columns):
                continue
            for column, value in zip(columns, values):
                value = value.strip()
                if value.startswith("'"):
                    SEEDS[(table, column)].add(value.strip("'"))


files = [INIT] + sorted(MIGRATIONS.glob('V*.sql')) + sorted(MIGRATIONS.glob('R__*.sql'))
for path in files:
    text = strip_comments(path.read_text(encoding='utf-8'))
    if path.name.startswith('R__'):
        parse_seed_values(text)
        continue
    parse_create_tables(text)
    if path == INIT:
        parse_common_columns(text)
    parse_alters(text)
    parse_helper_constraints(text)
    parse_seed_values(text)
ALL_TABLES = sorted(t for t in SCHEMA if not t.endswith('_default') and not re.search(r'_p\d{6}$', t))

# --------------------------------------------------------------------------------------------------------------
# 2. Parser del seed generado
# --------------------------------------------------------------------------------------------------------------
class Fn:
    def __init__(self, name, args):
        self.name, self.args = name, args

    def __repr__(self):
        return f'{self.name}{tuple(self.args)}'


def parse_value(text):
    text = text.strip()
    if text == 'NULL':
        return None
    if text in ('TRUE', 'FALSE'):
        return text == 'TRUE'
    if text.startswith("'") and text.endswith("'"):
        return text[1:-1].replace("''", "'")
    if text.startswith('ARRAY['):
        return Fn('array', [parse_value(v) for v in split_top(text[6:text.index(']')])])
    m = re.match(r'pg_temp\.demo_(\w+)\((.*)\)$', text, re.S)
    if m:
        return Fn(m.group(1), [parse_value(v) for v in split_top(m.group(2))])
    if re.fullmatch(r'-?\d+', text):
        return int(text)
    if re.fullmatch(r'-?\d+\.\d+', text):
        return Decimal(text)
    raise ValueError(f'Valor no reconocido: {text[:80]}')


def parse_seed(sql):
    tables = defaultdict(list)
    statements = 0
    for m in re.finditer(r'^INSERT INTO ([\w.]+) \(([^)]*)\) VALUES\n(.*?)\nON CONFLICT DO NOTHING;$', sql, re.M | re.S):
        statements += 1
        table, columns = qualify(m.group(1)), [c.strip() for c in m.group(2).split(',')]
        for line in m.group(3).split('\n'):
            line = line.strip().rstrip(',')
            assert line.startswith('(') and line.endswith(')'), line[:80]
            values = [parse_value(v) for v in split_top(line[1:-1])]
            if len(values) != len(columns):
                error(f'{table}: tupla con {len(values)} valores para {len(columns)} columnas')
                continue
            tables[table].append(dict(zip(columns, values)))
    total_inserts = len(re.findall(r'^INSERT INTO ', sql, re.M))
    if total_inserts != statements:
        error(f'Hay {total_inserts - statements} INSERT sin el formato esperado o sin ON CONFLICT DO NOTHING')
    return tables


# --------------------------------------------------------------------------------------------------------------
# 3. Comprobaciones
# --------------------------------------------------------------------------------------------------------------
TEMPORAL = {'ts': 'timestamp', 'tstz': 'timestamptz', 'date': 'date'}
LOOKUP_TARGET = {
    'state': ('public.states', 'code'), 'account_type': ('public.types_accounts', 'code'),
    'permission': ('public.permissions', 'code'), 'payment_method': ('public.payment_methods', 'code'),
    'payment_status': ('public.payment_statuses', 'code'), 'shipment_status': ('public.shipment_statuses', 'code'),
    'plan': ('public.seller_plans', 'code'), 'category': ('public.categories', 'code'),
    'manufacturer': ('public.manufacturers', 'name'), 'specification': ('public.specifications', 'name'),
    'unit': ('public.units', 'code'), 'make': ('public.make', 'name'),
}


def check_structure(tables):
    for table, rows in tables.items():
        if table not in SCHEMA:
            error(f'Tabla inexistente en el esquema migrado: {table}')
            continue
        columns = SCHEMA[table]
        used = set(rows[0])
        for name in used:
            if name not in columns:
                error(f'{table}.{name}: columna inexistente en init.sql + V*')
            elif columns[name].generated:
                error(f'{table}.{name}: es una columna generada (no se puede insertar)')
            elif columns[name].identity:
                error(f'{table}.{name}: es IDENTITY GENERATED ALWAYS (no se puede insertar)')
        for name, column in columns.items():
            if column.not_null and not column.has_default and not column.generated and not column.identity:
                if name not in used:
                    error(f'{table}.{name}: NOT NULL sin DEFAULT y el seed no la incluye')
        for row in rows:
            for name, value in row.items():
                column = columns.get(name)
                if column is None:
                    continue
                if value is None and column.not_null:
                    error(f'{table}.{name}: NULL en columna NOT NULL')
                if isinstance(value, Fn) and value.name in TEMPORAL and TEMPORAL[value.name] != column.type:
                    error(f'{table}.{name}: {value.name} en columna {column.type}')
                if isinstance(value, float):
                    error(f'{table}.{name}: valor float')


def collect_keys(tables):
    """Claves disponibles: ids del script, semillas y claves naturales de las funciones de búsqueda."""
    keys = defaultdict(set)
    for table, rows in tables.items():
        for row in rows:
            for column, value in row.items():
                if isinstance(value, (str, int)) and not isinstance(value, bool):
                    keys[(table, column)].add(str(value))
    for key, values in SEEDS.items():
        keys[key] |= values
    keys[('public.seller_plans', 'code')].add('TRY')   # semilla de init.sql
    return keys


def natural_key(fn, tables):
    args = fn.args
    if fn.name in LOOKUP_TARGET:
        return LOOKUP_TARGET[fn.name], args[0]
    return None, None


def check_references(tables, keys):
    model_keys = {(r['make_id'].args[0], r['name']) for r in tables.get('public.model', [])}
    version_keys = {(r['model_id'].args[0], r['model_id'].args[1], r['version']) for r in tables.get('public.versions', [])}
    compat_keys = {tuple(r['version_id'].args) for r in tables.get('public.compatibilities', [])}
    unit_spec_keys = {(r['specification_id'].args[0], r['unit_id'].args[0]) for r in tables.get('public.units_specifications', [])}
    lowered = {k: {v.lower() for v in vals} for k, vals in keys.items()}
    for table, rows in tables.items():
        for row in rows:
            for column, value in row.items():
                if isinstance(value, Fn):
                    if value.name in LOOKUP_TARGET:
                        target, key = LOOKUP_TARGET[value.name], value.args[0]
                        pool = lowered.get(target, set()) if value.name in ('category', 'manufacturer', 'plan') else keys.get(target, set())
                        if (key.lower() if value.name in ('category', 'manufacturer', 'plan') else key) not in pool:
                            error(f'{table}.{column}: {value.name}({key}) no existe en el script ni en las semillas')
                    elif value.name == 'model' and tuple(value.args) not in model_keys:
                        error(f'{table}.{column}: modelo {value.args} no definido')
                    elif value.name == 'version' and tuple(value.args) not in version_keys:
                        error(f'{table}.{column}: versión {value.args} no definida')
                    elif value.name == 'compat' and tuple(value.args) not in compat_keys:
                        error(f'{table}.{column}: compatibilidad {value.args} no definida')
                    elif value.name == 'unit_spec' and tuple(value.args) not in unit_spec_keys:
                        error(f'{table}.{column}: unidad-especificación {value.args} no definida')
                    continue
                fk = FKS.get(table, {}).get(column)
                if fk is None or value is None:
                    continue
                target_table, target_column = fk
                if str(value) not in keys.get((target_table, target_column), set()):
                    error(f'{table}.{column} = {value}: no existe en {target_table}.{target_column}')


def dec(value):
    return value if isinstance(value, Decimal) else Decimal(value)


def check_rules(tables):
    T = lambda name: tables.get('public.' + name if '.' not in name else name, [])

    def require(condition, message):
        if not condition:
            error(message)

    def unique(table, columns, where=lambda r: True):
        seen = Counter(tuple(str(r[c]).lower() if isinstance(r[c], str) else str(r[c]) for c in columns)
                       for r in T(table) if where(r))
        for key, count in seen.items():
            if count > 1:
                error(f'{table}: clave única {columns} repetida {key}')

    fn_arg = lambda v: v.args[0] if isinstance(v, Fn) else v
    # Unicidades
    unique('accounts', ['email'])
    unique('persons', ['identification'])
    unique('store', ['bussnies_name'])
    unique('store', ['nit'])
    unique('store', ['bussnies_email'])
    unique('branches', ['store_id', 'name'])
    unique('products', ['sku'])
    unique('inventory', ['branch_id', 'product_id'])
    unique('roles', ['store_id', 'code'])
    unique('collaborators', ['person_id', 'collab_store_id'])
    unique('products_specifications', ['product_id', 'specification_id'])
    unique('receipts', ['payment_id'])
    unique('shipments', ['tracking_number'])
    unique('shipments', ['sale_id'])
    unique('sales', ['order_id', 'branch_id'])
    unique('store_reviews', ['store_id', 'person_id', 'order_id'])
    unique('inventory_price_history', ['inventory_id'], lambda r: r['valid_to'] is None)
    unique('inventory_reorder_policies', ['inventory_id'], lambda r: r['valid_to'] is None)
    unique('carts', ['person_id'], lambda r: r['status'] == 'ACTIVE')
    unique('cart_items', ['cart_id', 'inventory_id'], lambda r: r['removed_at'] is None)
    unique('customer_vehicles', ['person_id'], lambda r: r['is_primary'])
    unique('seller_collaborator_invitations', ['store_id', 'email'], lambda r: r['accepted_at'] is None and r['revoked_at'] is None)
    unique('payments', ['idempotency_key'])
    unique('inventory_movements', ['idempotency_key'])
    unique('purchase_orders', ['store_id', 'po_number'])
    unique('purchase_order_items', ['purchase_order_id', 'product_id'])
    unique('purchase_receipt_items', ['inventory_movement_id'], lambda r: r['inventory_movement_id'] is not None)
    unique('supplier_products', ['supplier_id', 'product_id'])
    unique('suppliers', ['store_id', 'nit'])
    unique('geo_municipalities', ['department_code'], lambda r: r['is_department_capital'])
    unique('seller_plans', ['code'])
    for t in ('tracking.search_events', 'tracking.product_events', 'tracking.events'):
        unique(t, ['event_id'])

    # CHECK de init.sql y de las V*
    for r in T('inventory'):
        require(r['stock'] >= 0 and dec(r['price']) >= 0 and 0 <= r['reserved_stock'] <= r['stock'], f'inventory CHECK {r["id"]}')
    for r in T('order_details'):
        require(r['units'] > 0 and dec(r['unit_price']) >= 0 and dec(r['discount_amount']) >= 0, f'order_details CHECK {r["id"]}')
    for r in T('sales'):
        require(min(dec(r['subtotal']), dec(r['shipping_cost']), dec(r['total'])) >= 0, f'sales >= 0 {r["id"]}')
        require(dec(r['total']) == dec(r['subtotal']) + dec(r['shipping_cost']), f'chk_sales__total_consistent {r["id"]}')
    for r in T('payments'):
        require(dec(r['amount']) > 0 and 0 <= dec(r['refunded_amount']) <= dec(r['amount']), f'payments CHECK {r["id"]}')
        require(re.fullmatch(r'[A-Z]{3}', r['currency']) is not None and r['environment'] in ('SANDBOX', 'PRODUCTION'), 'payments currency/env')
    for r in T('receipts'):
        require(dec(r['total']) >= 0, 'receipts total')
    for r in T('store_reviews'):
        require(1 <= r['rating'] <= 5, 'store_reviews rating')
    for r in T('inventory_movements'):
        require(r['quantity'] > 0 and r['previous_stock'] >= 0 and r['new_stock'] >= 0, f'inventory_movements CHECK {r["id"]}')
        require(r['movement_type'] in ('IN', 'OUT'), 'movement_type IN/OUT (PA_V…0945)')
        delta = r['new_stock'] - r['previous_stock']
        require(delta == (r['quantity'] if r['movement_type'] == 'IN' else -r['quantity']), f'balance del movimiento {r["id"]}')
        require(r['source'] in ('USER', 'ORDER', 'ERP', 'CSV', 'JOB', 'SYSTEM'), 'movement source')
        require((r['reference_type'] is None) == (r['reference_id'] is None), 'reference pair')
    for r in T('locations'):
        require(-90 <= dec(r['latitude']) <= 90 and -180 <= dec(r['longitude']) <= 180, 'locations lat/long')
        require(isinstance(r['metadata'], str) and r['metadata'].startswith('{'), 'locations metadata object')
    for r in T('persons'):
        digits = re.sub(r'[^0-9]', '', r['number_phone'])
        require(re.fullmatch(r'\+?[0-9 ().-]+', r['number_phone']) and 7 <= len(digits) <= 15, 'chk_persons__number_phone_format')
        require(r['date_birth'] >= '1900-01-01', 'date_birth')
    for r in T('branches'):
        require(re.fullmatch(r'[0-9]{7,10}', r['number_phone']) is not None, 'chk_branches__number_phone_format')
    for r in T('accounts'):
        require(r['email'] == r['email'].strip().lower(), 'chk_accounts__email_normalized')
        require(not r['password'].startswith('$2'), 'password no debe ser un BCrypt válido')
    for r in T('store'):
        require(r['bussnies_email'] == r['bussnies_email'].strip().lower(), 'store email normalizado')
        require(r['data_origin'] in ('REAL', 'DEMO', 'SYNTHETIC'), 'store data_origin')
    for r in T('account_tokens'):
        require(r['purpose'] in ('EMAIL_VERIFICATION', 'ACCOUNT_UNLOCK', 'PASSWORD_RECOVERY'), 'token purpose')
        require(r['expiration_at'].args[0] > r['created_at'].args[0], 'chk_account_tokens__expiration')
        require(r['expiration_at'].args[0] < 0, 'tokens: solo vencidos')
    for r in T('account_sessions'):
        require(r['expired_at'].args[0] < 0, 'sesiones: solo vencidas')
    for r in T('seller_collaborator_invitations'):
        require(r['expires_at'].args[0] > r['created_at'].args[0], 'invitations expiration')
        require(r['accepted_at'] is None or r['revoked_at'] is None, 'invitations single outcome')
        require(r['email'] == r['email'].lower(), 'invitations email')
    for r in T('seller_applications'):
        require((r['onboarding_token_hash'] is None) == (r['onboarding_token_expires_at'] is None), 'onboarding pair')
        require(r['status'] in ('PENDING_REVIEW', 'CONFIGURATION_PENDING', 'REJECTED', 'ACTIVE'), 'application status')
        require(r['legal_type'] in ('NATURAL_PERSON', 'LEGAL_ENTITY'), 'legal_type')
        if r['trial_starts_at'] is not None:
            require(r['trial_ends_at'].args[0] > r['trial_starts_at'].args[0], 'trial period')
    for r in T('seller_application_documents'):
        require(r['content_type'] in ('application/pdf', 'image/png', 'image/jpeg') and 0 < r['size_bytes'] <= 10485760, 'documents')
    for r in T('seller_payments'):
        require(r['status'] in ('PENDING', 'APPROVED', 'DECLINED', 'ERROR', 'VOIDED', 'FAILED'), 'seller_payments status')
        require(r['amount_in_cents'] > 0 and 1 <= r['prepaid_months'] <= 12, 'seller_payments amount/months')
    for r in T('seller_plans'):
        require(r['max_branches'] is None or r['max_branches'] >= 1, 'max_branches')
        require(r['code'] != 'TRY', 'el seed no toca el plan TRY')
    for r in T('promotions'):
        tt, scope, target = r['target_type'], r['scope'], r['target_id']
        ok = ((tt == 'ALL' and scope == 'PLATFORM' and target is None) or (tt == 'ATTRIBUTE' and target is None)
              or (tt in ('PRODUCT', 'CATEGORY', 'MANUFACTURER') and target is not None)
              or (tt == 'STORE' and ((scope == 'PLATFORM' and target is not None) or (scope == 'STORE' and target in (None, r['store_id'])))))
        require(ok, f'chk_promotions__target_id {r["name"]}')
        require((scope == 'STORE') == (r['store_id'] is not None), 'promotions scope/store')
        require(r['ends_at'].args[0] > r['starts_at'].args[0], 'promotions dates')
        value = dec(r['discount_value'])
        require(value > 0 and (r['discount_type'] != 'PERCENTAGE' or value <= 100), 'promotions discount')
    for r in T('shipments'):
        if r['delivered_at'] is not None:
            require(r['shipped_at'] is not None and r['delivered_at'].args[0] >= r['shipped_at'].args[0], 'delivery after shipping')
        require(dec(r['distance_km']) >= 0 and r['package_weight_grams'] > 0, 'shipments distance/weight')
    for r in T('orders'):
        require(r['data_origin'] in ('REAL', 'DEMO', 'SYNTHETIC') and r['channel'] in ('WEB', 'MOBILE_WEB', 'APP', 'STORE_ASSISTED', 'API'), 'orders enums')
    for r in T('order_status_history') + T('sale_status_history') + T('return_status_history'):
        require(r['from_status'] != r['to_status'], 'transition from != to')
        require(r['actor_type'] in ('CUSTOMER', 'STORE', 'PLATFORM', 'SYSTEM', 'PROVIDER'), 'actor_type')
    for r in T('products_compatibilies'):
        require(r['evidence_status'] in ('CONFIRMED', 'SELLER_DECLARED', 'SUGGESTED', 'REJECTED'), 'evidence status')
        require(r['year_to'] >= r['year_from'], 'compat years')
    for r in T('inventory_price_history'):
        require(dec(r['list_price']) >= 0 and (r['valid_to'] is None or r['valid_to'].args[0] > r['valid_from'].args[0]), 'price history')
    for r in T('inventory_reorder_policies'):
        require(r['reorder_point'] >= 0 and (r['max_stock'] is None or r['max_stock'] >= r['reorder_point']), 'reorder policy')
    for r in T('inventory_daily_snapshots'):
        require(r['units_sold'] <= r['units_out'] and 0 <= r['stockout_minutes'] <= 1440 and r['reserved'] >= 0, 'snapshots')
    for r in T('carts'):
        require((r['status'] == 'CONVERTED') == (r['converted_order_id'] is not None and r['converted_at'] is not None), 'chk_carts__converted')
        require(r['status'] != 'ABANDONED' or r['abandoned_at'] is not None, 'chk_carts__abandoned')
    for r in T('cart_items'):
        require(r['quantity'] > 0 and dec(r['captured_unit_price']) >= 0, 'cart_items')
    for r in T('stock_reservations'):
        require(r['quantity'] > 0 and r['expires_at'].args[0] > r['created_at'].args[0], 'stock_reservations')
        require(r['status'] != 'ACTIVE', 'el seed no deja reservas ACTIVE (reserved_stock = 0)')
    for r in T('return_requests'):
        require(r['status'] != 'RESOLVED' or (r['resolution'] is not None and r['resolved_at'] is not None), 'chk_return_requests__resolved')
        require(r['refund_amount'] is None or (dec(r['refund_amount']) >= 0 and r['resolution'] in ('REFUND', 'PARTIAL_REFUND')), 'refund')
    for r in T('purchase_orders'):
        require(r['status'] in ('DRAFT', 'CANCELLED') or r['ordered_at'] is not None, 'chk_purchase_orders__ordered')
    for r in T('person_consents'):
        require(r['channel'] in ('WEB', 'APP', 'EMAIL', 'SUPPORT', 'IMPORT'), 'consent channel')
    for r in T('tracking.search_events'):
        q = r['query_text']
        if q is not None:
            require('@' not in q and not re.search(r'(^|[^0-9])(\+?57)?3[0-9]{9}([^0-9]|$)', q), 'query_text sin PII')
        require(r['compatible_results_count'] is None or r['compatible_results_count'] <= r['results_count'], 'search results')
    for r in T('tracking.sessions'):
        require(r['last_seen_at'].args[0] >= r['started_at'].args[0], 'sessions last_seen')
        require('?' not in r['landing_path'] and '@' not in r['landing_path'], 'landing_path sin PII')

    # Invariantes de negocio
    details_by_sale = defaultdict(list)
    for d in T('order_details'):
        details_by_sale[d['sale_id']].append(d)
    sales_by_order = defaultdict(list)
    for s in T('sales'):
        sales_by_order[s['order_id']].append(s)
        lines = details_by_sale[s['id']]
        require(lines, f'venta sin líneas {s["id"]}')
        require(dec(s['subtotal']) == sum(dec(d['unit_price']) * d['units'] for d in lines), f'subtotal de venta {s["id"]}')
        for d in lines:
            require(dec(d['discount_amount']) == (dec(d['list_unit_price']) - dec(d['unit_price'])) * d['units'], f'descuento de línea {d["id"]}')
    payments = {p['order_id']: p for p in T('payments')}
    for o in T('orders'):
        sales = sales_by_order[o['id']]
        require(dec(o['total']) == sum(dec(s['total']) for s in sales), f'total del pedido {o["id"]}')
        require(o['id'] in payments and dec(payments[o['id']]['amount']) == dec(o['total']), f'pago = total {o["id"]}')
    approved = Counter(p['order_id'] for p in T('payments') if fn_arg(p['payment_status_id']) == 'APPROVED')
    require(all(c == 1 for c in approved.values()), 'un solo pago APPROVED por pedido')
    for r in T('receipts'):
        require(dec(r['total']) == dec(payments[r['order_id']]['amount']), 'recibo = pago')

    allowed_sale = {None: {'PENDING'}, 'PENDING': {'CONFIRMED', 'CANCELLED'}, 'CONFIRMED': {'PREPARING', 'CANCELLED'},
                    'PREPARING': {'SHIPPED'}, 'SHIPPED': {'DELIVERED'}}
    history = defaultdict(list)
    for h in T('sale_status_history'):
        history[h['sale_id']].append(h)
    sale_state = {s['id']: fn_arg(s['state_id']) for s in T('sales')}
    for sale_id, rows in history.items():
        rows.sort(key=lambda h: h['changed_at'].args[0])
        for h in rows:
            require(h['to_status'] in allowed_sale.get(h['from_status'], set()), f'transición de venta inválida {h["from_status"]}->{h["to_status"]}')
        require(rows[-1]['to_status'] == sale_state[sale_id], f'estado final de la venta {sale_id}')
    allowed_ship = {None: {'PREPARING'}, 'PREPARING': {'DISPATCHED'}, 'DISPATCHED': {'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED'},
                    'IN_TRANSIT': {'OUT_FOR_DELIVERY', 'DELIVERED'}, 'OUT_FOR_DELIVERY': {'DELIVERED'}}
    events = defaultdict(list)
    for e in T('shipment_events'):
        events[e['shipment_id']].append(e)
    for shipment in T('shipments'):
        rows = sorted(events[shipment['id']], key=lambda e: e['event_at'].args[0])
        previous = None
        for e in rows:
            status = fn_arg(e['shipment_status_id'])
            require(status in allowed_ship.get(previous, set()), f'transición de envío inválida {previous}->{status}')
            require(fn_arg(e['from_status_id']) == previous, 'from_status_id del evento')
            previous = status
        require(previous == fn_arg(shipment['shipment_status_id']), 'estado final del envío')
        require(sale_state[shipment['sale_id']] in ('PREPARING', 'SHIPPED', 'DELIVERED'), 'envío de venta no preparada')
    for r in T('store_reviews'):
        delivered = [s for s in sales_by_order[r['order_id']] if fn_arg(s['state_id']) == 'DELIVERED']
        require(delivered, f'reseña de pedido sin ventas entregadas {r["id"]}')

    # Coherencia de almacén (triggers de V…0925 y V…141330)
    branch_store = {b['id']: b['store_id'] for b in T('branches')}
    product_store = {p['id']: p['store_id'] for p in T('products')}
    inventory = {i['id']: i for i in T('inventory')}
    for i in inventory.values():
        require(product_store[i['product_id']] == branch_store[i['branch_id']], f'chk_inventory__same_store {i["id"]}')
    sales = {s['id']: s for s in T('sales')}
    for d in T('order_details'):
        require(inventory[d['inventory_id']]['branch_id'] == sales[d['sale_id']]['branch_id'], 'chk_order_details__consistency')
        require(sales[d['sale_id']]['order_id'] == d['order_id'], 'detalle de otro pedido')
        require(inventory[d['inventory_id']]['product_id'] == d['product_id'], 'product_id de la línea')
    for h in T('sale_status_history'):
        require(branch_store[sales[h['sale_id']]['branch_id']] == h['store_id'], 'chk_sale_status_history__consistency')
    for h in T('inventory_price_history'):
        i = inventory[h['inventory_id']]
        require(i['branch_id'] == h['branch_id'] and i['product_id'] == h['product_id'] and branch_store[i['branch_id']] == h['store_id'],
                'chk_inventory_price_history__consistency')

    # Ledger: último new_stock = stock
    movements = defaultdict(list)
    details = {d['id']: d for d in T('order_details')}
    for m in T('inventory_movements'):
        movements[m['inventory_id']].append(m)
        if m['order_detail_id'] is not None:
            require(details[m['order_detail_id']]['inventory_id'] == m['inventory_id'], 'chk_inventory_movements__order_detail_match')
    for inventory_id, i in inventory.items():
        rows = sorted(movements[inventory_id], key=lambda m: m['occurred_at'].args[0])
        times = [m['occurred_at'].args[0] for m in rows]
        require(len(times) == len(set(times)), f'instantes repetidos en el ledger {inventory_id}')
        running = 0
        for m in rows:
            require(m['previous_stock'] == running, f'ledger encadenado {inventory_id}')
            running = m['new_stock']
        require(running == i['stock'], f'ledger descuadrado {inventory_id}: {running} vs {i["stock"]}')


def report(tables, sql_path, out):
    empty_reasons = {
        'public.states': 'catálogo sembrado por init.sql', 'public.types_accounts': 'catálogo sembrado por init.sql',
        'public.permissions': 'catálogo sembrado por init.sql', 'public.payment_methods': 'catálogo sembrado por init.sql',
        'public.payment_statuses': 'catálogo sembrado por init.sql',
        'public.shipment_statuses': 'catálogo sembrado por init.sql y V202610141020',
        'public.inventory_movement_reasons': 'catálogo sembrado por V202610140940',
        'public.cancellation_reasons': 'catálogo sembrado por V202610141020',
        'public.consent_purposes': 'catálogo sembrado por V202610141030',
        'public.return_reasons': 'catálogo sembrado por V202610141250',
        'tracking.event_definitions': 'catálogo sembrado por V202610141040',
        'public.geo_departments': 'la llena R__seed_geo_divipola.sql',
        'analytics.holidays': 'la llena V202610140930 / R__seed_holidays_co.sql',
        'analytics.dim_date': 'la llena V202610140930 / R__seed_holidays_co.sql',
        'public.product_images': 'requiere archivos reales en el almacenamiento; una ruta inventada rompería la imagen en la UI',
        'public.migration_backfill_order_details': 'tabla interna del rollback de V202610141021',
    }
    for t in ALL_TABLES:
        if t.startswith('analytics.fact_'):
            empty_reasons[t] = 'hecho derivado: lo llena analytics.refresh_* al final del seed (solo data_origin = REAL)'
        if t.startswith('ml.'):
            empty_reasons[t] = 'no hay modelos entrenados; predicciones o features inventados contaminarían la evaluación'
    lines = ['| Tabla | Filas generadas | Nota |', '|---|---:|---|']
    for t in ALL_TABLES:
        count = len(tables.get(t, []))
        note = '' if count else empty_reasons.get(t, '**SIN MOTIVO DOCUMENTADO**')
        if not count and t not in empty_reasons:
            error(f'Tabla vacía sin motivo documentado: {t}')
        lines.append(f'| `{t}` | {count} | {note} |')
    unknown = sorted(set(tables) - set(ALL_TABLES))
    if unknown:
        error(f'Tablas del seed fuera del esquema: {unknown}')
    text = '\n'.join(lines)
    if out:
        Path(out).write_text(text + '\n', encoding='utf-8', newline='\n')
    return text


def main():
    args = sys.argv[1:]
    out = None
    if '--report' in args:
        out = args[args.index('--report') + 1]
        args = [a for a in args if a not in ('--report', out)]
    sql_path = Path(args[0]) if args else Path(__file__).with_name('demo_seed.sql')
    sql = sql_path.read_text(encoding='utf-8')
    require_text = [('BEGIN;', 'transacción'), ('COMMIT;', 'transacción'),
                    ("SET LOCAL autopartco.capture_managed_by_app = 'on';", 'triggers puente apagados'),
                    ('\\gset', 'guarda de idempotencia'), ('ensure_monthly_partitions', 'particiones')]
    for text, why in require_text:
        if text not in sql:
            error(f'Falta {text!r} ({why})')
    if re.search(r'\bplaca\s*[,)]', sql, re.I):
        error('El seed parece contener una columna de placa')
    if re.search(r"'[A-Z]{3}(\d{3}|\d{2}[A-Z])'", sql):   # placas: ABC123 (carro) o ABC12D (moto)
        error('El seed contiene un valor con formato de placa')
    tables = parse_seed(sql)
    check_structure(tables)
    check_references(tables, collect_keys(tables))
    check_rules(tables)
    table_report = report(tables, sql_path, out)
    print(f'Esquema migrado: {len(ALL_TABLES)} tablas ({len(PARTITIONED)} particionadas) desde {len(files)} archivos')
    print(f'Seed: {sum(len(r) for r in tables.values())} filas en {len(tables)} tablas')
    print(table_report)
    for message in WARNINGS:
        print('AVISO:', message)
    if ERRORS:
        print(f'\n{len(ERRORS)} ERRORES')
        for message in sorted(set(ERRORS))[:200]:
            print(' -', message)
        sys.exit(1)
    print('\nOK: sin errores')


if __name__ == '__main__':
    main()
