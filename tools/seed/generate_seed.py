#!/usr/bin/env python3
"""Generador de datos sintéticos para Autopartco (solo librería estándar).

Produce SQL (INSERT ... ON CONFLICT DO NOTHING, idempotente: IDs deterministas) con:
  clientes (persons + accounts), almacenes sintéticos con gerente, sucursales y ubicaciones,
  productos con imágenes, inventario, pedidos, ventas, líneas, pagos y direcciones de envío.
  Al almacén del usuario lt.antonio.p@gmail.com se le añaden productos, inventario y ventas.

Uso:
  python tools/seed/generate_seed.py                 # escribe tools/seed/out/seed_NN.sql
  python tools/seed/generate_seed.py --scale 0.5     # la mitad de volumen
  python tools/seed/generate_seed.py --single        # un solo archivo
  python tools/seed/generate_seed.py --upload        # sube las imágenes a Supabase Storage (usa .env)

Las imágenes son PNG dibujados por categoría y marca (sin dependencias). Cada producto apunta a
products/<product_id>/<uuid>.png en el bucket SUPABASE_BUCKET_PRODUCTS_IMGS; el SQL referencia esas rutas,
así que hay que ejecutar --upload para que se vean. La salida (tools/seed/out/) está en .gitignore.
Las cuentas sembradas reutilizan el hash de contraseña de la cuenta lt.antonio.p@gmail.com.
"""
import argparse
import math
import os
import random
import struct
import sys
import unicodedata
import uuid
import zlib
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone

NS = uuid.UUID("6f1c1b3e-5b0a-4e0b-9d6b-5e0d0a7a11aa")
UTC = timezone.utc


def uid(*parts):
    return str(uuid.uuid5(NS, ":".join(str(p) for p in parts)))


# ----------------------------------------------------------------------------------------------
# Referencias reales de Supabase (autopartco-dev)
# ----------------------------------------------------------------------------------------------
ST = {
    "ACTIVE": "9e6414b0-3160-42aa-a6c3-b5b13069b40f", "PENDING": "cbb53a6f-9662-48c8-a427-95fa4eacf186",
    "CONFIRMED": "737d4006-6374-4fe6-bfe4-8c5d816a5d1b", "PREPARING": "9b546ea4-75ca-4a54-9462-d5c52e9216eb",
    "SHIPPED": "032a6346-f7b7-48ab-b1cd-5dcbb44606e2", "DELIVERED": "22ae19fb-9677-471c-bed3-ba4fd1e90308",
    "CANCELLED": "629ed797-d69c-4b29-b8db-da4ea6e7a23e", "REJECTED": "7dc4d39e-cb6d-4ca5-b5bc-8126ca836167",
}
CUSTOMER_TYPE = "1e303707-0cf8-43b1-bf42-81015c1631c5"
MANAGER_TYPE = "7f2e2772-1b3c-48ed-bbb9-4ffde61e1ae8"
CAT = {  # code -> id
    "FRENOS": "8a5e1587-f27a-4c29-85eb-ab05bbba1412", "SUSPENSION": "d66efe1a-c966-4999-97d8-5c2f176ebc17",
    "MOTOR": "710c7fb6-e4ce-4f47-8fae-39be6073dc45", "FILTROS": "0ce6cb26-8f48-4548-9116-d43f27f14fbd",
    "ELECTRICO": "d3bf5ae1-aaa0-4608-9515-e84489eb86a2", "TRANSMISION": "d5882d20-f55f-4ec4-991f-12eb9e95d881",
    "DIRECCION": "f2a3f447-3e3a-45e6-a6b8-5531ae7be8e4", "REFRIGERACION": "53e866f1-f6af-44dd-8ba1-74c069890e79",
    "ESCAPE": "2fe7b709-5816-4e0e-af77-fdf4b426821b", "CARROCERIA": "2d1ad7e3-32ca-434d-80c9-1a527ba69738",
    "ILUMINACION": "e55c44f6-7dda-49dc-906c-835152ffa7d5", "ACEITES_LUBRICANTES": "6f8bc5c7-d509-49d5-88cd-37497d298a22",
}
BRAND = {
    "Bosch": "193be2dc-25a8-460e-80b9-fd7d83e6f941", "Brembo": "1bc90c6f-1263-47db-b59f-2d7bcd5df7d0",
    "Gates": "ad76aeb2-fb73-45e9-9b65-07e6e827fc59", "Mahle": "f0aaec3f-4455-487a-8469-4595084540d1",
    "NGK": "ffd14e77-533c-4ce9-aa95-959c037bf111", "Denso": "aed132ca-6f18-4e71-9b1b-86a7de1956a7",
    "Valeo": "f3377f33-0de0-474f-87da-00a830df2a13", "SKF": "afdeb972-f10c-4b8d-a2cf-cee9ac0c36f3",
    "Monroe": "7a4287be-4ef6-4f4e-bb5d-d8f6b318fe95", "Sachs": "16199189-dc03-4768-9868-56e587c2f886",
    "TRW": "8d821233-a5ce-4af4-87f8-1c43e0bf737c", "Mobil": "d52c7003-7658-4fea-9708-10aea867fb89",
    "Castrol": "4bac1126-8422-42ec-b8e7-09bfec16272e", "Genuine/OEM": "78775b64-7d1d-4541-b9a6-443f7cacd6f0",
    "Philips": "8bca7888-02a0-44aa-974d-9fd5f6bae368",
}
BRAND_COLOR = {
    "Bosch": (200, 30, 40), "Brembo": (220, 40, 40), "Gates": (30, 120, 60), "Mahle": (0, 90, 160),
    "NGK": (230, 190, 0), "Denso": (200, 20, 50), "Valeo": (20, 130, 90), "SKF": (20, 60, 140),
    "Monroe": (230, 130, 0), "Sachs": (40, 40, 40), "TRW": (0, 100, 170), "Mobil": (210, 20, 40),
    "Castrol": (0, 130, 60), "Genuine/OEM": (90, 90, 100), "Philips": (0, 80, 180),
}
PM = {"CARD": "13a60bb7-2b78-41e8-af0f-126aa7634f12", "TRANSFER": "49cc1d2a-de5e-464a-a739-3ebe29320f46",
      "CASH": "6f4e8db1-b186-40c6-846e-d41ad3b2a0e9", "WOMPI": "5efb7cc5-fcb4-4136-9a12-2dce7672fa91"}
PS = {"PENDING": "278253d1-d8ba-429f-93c9-39a823844b6d", "APPROVED": "bf426de8-aa21-409b-8725-782816885340",
      "REJECTED": "a711a8b4-0a4d-48f7-84a2-88c563f62cb9", "CANCELLED": "cb8bcb53-ef3e-4c4e-92cc-761898aaf012"}
CANCEL_REASONS = ["CUSTOMER_CHANGED_MIND", "CUSTOMER_FOUND_CHEAPER", "CUSTOMER_WRONG_PART",
                  "CUSTOMER_DELIVERY_TOO_SLOW", "STORE_OUT_OF_STOCK", "PAYMENT_FAILED", "PAYMENT_TIMEOUT"]

USER_EMAIL = "lt.antonio.p@gmail.com"
USER_STORE = {
    "id": "a0dc4773-bed1-4ed5-8e93-dc9fea2d3216", "name": "Almacen Test 1", "code": "AT1",
    "branches": [("df985cd1-7bf1-4ed1-85e3-f4d1700ad547", "Test Sucursal"),
                 ("34e067fc-7e3f-4eea-be64-82b3f5f8f69d", "test sucursal 2")],
}

# ----------------------------------------------------------------------------------------------
# Catálogo de repuestos
# ----------------------------------------------------------------------------------------------
VEH = [
    "Chevrolet Spark GT 2011-2018", "Chevrolet Aveo 2008-2016", "Chevrolet Sail 2013-2019", "Chevrolet Onix 2013-2020",
    "Chevrolet Captiva 2008-2016", "Renault Logan 2008-2021", "Renault Sandero 2008-2021", "Renault Duster 2012-2022",
    "Renault Kwid 2017-2023", "Renault Clio 2005-2014", "Mazda 3 2009-2018", "Mazda 2 2010-2019", "Mazda CX-5 2013-2021",
    "Kia Picanto 2011-2020", "Kia Rio 2012-2020", "Kia Sportage 2011-2021", "Hyundai i10 2011-2020",
    "Hyundai Accent 2008-2019", "Hyundai Tucson 2011-2020", "Toyota Corolla 2009-2020", "Toyota Hilux 2006-2020",
    "Toyota Yaris 2007-2019", "Nissan March 2012-2020", "Nissan Versa 2012-2020", "Nissan Sentra 2007-2019",
    "Nissan Frontier 2008-2020", "Ford Fiesta 2008-2019", "Ford EcoSport 2012-2020", "Ford Ranger 2009-2022",
    "Suzuki Swift 2010-2020", "Suzuki Vitara 2015-2022", "Volkswagen Gol 2008-2020", "Volkswagen Jetta 2007-2018",
]
H_BULB = ["H4 12V 60/55W", "H7 12V 55W", "H11 12V 55W", "9005 12V 65W", "H1 12V 55W", "T10 W5W 12V", "P21W 12V"]
H_LED = ["H4 6000K 12V", "H7 6000K 12V", "H11 6000K 12V", "9005 6000K 12V"]
H_OIL = ["5W-30 Sintético 1 Gal", "10W-40 Semisintético 1 Gal", "20W-50 Mineral 1 Gal", "5W-30 Sintético 1 Qt",
         "5W-20 Sintético 1 Qt", "15W-40 Diésel 1 Gal", "10W-30 Semisintético 1 Qt"]
H_ATF = ["ATF Dexron VI 1 Qt", "ATF Dexron III 1 Qt", "75W-90 Caja manual 1 Qt", "80W-90 Diferencial 1 Qt"]
H_BAT = ["40 Ah 12V", "55 Ah 12V", "65 Ah 12V", "75 Ah 12V", "100 Ah 12V"]

# categoría -> (marcas, [(tipo, pmin, pmax, variantes|None=vehículos, universal)])
CATALOG = {
    "FRENOS": (["Brembo", "TRW", "Bosch", "Valeo"], [
        ("Pastillas de freno delanteras", 55000, 190000, None, False),
        ("Pastillas de freno traseras", 45000, 150000, None, False),
        ("Disco de freno delantero", 90000, 340000, None, False),
        ("Disco de freno trasero", 80000, 280000, None, False),
        ("Bandas de freno traseras", 60000, 160000, None, False),
        ("Cilindro de rueda", 35000, 95000, None, False),
        ("Bomba de freno", 150000, 420000, None, False),
        ("Líquido de frenos DOT 4", 14000, 38000, ["250 ml", "500 ml", "1 L"], True)]),
    "SUSPENSION": (["Monroe", "Sachs", "SKF", "TRW"], [
        ("Amortiguador delantero", 130000, 420000, None, False),
        ("Amortiguador trasero", 110000, 380000, None, False),
        ("Resorte helicoidal delantero", 90000, 260000, None, False),
        ("Rótula inferior", 45000, 150000, None, False),
        ("Buje de barra estabilizadora", 18000, 65000, None, False),
        ("Base de amortiguador", 60000, 190000, None, False),
        ("Brazo de suspensión", 120000, 380000, None, False),
        ("Rodamiento de rueda delantero", 55000, 210000, None, False)]),
    "MOTOR": (["Mahle", "Gates", "SKF", "Bosch", "Genuine/OEM"], [
        ("Kit de distribución", 220000, 780000, None, False),
        ("Correa de accesorios", 40000, 130000, None, False),
        ("Junta de culata", 130000, 520000, None, False),
        ("Kit de empaques de motor", 160000, 450000, None, False),
        ("Bomba de aceite", 120000, 380000, None, False),
        ("Aros de pistón", 140000, 420000, None, False),
        ("Empaque de tapa de válvulas", 35000, 110000, None, False),
        ("Soporte de motor", 70000, 240000, None, False)]),
    "FILTROS": (["Bosch", "Mahle", "Valeo", "Genuine/OEM"], [
        ("Filtro de aceite", 14000, 52000, None, False),
        ("Filtro de aire", 22000, 85000, None, False),
        ("Filtro de combustible", 25000, 120000, None, False),
        ("Filtro de cabina", 28000, 95000, None, False)]),
    "ELECTRICO": (["Bosch", "Denso", "NGK", "Valeo"], [
        ("Bujía de iridio", 22000, 65000, None, False),
        ("Batería", 280000, 640000, H_BAT, True),
        ("Alternador", 320000, 980000, None, False),
        ("Motor de arranque", 280000, 860000, None, False),
        ("Bobina de encendido", 90000, 320000, None, False),
        ("Sensor de oxígeno", 110000, 380000, None, False),
        ("Sensor de posición del cigüeñal", 70000, 230000, None, False),
        ("Relé 12V", 8000, 35000, ["4 pines", "5 pines"], True)]),
    "TRANSMISION": (["Sachs", "SKF", "Valeo", "Gates"], [
        ("Kit de embrague", 280000, 950000, None, False),
        ("Eje homocinético delantero", 180000, 520000, None, False),
        ("Collarín de embrague", 70000, 240000, None, False),
        ("Cable de embrague", 25000, 80000, None, False),
        ("Bomba de embrague", 90000, 260000, None, False),
        ("Sello de caja de cambios", 15000, 60000, None, False)]),
    "DIRECCION": (["TRW", "SKF", "Monroe", "Genuine/OEM"], [
        ("Terminal de dirección", 40000, 140000, None, False),
        ("Cremallera de dirección", 450000, 1500000, None, False),
        ("Bomba de dirección hidráulica", 260000, 780000, None, False),
        ("Barra axial de dirección", 55000, 190000, None, False),
        ("Manguera de dirección hidráulica", 60000, 210000, None, False)]),
    "REFRIGERACION": (["Gates", "Valeo", "Mahle", "Denso"], [
        ("Radiador", 220000, 760000, None, False),
        ("Bomba de agua", 70000, 250000, None, False),
        ("Termostato", 25000, 95000, None, False),
        ("Electroventilador", 160000, 520000, None, False),
        ("Manguera superior de radiador", 30000, 110000, None, False),
        ("Tapa de radiador", 12000, 38000, ["0.9 bar", "1.1 bar", "1.3 bar"], True)]),
    "ESCAPE": (["Bosch", "Denso", "Genuine/OEM"], [
        ("Catalizador", 420000, 1700000, None, False),
        ("Silenciador trasero", 120000, 420000, None, False),
        ("Flexible de escape", 40000, 150000, None, False),
        ("Tubo intermedio de escape", 90000, 300000, None, False),
        ("Empaque de múltiple de escape", 18000, 70000, None, False)]),
    "CARROCERIA": (["Genuine/OEM", "Valeo"], [
        ("Retrovisor izquierdo", 85000, 290000, None, False),
        ("Retrovisor derecho", 85000, 290000, None, False),
        ("Parachoques delantero", 220000, 720000, None, False),
        ("Guardafango delantero", 140000, 420000, None, False),
        ("Capó", 350000, 980000, None, False),
        ("Manija exterior de puerta", 30000, 120000, None, False)]),
    "ILUMINACION": (["Philips", "Bosch", "Valeo", "Denso"], [
        ("Bombillo halógeno", 8000, 45000, H_BULB, True),
        ("Bombillo LED", 45000, 190000, H_LED, True),
        ("Farola delantera izquierda", 180000, 650000, None, False),
        ("Farola delantera derecha", 180000, 650000, None, False),
        ("Stop trasero izquierdo", 90000, 320000, None, False),
        ("Stop trasero derecho", 90000, 320000, None, False),
        ("Faro antiniebla", 70000, 260000, None, False)]),
    "ACEITES_LUBRICANTES": (["Mobil", "Castrol"], [
        ("Aceite de motor", 38000, 290000, H_OIL, True),
        ("Aceite de transmisión", 32000, 120000, H_ATF, True),
        ("Grasa multipropósito", 12000, 45000, ["400 g", "1 kg"], True),
        ("Aditivo limpia inyectores", 18000, 55000, ["300 ml", "500 ml"], True)]),
}
CAT_PREFIX = {c: c[:3] for c in CAT}

CITIES = [  # ciudad, departamento, lat, lon, peso
    ("Bogotá", "Bogotá D.C.", 4.711, -74.072, 30), ("Medellín", "Antioquia", 6.244, -75.581, 16),
    ("Cali", "Valle del Cauca", 3.452, -76.532, 12), ("Barranquilla", "Atlántico", 10.964, -74.796, 9),
    ("Cartagena", "Bolívar", 10.391, -75.479, 7), ("Bucaramanga", "Santander", 7.119, -73.122, 6),
    ("Pereira", "Risaralda", 4.813, -75.696, 4), ("Manizales", "Caldas", 5.070, -75.514, 3),
    ("Cúcuta", "Norte de Santander", 7.894, -72.508, 4), ("Ibagué", "Tolima", 4.438, -75.232, 3),
    ("Villavicencio", "Meta", 4.142, -73.627, 3), ("Santa Marta", "Magdalena", 11.241, -74.199, 3),
]
FIRST = ["Juan", "Carlos", "Andrés", "Luis", "Santiago", "Camilo", "Felipe", "Daniel", "Mateo", "Sebastián", "Jorge",
         "Diego", "Julián", "Nicolás", "David", "Alejandro", "Mauricio", "Hernán", "Óscar", "Iván", "María", "Laura",
         "Paula", "Daniela", "Camila", "Valentina", "Sofía", "Natalia", "Carolina", "Andrea", "Juliana", "Diana",
         "Luisa", "Paola", "Mónica", "Claudia", "Sandra", "Lina", "Alejandra", "Manuela"]
LAST = ["García", "Rodríguez", "Martínez", "López", "González", "Hernández", "Pérez", "Sánchez", "Ramírez", "Torres",
        "Flores", "Díaz", "Vargas", "Castro", "Rojas", "Moreno", "Jiménez", "Ruiz", "Gómez", "Ortiz", "Morales",
        "Muñoz", "Quintero", "Gutiérrez", "Cardona", "Restrepo", "Arango", "Mejía", "Ospina", "Valencia", "Suárez",
        "Herrera", "Medina", "Peña", "Cortés", "Pardo", "Salazar", "Giraldo", "Parra", "Duarte"]
STREETS = ["Calle", "Carrera", "Avenida", "Transversal", "Diagonal"]
SYNTH_STORES = [
    ("Autopartes El Cóndor", "ACO", 0), ("Repuestos La Montaña", "RLM", 1), ("Frenos y Suspensión del Norte", "FSN", 5),
    ("Motor Center Costa", "MCC", 3), ("Lubricentro Los Andes", "LLA", 2), ("Importadora Eje Cafetero", "IEC", 6),
]


# ----------------------------------------------------------------------------------------------
# Imágenes (PNG con rasterizador mínimo)
# ----------------------------------------------------------------------------------------------
class Canvas:
    def __init__(self, w, h, top, bot):
        self.w, self.h = w, h
        self.rows = []
        for y in range(h):
            t = y / (h - 1)
            self.rows.append(bytearray(bytes(int(a + (b - a) * t) for a, b in zip(top, bot)) * w))

    def _span(self, y, x0, x1, col):
        if 0 <= y < self.h:
            a, b = max(0, int(x0)), min(self.w, int(x1))
            if b > a:
                self.rows[y][a * 3:b * 3] = bytes(col) * (b - a)

    def rect(self, x0, y0, x1, y1, col):
        for y in range(int(y0), int(y1)):
            self._span(y, x0, x1, col)

    def circle(self, cx, cy, r, col):
        self.ring(cx, cy, r, 0, col)

    def ring(self, cx, cy, r, rin, col):
        for y in range(int(cy - r), int(cy + r) + 1):
            dy = y - cy
            if abs(dy) > r:
                continue
            ox = math.sqrt(r * r - dy * dy)
            if abs(dy) < rin:
                ix = math.sqrt(rin * rin - dy * dy)
                self._span(y, cx - ox, cx - ix, col)
                self._span(y, cx + ix, cx + ox + 1, col)
            else:
                self._span(y, cx - ox, cx + ox + 1, col)

    def poly(self, pts, col):
        ys = [p[1] for p in pts]
        n = len(pts)
        for y in range(int(min(ys)), int(max(ys)) + 1):
            xs = []
            for i in range(n):
                (xa, ya), (xb, yb) = pts[i], pts[(i + 1) % n]
                if (ya <= y < yb) or (yb <= y < ya):
                    xs.append(xa + (y - ya) * (xb - xa) / (yb - ya))
            xs.sort()
            for i in range(0, len(xs) - 1, 2):
                self._span(y, xs[i], xs[i + 1] + 1, col)

    def line(self, x0, y0, x1, y1, w, col):
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy) or 1
        nx, ny = -dy / ln * w / 2, dx / ln * w / 2
        self.poly([(x0 + nx, y0 + ny), (x1 + nx, y1 + ny), (x1 - nx, y1 - ny), (x0 - nx, y0 - ny)], col)

    def png(self):
        raw = b"".join(b"\x00" + bytes(r) for r in self.rows)

        def chunk(t, d):
            c = struct.pack(">I", len(d)) + t + d
            return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

        return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 2, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 6)) + chunk(b"IEND", b""))


LIGHT, MID, DARK = (236, 239, 243), (170, 178, 190), (52, 58, 68)
CAT_BG = {
    "FRENOS": (120, 30, 35), "SUSPENSION": (30, 70, 110), "MOTOR": (70, 70, 80), "FILTROS": (30, 100, 90),
    "ELECTRICO": (110, 90, 20), "TRANSMISION": (80, 50, 110), "DIRECCION": (30, 90, 130),
    "REFRIGERACION": (20, 100, 120), "ESCAPE": (85, 70, 60), "CARROCERIA": (60, 70, 90),
    "ILUMINACION": (50, 50, 90), "ACEITES_LUBRICANTES": (110, 70, 20),
}


def _glyph(c, cat, accent, v):
    cx, cy = 160, 150
    if cat == "FRENOS":
        c.circle(cx, cy, 112, MID); c.circle(cx, cy, 104, LIGHT); c.ring(cx, cy, 72, 62, MID)
        c.circle(cx, cy, 34, DARK); c.circle(cx, cy, 16, MID)
        for i in range(5 + v % 3):
            a = 2 * math.pi * i / (5 + v % 3)
            c.circle(cx + 52 * math.cos(a), cy + 52 * math.sin(a), 7, DARK)
        c.rect(228, 96, 262, 204, accent)
    elif cat == "FILTROS":
        c.rect(105, 78, 215, 232, LIGHT if v % 2 else accent); c.rect(98, 68, 222, 88, DARK); c.rect(98, 222, 222, 242, DARK)
        for x in range(116, 210, 14 - v % 3):
            c.rect(x, 96, x + 5, 214, MID)
        c.rect(105, 140, 215, 176, accent if v % 2 else LIGHT)
    elif cat == "ELECTRICO":
        c.rect(70, 100, 250, 220, DARK); c.rect(92, 80, 122, 102, MID); c.rect(198, 80, 228, 102, MID)
        c.poly([(170, 112), (138, 170), (160, 170), (148, 210), (188, 150), (166, 150), (180, 112)], accent)
    elif cat == "SUSPENSION":
        c.rect(150, 40, 170, 78, MID); c.rect(146, 220, 174, 262, DARK)
        for i in range(7):
            y = 84 + i * 20
            c.line(115, y, 205, y + 12, 9, LIGHT if i % 2 else MID)
            c.line(205, y + 12, 115, y + 20, 9, LIGHT if i % 2 else MID)
        c.rect(150, 78, 170, 224, accent)
    elif cat == "MOTOR":
        c.rect(105, 64, 215, 230, LIGHT)
        for y in (88, 108, 128):
            c.rect(105, y, 215, y + 8, DARK)
        c.circle(cx, 182, 20, DARK); c.circle(cx, 182, 9, accent); c.rect(105, 214, 215, 230, MID)
    elif cat == "TRANSMISION":
        for i in range(12):
            a = 2 * math.pi * i / 12
            ca, sa = math.cos(a), math.sin(a)
            pts = [(cx + ca * r - sa * w, cy + sa * r + ca * w) for r, w in ((86, -12), (112, -9), (112, 9), (86, 12))]
            c.poly(pts, LIGHT)
        c.circle(cx, cy, 92, LIGHT); c.circle(cx, cy, 58, DARK); c.circle(cx, cy, 24, accent)
    elif cat == "DIRECCION":
        c.ring(cx, cy, 108, 88, DARK)
        for a in (math.pi / 2, math.pi * 1.17, math.pi * 1.83):
            c.line(cx, cy, cx + 92 * math.cos(a), cy + 92 * math.sin(a), 14, DARK)
        c.circle(cx, cy, 30, accent)
    elif cat == "REFRIGERACION":
        c.rect(70, 70, 250, 230, LIGHT); c.rect(58, 70, 76, 230, DARK); c.rect(244, 70, 262, 230, DARK)
        for x in range(90, 240, 16):
            c.rect(x, 78, x + 5, 222, MID)
        for y in range(92, 220, 22):
            c.rect(76, y, 244, y + 4, accent)
    elif cat == "ESCAPE":
        c.rect(50, 138, 130, 164, MID); c.circle(190, 150, 62, DARK); c.rect(130, 88, 252, 212, DARK)
        c.circle(130, 150, 62, DARK); c.circle(252, 150, 62, DARK); c.rect(236, 138, 300, 164, accent)
    elif cat == "CARROCERIA":
        c.poly([(40, 170), (60, 130), (110, 100), (200, 100), (250, 130), (285, 150), (290, 190), (40, 190)], accent)
        c.poly([(115, 108), (195, 108), (232, 135), (100, 135)], LIGHT)
        c.circle(100, 195, 30, DARK); c.circle(100, 195, 12, MID); c.circle(232, 195, 30, DARK); c.circle(232, 195, 12, MID)
    elif cat == "ILUMINACION":
        c.circle(cx, 120, 62, (250, 230, 120)); c.circle(cx, 120, 40, (255, 248, 200))
        c.rect(135, 178, 185, 232, MID); c.rect(135, 196, 185, 204, DARK); c.rect(135, 214, 185, 222, DARK)
        for a in range(-60, 61, 30):
            r = math.radians(a - 90)
            c.line(cx + 76 * math.cos(r), 120 + 76 * math.sin(r), cx + 104 * math.cos(r), 120 + 104 * math.sin(r), 7, accent)
    else:  # ACEITES_LUBRICANTES
        c.rect(110, 90, 210, 238, LIGHT); c.rect(140, 54, 180, 94, LIGHT); c.rect(136, 40, 184, 58, accent)
        c.rect(110, 130, 210, 196, accent); c.circle(cx, 163, 22, LIGHT)


def make_png(cat, brand, v):
    top = tuple(min(255, int(x * 1.0)) for x in CAT_BG[cat])
    bot = tuple(min(255, int(x * 0.55)) for x in CAT_BG[cat])
    c = Canvas(320, 320, top, bot)
    accent = BRAND_COLOR[brand]
    _glyph(c, cat, accent, v)
    c.rect(0, 292, 320, 320, accent)
    return c.png()


# ----------------------------------------------------------------------------------------------
# SQL
# ----------------------------------------------------------------------------------------------
class Raw(str):
    pass


def lit(v):
    if v is None:
        return "NULL"
    if isinstance(v, Raw):
        return str(v)
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, (int, float)):
        return repr(round(v, 6)) if isinstance(v, float) else str(v)
    if isinstance(v, datetime):
        return "'" + v.astimezone(UTC).strftime("%Y-%m-%d %H:%M:%S+00") + "'"
    return "'" + str(v).replace("'", "''") + "'"


def insert(table, cols, rows, chunk=250, overriding=False):
    out = []
    for i in range(0, len(rows), chunk):
        vals = ",\n".join("(" + ",".join(lit(x) for x in r) + ")" for r in rows[i:i + chunk])
        ov = " OVERRIDING SYSTEM VALUE" if overriding else ""
        out.append(f"INSERT INTO public.{table} ({', '.join(cols)}){ov} VALUES\n{vals}\nON CONFLICT DO NOTHING;")
    return out


def slug(s):
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode().lower()
    return "".join(ch for ch in s if ch.isalnum())


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out-dir", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "out"))
    ap.add_argument("--seed", type=int, default=20261105)
    ap.add_argument("--scale", type=float, default=1.0, help="multiplicador de volumen (defecto 1.0)")
    ap.add_argument("--single", action="store_true", help="un solo archivo en vez de partes")
    ap.add_argument("--max-part-kb", type=int, default=900)
    ap.add_argument("--rollback", action="store_true", help="termina cada parte con ROLLBACK (prueba en seco)")
    ap.add_argument("--upload", action="store_true", help="sube las imágenes a Supabase Storage y sale")
    ap.add_argument("--env-file", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".env"))
    ap.add_argument("--now", default="2026-11-05")
    args = ap.parse_args()

    rng = random.Random(args.seed)
    sc = args.scale
    now = datetime.fromisoformat(args.now).replace(tzinfo=UTC)
    n_customers = max(5, int(500 * sc))
    n_user_products = max(6, int(600 * sc))
    n_store_products = max(6, int(450 * sc))
    n_user_orders = max(5, int(380 * sc))
    n_store_orders = max(5, int(290 * sc))

    def rand_dt(a, b, recent_bias=True):
        span = (b - a).total_seconds()
        t = rng.random() ** (0.8 if recent_bias else 1.0)
        # horario comercial colombiano (UTC-5 -> 13:00-24:00 UTC)
        d = a + timedelta(seconds=t * span)
        return d.replace(hour=rng.choice([13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23]), minute=rng.randint(0, 59),
                         second=rng.randint(0, 59), microsecond=0)

    pw = Raw(f"(SELECT password FROM public.accounts WHERE email='{USER_EMAIL}')")
    city_w = [c[4] for c in CITIES]

    # ---------------- clientes ----------------
    persons, accounts, customers = [], [], []
    seen_email = set()
    for i in range(n_customers):
        fn, ln = rng.choice(FIRST), rng.choice(LAST)
        city = rng.choices(CITIES, city_w)[0]
        pid, aid = uid("person", i), uid("account", i)
        email = f"{slug(fn)}.{slug(ln)}{i + 1}@example.com"
        assert email not in seen_email
        seen_email.add(email)
        created = rand_dt(datetime(2026, 1, 5, tzinfo=UTC), datetime(2026, 2, 25, tzinfo=UTC), False)
        persons.append((pid, fn, ln, f"99{i + 10000000:08d}"[:10], datetime(rng.randint(1960, 2004), rng.randint(1, 12), rng.randint(1, 28)).date().isoformat(),
                        f"+57 3{rng.randint(0, 2)}{rng.randint(0, 9)}{rng.randint(1000000, 9999999)}", created))
        accounts.append((aid, pid, email, pw, CUSTOMER_TYPE, ST["ACTIVE"], Raw("'{}'::jsonb"), created))
        customers.append({"person": pid, "city": city})
    cust_w = [1 / (k + 1) ** 0.6 for k in range(len(customers))]
    rng.shuffle(customers)

    # ---------------- almacenes ----------------
    stores = [{"id": USER_STORE["id"], "name": USER_STORE["name"], "code": USER_STORE["code"], "user": True,
               "branches": [{"id": b[0], "name": b[1]} for b in USER_STORE["branches"]],
               "created": datetime(2026, 10, 15, tzinfo=UTC), "start": datetime(2026, 10, 19, tzinfo=UTC),
               "n_products": n_user_products, "n_orders": n_user_orders, "cov": [0.9, 0.7]}]
    store_rows, branch_rows, loc_rows, collab_rows, mgr_persons, mgr_accounts = [], [], [], [], [], []
    for k, (name, code, city_idx) in enumerate(SYNTH_STORES):
        sid = uid("store", k)
        created = datetime(2026, 2, 1, tzinfo=UTC) + timedelta(days=k * 3)
        n_br = 3 if k in (2, 3) else 2
        brs = []
        for j in range(n_br):
            city = CITIES[(city_idx + j * 4) % len(CITIES)]
            lid, bid = uid("loc", k, j), uid("branch", k, j)
            loc_rows.append((lid, f"{rng.choice(STREETS)} {rng.randint(1, 120)} # {rng.randint(1, 99)}-{rng.randint(1, 99)}",
                             city[0], city[1], "Colombia", round(city[2] + rng.uniform(-.02, .02), 6),
                             round(city[3] + rng.uniform(-.02, .02), 6), created))
            brs.append({"id": bid, "name": f"Sucursal {city[0]}" + (" Centro" if j == 0 else " Norte"),
                        "loc": lid, "phone": f"3{rng.randint(0, 2)}{rng.randint(0, 9)}{rng.randint(1000000, 9999999)}"})
            branch_rows.append((bid, sid, brs[-1]["name"], lid, brs[-1]["phone"], ST["ACTIVE"], created))
        store_rows.append((sid, name, f"9{rng.randint(10000000, 99999999)}-{rng.randint(0, 9)}",
                           f"contacto@{slug(name)}.example.com", ST["ACTIVE"], Raw("'{}'::jsonb"), "SYNTHETIC", created))
        mfn, mln = rng.choice(FIRST), rng.choice(LAST)
        mpid, maid = uid("mgr-person", k), uid("mgr-account", k)
        mgr_persons.append((mpid, mfn, mln, f"98{k + 20000000:08d}"[:10], "1980-06-15",
                            f"+57 3{rng.randint(0, 2)}{rng.randint(0, 9)}{rng.randint(1000000, 9999999)}", created))
        mgr_accounts.append((maid, mpid, f"gerente.{slug(name)}@example.com", pw, MANAGER_TYPE, ST["ACTIVE"],
                             Raw("'{}'::jsonb"), created))
        collab_rows.append((uid("collab", k), mpid, sid, ST["ACTIVE"], created))
        stores.append({"id": sid, "name": name, "code": code, "user": False, "branches": brs, "created": created,
                       "start": datetime(2026, 3, 1, tzinfo=UTC), "n_products": n_store_products,
                       "n_orders": n_store_orders, "cov": [0.85] * n_br})

    # ---------------- productos ----------------
    pool = []
    for cat, (brands, items) in CATALOG.items():
        for ti, (tipo, pmin, pmax, variants, universal) in enumerate(items):
            for br in brands:
                for var in (variants or VEH):
                    pool.append((cat, ti, tipo, br, var, pmin, pmax, universal))
    product_rows, image_rows, inv_rows_src = [], [], []
    products = []
    image_plan = {}  # (cat,brand,v) -> png
    for s in stores:
        sample = rng.sample(pool, s["n_products"])
        for n, (cat, ti, tipo, br, var, pmin, pmax, universal) in enumerate(sample):
            comboid = uid("combo", cat, ti, br, var)
            h = int(comboid.replace("-", "")[:8], 16)
            base = pmin + (pmax - pmin) * ((h % 1000) / 1000) ** 1.3
            part = f"{br[:3].upper()}-{CAT_PREFIX[cat]}{h % 900000 + 100000}"
            garantia = rng.choice([3, 6, 12, 12, 24])
            pid = uid("product", s["id"], comboid)
            name = f"{tipo} {br} {var}"
            desc = (f"{tipo} marca {br}, " + ("de uso universal" if universal else f"compatible con {var}")
                    + f". Repuesto nuevo con {garantia} meses de garantía. Referencia {part}.")
            created = s["created"] + timedelta(days=1, hours=rng.randint(0, 40), minutes=rng.randint(0, 59))
            attrs = Raw("'" + ('{"garantia_meses": %d, "condicion": "nuevo", "origen": "%s"}' % (
                garantia, rng.choice(["Alemania", "Italia", "Japón", "Brasil", "México", "Colombia", "China"]))) + "'::jsonb")
            product_rows.append((pid, s["id"], name, desc, f"{s['code']}-{CAT_PREFIX[cat]}-{n + 1:05d}", part, BRAND[br],
                                 CAT[cat], ST["ACTIVE"], attrs, universal, created))
            v = ti % 4
            path = f"products/{pid}/{uid('img', pid)}.png"
            image_rows.append((uid("image", pid), pid, path, "image/png", True, 0, created))
            image_plan[path] = (cat, br, v)
            products.append({"id": pid, "store": s["id"], "cat": cat, "name": name, "sku": product_rows[-1][4],
                             "base": base, "created": created})
    prod_by_store = {}
    for p in products:
        prod_by_store.setdefault(p["store"], []).append(p)

    if args.upload:
        return upload(args, image_plan)

    # ---------------- inventario ----------------
    inventory = {}  # branch_id -> [items]
    for s in stores:
        factor = rng.uniform(0.92, 1.12)
        for bi, b in enumerate(s["branches"]):
            lst = []
            for p in prod_by_store[s["id"]]:
                if rng.random() <= s["cov"][bi] or (bi == 0):
                    price = max(1000, int(round(p["base"] * factor * rng.uniform(0.97, 1.05) / 100.0)) * 100)
                    cost = int(round(price * rng.uniform(0.60, 0.78) / 100.0)) * 100
                    lst.append({"id": uid("inv", b["id"], p["id"]), "branch": b["id"], "p": p, "price": price,
                                "cost": cost, "sold": 0, "created": p["created"] + timedelta(hours=rng.randint(1, 12))})
            inventory[b["id"]] = lst
    inv_weights = {}
    for bid, lst in inventory.items():
        rng.shuffle(lst)
        inv_weights[bid] = [1 / (k + 1) ** 0.75 for k in range(len(lst))]

    # ---------------- pedidos ----------------
    slots = []
    for s in stores:
        for _ in range(s["n_orders"]):
            slots.append((rand_dt(s["start"], now - timedelta(hours=2)), s))
    slots.sort(key=lambda x: x[0])
    order_rows, sale_rows, detail_rows, pay_rows, addr_rows = [], [], [], [], []
    state_counts = {}
    for idx, (placed, s) in enumerate(slots):
        cust = rng.choices(customers, cust_w)[0]
        oid = uid("order", idx)
        age = (now - placed).days
        if age > 21:
            code = rng.choices(["DELIVERED", "CANCELLED", "REJECTED"], [86, 10, 4])[0]
        elif age > 6:
            code = rng.choices(["DELIVERED", "SHIPPED", "PREPARING", "CANCELLED", "REJECTED"], [60, 20, 7, 10, 3])[0]
        else:
            code = rng.choices(["DELIVERED", "SHIPPED", "PREPARING", "CONFIRMED", "PENDING", "CANCELLED", "REJECTED"],
                               [15, 22, 18, 17, 14, 11, 3])[0]
        state_counts[code] = state_counts.get(code, 0) + 1
        cancelled = code == "CANCELLED"
        counts_stock = code not in ("CANCELLED", "REJECTED")
        targets = [(s, rng.choice(s["branches"]))]
        if rng.random() < 0.07 and len(stores) > 1:
            os_ = rng.choice([x for x in stores if x is not s])
            targets.append((os_, rng.choice(os_["branches"])))
        order_sub = order_ship = 0
        for ti, (ts, br) in enumerate(targets):
            lst = inventory[br["id"]]
            nl = rng.choices([1, 2, 3, 4], [50, 28, 14, 8])[0] if ti == 0 else rng.choice([1, 2])
            picked, seen = [], set()
            while len(picked) < min(nl, len(lst)):
                it = rng.choices(lst, inv_weights[br["id"]])[0]
                if it["id"] not in seen:
                    seen.add(it["id"]); picked.append(it)
            sid = uid("sale", oid, br["id"])
            sub = 0
            for it in picked:
                units = rng.choices([1, 2, 3, 4, 6], [62, 22, 9, 5, 2])[0]
                sub += units * it["price"]
                if counts_stock:
                    it["sold"] += units
                detail_rows.append((uid("line", sid, it["id"]), oid, sid, it["id"], units, it["price"], it["p"]["id"],
                                    it["p"]["name"], it["p"]["sku"], CAT[it["p"]["cat"]], it["price"], 0,
                                    it["cost"], 0.19, "UNKNOWN", placed))
            pickup = rng.random() < 0.28
            ship = 0 if pickup else (0 if sub >= 300000 else rng.choice([7900, 9900, 12900, 15900, 18900]))
            order_sub += sub
            order_ship += ship
            conf = placed + timedelta(minutes=rng.randint(5, 180)) if code not in ("PENDING", "REJECTED") else None
            ready = (conf + timedelta(hours=rng.randint(2, 30))) if code in ("SHIPPED", "DELIVERED") and conf else None
            canc = placed + timedelta(hours=rng.randint(1, 48)) if cancelled else None
            sale_rows.append((sid, oid, br["id"], ST[code], sub, ship, sub + ship, placed, "STORE_PICKUP" if pickup else "SHIPPING",
                              conf, ready, canc, rng.choice(CANCEL_REASONS) if cancelled else None,
                              None if pickup else placed + timedelta(days=1),
                              None if pickup else placed + timedelta(days=rng.randint(3, 7)), 0))
        reason = next((r[12] for r in sale_rows[-len(targets):] if r[12]), None) if cancelled else None
        if cancelled:  # mismo motivo en pedido y ventas
            for k in range(len(sale_rows) - len(targets), len(sale_rows)):
                r = list(sale_rows[k]); r[12] = reason; sale_rows[k] = tuple(r)
        total = order_sub + order_ship
        order_rows.append((oid, 100000 + idx, cust["person"], ST[code],
                           rng.choices(["WEB", "MOBILE_WEB", "APP", "STORE_ASSISTED"], [60, 25, 10, 5])[0],
                           "SYNTHETIC", "COP", order_sub, 0, order_ship, total, placed,
                           placed + timedelta(hours=rng.randint(1, 48)) if cancelled else None, reason, placed))
        # pago
        wompi = rng.random() < 0.55
        method = PM["WOMPI"] if wompi else rng.choice([PM["CARD"], PM["TRANSFER"], PM["CASH"]])
        pstat = {"PENDING": "PENDING", "CANCELLED": "CANCELLED", "REJECTED": "REJECTED"}.get(code, "APPROVED")
        paid = placed + timedelta(minutes=rng.randint(1, 30)) if pstat == "APPROVED" else None
        failed = placed + timedelta(minutes=rng.randint(1, 30)) if pstat == "REJECTED" else None
        pay_rows.append((uid("payment", oid), oid, method, PS[pstat], "WOMPI" if wompi else "SIMULATED", f"SEED-{idx + 1:07d}",
                         total, "COP", paid, "SANDBOX",
                         {"APPROVED": "APPROVED", "PENDING": "PENDING", "CANCELLED": "VOIDED", "REJECTED": "DECLINED"}[pstat],
                         "CARD_DECLINED" if pstat == "REJECTED" else None,
                         "Tarjeta rechazada por el emisor" if pstat == "REJECTED" else None, failed, placed))
        street = f"{rng.choice(STREETS)} {rng.randint(1, 150)} # {rng.randint(1, 99)}-{rng.randint(1, 99)}"
        c = cust["city"]
        addr_rows.append((uid("addr", oid), oid, street, rng.choice([None, None, f"Apto {rng.randint(101, 1204)}", f"Casa {rng.randint(1, 40)}"]),
                          c[0], c[1], "Colombia", round(c[2] + rng.uniform(-.03, .03), 6), round(c[3] + rng.uniform(-.03, .03), 6), placed))

    # stock final: lo vendido (no cancelado) + remanente
    inv_rows = []
    for lst in inventory.values():
        for it in lst:
            r = rng.random()
            rem = 0 if r < 0.04 else rng.randint(1, 6) if r < 0.14 else rng.randint(7, 40) if r < 0.6 else rng.randint(41, 220)
            inv_rows.append((it["id"], it["branch"], it["p"]["id"], it["sold"] + rem, it["price"], it["cost"], it["created"]))

    # ---------------- SQL ----------------
    stmts = []
    stmts += insert("locations", ["id", "address_line", "city", "department", "country", "latitude", "longitude", "created_at"], loc_rows)
    stmts += insert("persons", ["id", "name", "surname", "identification", "date_birth", "number_phone", "created_at"], persons + mgr_persons)
    stmts += insert("accounts", ["id", "id_person", "email", "password", "type_id", "state_id", "configuration", "created_at"], accounts + mgr_accounts)
    stmts += insert("store", ["id", "business_name", "nit", "business_email", "state", "configuration", "data_origin", "created_at"], store_rows)
    stmts += insert("branches", ["id", "store_id", "name", "location_id", "number_phone", "state", "created_at"], branch_rows)
    stmts += insert("collaborators", ["id", "person_id", "collab_store_id", "state", "created_at"], collab_rows)
    stmts += insert("products", ["id", "store_id", "name", "description", "sku", "part_number", "manufacturer_id", "category_id",
                                 "state_id", "attributes", "is_universal", "created_at"], product_rows)
    stmts += insert("product_images", ["id", "product_id", "storage_path", "content_type", "is_main", "display_order", "created_at"], image_rows)
    stmts += insert("inventory", ["id", "branch_id", "product_id", "stock", "price", "unit_cost", "created_at"], inv_rows)
    stmts += insert("orders", ["id", "order_number", "person_id", "state_id", "channel", "data_origin", "currency", "subtotal",
                               "discount_total", "shipping_total", "total", "placed_at", "cancelled_at", "cancellation_reason_code",
                               "created_at"], order_rows, overriding=True)
    stmts += insert("sales", ["id", "order_id", "branch_id", "state_id", "subtotal", "shipping_cost", "total", "created_at",
                              "fulfillment_type", "confirmed_at", "ready_at", "cancelled_at", "cancellation_reason_code",
                              "promised_ship_by", "promised_delivery_by", "discount_total"], sale_rows)
    stmts += insert("order_details", ["id", "order_id", "sale_id", "inventory_id", "units", "unit_price", "product_id",
                                      "product_name_snapshot", "sku_snapshot", "category_id_snapshot", "list_unit_price",
                                      "discount_amount", "unit_cost_snapshot", "vat_rate", "compatibility_status_shown",
                                      "created_at"], detail_rows)
    stmts += insert("payments", ["id", "order_id", "payment_method_id", "payment_status_id", "provider", "provider_reference",
                                 "amount", "currency", "paid_at", "environment", "provider_status", "failure_code",
                                 "failure_message", "failed_at", "created_at"], pay_rows)
    stmts += insert("order_addresses", ["id", "order_id", "address_line", "address_complement", "city", "department", "country",
                                        "latitude", "longitude", "created_at"], addr_rows)

    os.makedirs(args.out_dir, exist_ok=True)
    for f in os.listdir(args.out_dir):
        if f.startswith("seed_") and f.endswith(".sql"):
            os.remove(os.path.join(args.out_dir, f))
    end = "ROLLBACK;" if args.rollback else "COMMIT;"
    limit = 10 ** 12 if args.single else args.max_part_kb * 1024
    parts, cur, size = [], [], 0
    for st in stmts:
        if cur and size + len(st) > limit:
            parts.append(cur); cur, size = [], 0
        cur.append(st); size += len(st)
    parts.append(cur)
    for i, p in enumerate(parts, 1):
        with open(os.path.join(args.out_dir, f"seed_{i:02d}.sql"), "w", encoding="utf-8") as fh:
            fh.write(f"-- Autopartco datos sintéticos, parte {i}/{len(parts)} (seed={args.seed}, scale={sc})\nBEGIN;\n"
                     + "\n".join(p) + f"\n{end}\n")

    total_rows = sum(map(len, (loc_rows, persons, mgr_persons, accounts, mgr_accounts, store_rows, branch_rows, collab_rows,
                               product_rows, image_rows, inv_rows, order_rows, sale_rows, detail_rows, pay_rows, addr_rows)))
    print(f"Archivos: {len(parts)} en {args.out_dir}")
    print(f"clientes={len(persons)} gerentes={len(mgr_persons)} almacenes nuevos={len(store_rows)} sucursales={len(branch_rows)}")
    print(f"productos={len(product_rows)} (almacén de {USER_EMAIL}: {len(prod_by_store[USER_STORE['id']])}) imágenes={len(image_rows)}")
    print(f"inventario={len(inv_rows)} pedidos={len(order_rows)} ventas={len(sale_rows)} líneas={len(detail_rows)} pagos={len(pay_rows)}")
    print(f"estados de pedido: {state_counts}")
    print(f"filas totales: {total_rows}")
    user_orders = sum(1 for r in sale_rows if r[2] in {b['id'] for b in stores[0]['branches']})
    print(f"ventas del almacén del usuario: {user_orders}")


def upload(args, image_plan):
    import json
    import urllib.request
    env = {}
    with open(args.env_file, encoding="utf-8") as fh:
        for line in fh:
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.strip().split("=", 1)
                env[k.strip()] = v.strip().strip('"').strip("'")
    base, key, bucket = env["SUPABASE_URL"].rstrip("/"), env["SUPABASE_SECRET_KEY"], env["SUPABASE_BUCKET_PRODUCTS_IMGS"]
    cache = {}

    def png_for(k):
        if k not in cache:
            cache[k] = make_png(*k)
        return cache[k]

    for k in set(image_plan.values()):  # genera una vez cada variante (hilo principal)
        png_for(k)

    def put(item):
        path, k = item
        req = urllib.request.Request(f"{base}/storage/v1/object/{bucket}/{path}", data=cache[k], method="POST", headers={
            "Authorization": f"Bearer {key}", "apikey": key, "Content-Type": "image/png", "x-upsert": "true"})
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.status
        except Exception as e:  # noqa: BLE001
            return str(e)

    items = list(image_plan.items())
    ok = 0
    with ThreadPoolExecutor(8) as ex:
        for n, res in enumerate(ex.map(put, items), 1):
            ok += res == 200
            if res != 200:
                print("FALLO", items[n - 1][0], res, file=sys.stderr)
            if n % 250 == 0:
                print(f"{n}/{len(items)}")
    print(f"Subidas correctas: {ok}/{len(items)}")


if __name__ == "__main__":
    main()
