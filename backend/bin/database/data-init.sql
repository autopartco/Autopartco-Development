
-- ============================================================
-- AUTOPARTCO - DATA INIT
-- PostgreSQL
-- ============================================================

BEGIN;

-- ============================================================
-- CATEGORIAS
-- ============================================================

INSERT INTO categories(code)
VALUES
    ('BRAKES'),
    ('FILTERS'),
    ('SUSPENSION'),
    ('ENGINE'),
    ('ELECTRICAL'),
    ('TRANSMISSION'),
    ('STEERING'),
    ('COOLING'),
    ('EXHAUST'),
    ('BODY'),
    ('LIGHTING'),
    ('IGNITION'),
    ('FUEL_SYSTEM'),
    ('ACCESSORIES'),
    ('LUBRICANTS')
ON CONFLICT(code) DO NOTHING;

-- ============================================================
-- FABRICANTES
-- ============================================================

INSERT INTO manufacturers(name)
VALUES
    ('Bosch'),
    ('Brembo'),
    ('Valeo'),
    ('Mann Filter'),
    ('Mahle'),
    ('NGK'),
    ('Denso'),
    ('SKF'),
    ('Monroe'),
    ('Sachs'),
    ('Gates'),
    ('Dayco'),
    ('LUK'),
    ('Exedy'),
    ('KYB'),
    ('TRW'),
    ('ACDelco'),
    ('Motorcraft'),
    ('Continental'),
    ('Hella')
ON CONFLICT(name) DO NOTHING;

-- ============================================================
-- ESPECIFICACIONES
-- ============================================================

INSERT INTO specifications(name)
VALUES
    ('Voltaje'),
    ('Peso'),
    ('Longitud'),
    ('Diámetro'),
    ('Ancho'),
    ('Altura'),
    ('Capacidad')
ON CONFLICT(name) DO NOTHING;

-- ============================================================
-- UNIDADES
-- ============================================================

INSERT INTO units(name, code)
VALUES
    ('Voltio', 'V'),
    ('Kilogramo', 'KG'),
    ('Milímetro', 'MM'),
    ('Centímetro', 'CM'),
    ('Litro', 'L')
ON CONFLICT(name) DO NOTHING;

-- ============================================================
-- UNIDADES DE ESPECIFICACIONES
-- ============================================================

INSERT INTO units_specifications(specification_id, unit_id)
SELECT
    s.id,
    u.id
FROM specifications s
CROSS JOIN units u
WHERE
    (s.name = 'Voltaje' AND u.code = 'V')
    OR
    (s.name IN ('Peso') AND u.code = 'KG')
    OR
    (s.name IN ('Longitud','Diámetro','Ancho','Altura') AND u.code = 'MM')
    OR
    (s.name = 'Capacidad' AND u.code = 'L')
ON CONFLICT(specification_id, unit_id) DO NOTHING;

-- ============================================================
-- UBICACIONES
-- ============================================================

INSERT INTO locations(
    address_line,
    address_complement,
    city,
    department,
    country,
    postal_code,
    latitude,
    longitude,
    provider,
    metadata
)
SELECT
    'Carrera ' || (10 + (g % 80)) ||
        ' # ' || (15 + (g % 60)) || '-' || (10 + (g % 40)),
    CASE
        WHEN g % 3 = 0 THEN 'Local ' || ((g % 20) + 1)
        ELSE NULL
    END,
    CASE (g % 8)
        WHEN 0 THEN 'Cartagena'
        WHEN 1 THEN 'Barranquilla'
        WHEN 2 THEN 'Valledupar'
        WHEN 3 THEN 'Bosconia'
        WHEN 4 THEN 'Sincelejo'
        WHEN 5 THEN 'Monteria'
        WHEN 6 THEN 'Santa Marta'
        ELSE 'Bogota'
    END,
    CASE (g % 8)
        WHEN 0 THEN 'Bolivar'
        WHEN 1 THEN 'Atlantico'
        WHEN 2 THEN 'Cesar'
        WHEN 3 THEN 'Cesar'
        WHEN 4 THEN 'Sucre'
        WHEN 5 THEN 'Cordoba'
        WHEN 6 THEN 'Magdalena'
        ELSE 'Cundinamarca'
    END,
    'Colombia',
    LPAD((130001 + g)::TEXT, 6, '0'),
    8.90 + ((g % 300) / 1000.0),
    -75.50 - ((g % 300) / 1000.0),
    'INTERNAL',
    jsonb_build_object(
        'test_data', true,
        'source', 'data-init'
    )
FROM generate_series(1, 80) AS g;

-- ============================================================
-- PERSONAS
-- ============================================================

INSERT INTO persons(
    name,
    surname,
    identification,
    date_birth,
    number_phone
)
SELECT
    CASE (g % 10)
        WHEN 0 THEN 'Carlos'
        WHEN 1 THEN 'Juan'
        WHEN 2 THEN 'Luis'
        WHEN 3 THEN 'Miguel'
        WHEN 4 THEN 'Daniel'
        WHEN 5 THEN 'Andres'
        WHEN 6 THEN 'Camilo'
        WHEN 7 THEN 'Felipe'
        WHEN 8 THEN 'Santiago'
        ELSE 'David'
    END,
    CASE (g % 10)
        WHEN 0 THEN 'Gomez'
        WHEN 1 THEN 'Rodriguez'
        WHEN 2 THEN 'Martinez'
        WHEN 3 THEN 'Lopez'
        WHEN 4 THEN 'Garcia'
        WHEN 5 THEN 'Torres'
        WHEN 6 THEN 'Ramirez'
        WHEN 7 THEN 'Perez'
        WHEN 8 THEN 'Sanchez'
        ELSE 'Rojas'
    END,
    'TEST' || LPAD(g::TEXT, 12, '0'),
    DATE '1975-01-01' + ((g * 137) % 12000),
    '3' || LPAD((100000000 + g)::TEXT, 9, '0')
FROM generate_series(1,150) AS g
ON CONFLICT(identification) DO NOTHING;

-- ============================================================
-- CUENTAS
-- Password de prueba:
-- Password123!
--
-- Se genera mediante bcrypt usando pgcrypto.
-- ============================================================

INSERT INTO accounts(
    id_person,
    email,
    password,
    type_id,
    state_id,
    configuration
)
SELECT
    p.id,
    'usuario' || ROW_NUMBER() OVER (ORDER BY p.identification)
        || '@autopartco.test',

    crypt(
        'Password123!',
        gen_salt('bf', 10)
    ),

    CASE
        WHEN ROW_NUMBER() OVER (ORDER BY p.identification) <= 120
            THEN (
                SELECT id
                FROM types_accounts
                WHERE code = 'CUSTOMER'
            )

        WHEN ROW_NUMBER() OVER (ORDER BY p.identification) <= 145
            THEN (
                SELECT id
                FROM types_accounts
                WHERE code = 'STORE_MANAGER'
            )

        ELSE (
            SELECT id
            FROM types_accounts
            WHERE code = 'ROOT'
        )
    END,

    (
        SELECT id
        FROM states
        WHERE code = 'ACTIVE'
    ),

    jsonb_build_object(
        'test_data', true
    )

FROM persons p
WHERE p.identification LIKE 'TEST%'
ON CONFLICT(id_person) DO NOTHING;

-- ============================================================
-- ALMACENES
-- ============================================================

INSERT INTO store(
    bussnies_name,
    nit,
    bussnies_email,
    state,
    configuration
)
SELECT
    'Autopartes Colombia ' || LPAD(g::TEXT, 2, '0'),
    'TESTNIT' || LPAD(g::TEXT, 10, '0'),
    'almacen' || g || '@autopartco.test',
    (
        SELECT id
        FROM states
        WHERE code =
            CASE
                WHEN g <= 25 THEN 'ACTIVE'
                WHEN g <= 28 THEN 'PENDING'
                ELSE 'INACTIVE'
            END
    ),
    jsonb_build_object(
        'test_data', true,
        'whatsapp', '300555' || LPAD(g::TEXT, 4, '0'),
        'description',
            'Almacen ficticio para pruebas de Autopartco'
    )
FROM generate_series(1,30) AS g
ON CONFLICT DO NOTHING;

-- ============================================================
-- SUCURSALES
-- ============================================================

INSERT INTO branches(
    store_id,
    name,
    location_id,
    number_phone,
    state
)
SELECT
    s.id,
    'Sucursal ' || branch_number,
    l.id,
    '3' || LPAD(
        (200000000 + ROW_NUMBER() OVER (
            ORDER BY s.id, branch_number
        ))::TEXT,
        9,
        '0'
    ),
    (
        SELECT id
        FROM states
        WHERE code = 'ACTIVE'
    )
FROM store s
CROSS JOIN generate_series(1,2) AS branch_number
JOIN locations l
    ON l.id = (
        SELECT id
        FROM locations
        ORDER BY random()
        LIMIT 1
    )
WHERE s.bussnies_name LIKE 'Autopartes Colombia %'
ON CONFLICT(store_id,name) DO NOTHING;

-- ============================================================
-- ROLES
-- ============================================================

INSERT INTO roles(store_id, code)
SELECT
    s.id,
    r.code
FROM store s
CROSS JOIN (
    VALUES
        ('OWNER'),
        ('MANAGER'),
        ('SELLER'),
        ('INVENTORY_ADMIN')
) AS r(code)
ON CONFLICT(store_id,code) DO NOTHING;

-- ============================================================
-- COLABORADORES
-- ============================================================

INSERT INTO collaborators(
    person_id,
    collab_store_id,
    state
)
SELECT
    p.id,
    s.id,
    (
        SELECT id
        FROM states
        WHERE code = 'ACTIVE'
    )
FROM (
    SELECT
        id,
        ROW_NUMBER() OVER (ORDER BY identification) AS rn
    FROM persons
    WHERE identification LIKE 'TEST%'
) p
JOIN store s
    ON s.id = (
        SELECT id
        FROM store
        ORDER BY s.id
        LIMIT 1
        OFFSET ((p.rn - 1) % 30)
    )
WHERE p.rn BETWEEN 121 AND 145
ON CONFLICT(person_id,collab_store_id) DO NOTHING;

-- ============================================================
-- ASIGNACION DE COLABORADORES A SUCURSALES
-- ============================================================

INSERT INTO branches_collaborators(
    collaborator_id,
    branch_id
)
SELECT
    c.id,
    b.id
FROM collaborators c
JOIN branches b
    ON b.store_id = c.collab_store_id
ON CONFLICT(collaborator_id,branch_id) DO NOTHING;

-- ============================================================
-- ASIGNACION DE ROLES
-- ============================================================

INSERT INTO roles_collaborators(
    collaborator_id,
    role_id
)
SELECT
    c.id,
    r.id
FROM collaborators c
JOIN roles r
    ON r.store_id = c.collab_store_id
    AND r.code = 'SELLER'
ON CONFLICT(collaborator_id,role_id) DO NOTHING;

INSERT INTO roles_collaborators(
    collaborator_id,
    role_id
)
SELECT
    c.id,
    r.id
FROM collaborators c
JOIN accounts a
    ON a.id_person = c.person_id
JOIN types_accounts ta
    ON ta.id = a.type_id
JOIN roles r
    ON r.store_id = c.collab_store_id
    AND r.code = 'OWNER'
WHERE ta.code = 'STORE_MANAGER'
ON CONFLICT(collaborator_id,role_id) DO NOTHING;

-- ============================================================
-- PERMISOS
-- ============================================================

INSERT INTO permissions(code,description)
VALUES
    ('PRODUCT_CREATE','Crear productos'),
    ('PRODUCT_UPDATE','Actualizar productos'),
    ('PRODUCT_DELETE','Desactivar productos'),
    ('INVENTORY_READ','Consultar inventario'),
    ('INVENTORY_UPDATE','Actualizar inventario'),
    ('ORDER_READ','Consultar pedidos'),
    ('ORDER_UPDATE','Actualizar pedidos'),
    ('SHIPMENT_UPDATE','Actualizar envios'),
    ('USER_READ','Consultar usuarios'),
    ('STORE_UPDATE','Actualizar almacenes')
ON CONFLICT(code) DO NOTHING;

-- ============================================================
-- ASIGNACION DE PERMISOS
-- ============================================================

INSERT INTO roles_permissions(
    role_id,
    permission_id
)
SELECT
    r.id,
    p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('MANAGER','INVENTORY_ADMIN')
ON CONFLICT(role_id,permission_id) DO NOTHING;

-- ============================================================
-- MARCAS DE VEHICULOS
-- ============================================================

INSERT INTO make(name)
VALUES
    ('Toyota'),
    ('Chevrolet'),
    ('Mazda'),
    ('Renault'),
    ('Kia'),
    ('Hyundai'),
    ('Nissan'),
    ('Ford'),
    ('Volkswagen'),
    ('Honda'),
    ('Suzuki'),
    ('Mitsubishi')
ON CONFLICT(name) DO NOTHING;

-- ============================================================
-- MODELOS
-- ============================================================

INSERT INTO model(make_id,name)
SELECT
    m.id,
    x.model_name
FROM make m
JOIN (
    VALUES
        ('Toyota','Corolla'),
        ('Toyota','Yaris'),
        ('Toyota','Hilux'),
        ('Toyota','Fortuner'),

        ('Chevrolet','Onix'),
        ('Chevrolet','Tracker'),
        ('Chevrolet','Spark'),
        ('Chevrolet','Sail'),

        ('Mazda','Mazda 2'),
        ('Mazda','Mazda 3'),
        ('Mazda','CX-30'),
        ('Mazda','CX-5'),

        ('Renault','Logan'),
        ('Renault','Sandero'),
        ('Renault','Duster'),
        ('Renault','Kwid'),

        ('Kia','Picanto'),
        ('Kia','Sportage'),
        ('Kia','Rio'),
        ('Kia','Cerato'),

        ('Hyundai','i10'),
        ('Hyundai','i25'),
        ('Hyundai','Tucson'),
        ('Hyundai','Santa Fe'),

        ('Nissan','March'),
        ('Nissan','Versa'),
        ('Nissan','Kicks'),
        ('Nissan','Frontier'),

        ('Ford','Fiesta'),
        ('Ford','Focus'),
        ('Ford','Escape'),
        ('Ford','Ranger'),

        ('Volkswagen','Gol'),
        ('Volkswagen','Jetta'),
        ('Volkswagen','Polo'),
        ('Volkswagen','Tiguan'),

        ('Honda','Civic'),
        ('Honda','City'),
        ('Honda','CR-V'),
        ('Honda','HR-V'),

        ('Suzuki','Swift'),
        ('Suzuki','Vitara'),
        ('Suzuki','Jimny'),
        ('Suzuki','Baleno'),

        ('Mitsubishi','Lancer'),
        ('Mitsubishi','ASX'),
        ('Mitsubishi','Outlander'),
        ('Mitsubishi','Montero')
) AS x(make_name,model_name)
    ON x.make_name = m.name
ON CONFLICT(make_id,name) DO NOTHING;

-- ============================================================
-- VERSIONES / AÑOS
-- ============================================================

INSERT INTO versions(model_id,version)
SELECT
    m.id,
    y.year::TEXT
FROM model m
CROSS JOIN generate_series(2010,2024) AS y(year)
ON CONFLICT(model_id,version) DO NOTHING;

-- ============================================================
-- COMPATIBILIDADES
-- ============================================================

INSERT INTO compatibilities(
    make_id,
    model_id,
    version_id
)
SELECT
    m.make_id,
    m.id,
    v.id
FROM model m
JOIN versions v
    ON v.model_id = m.id
ON CONFLICT(make_id,model_id,version_id) DO NOTHING;

-- ============================================================
-- PRODUCTOS
-- ============================================================

INSERT INTO products(
    name,
    sku,
    part_number,
    manufacturer_id,
    category_id,
    state_id
)
SELECT
    CASE (g % 20)
        WHEN 0 THEN 'Pastillas de freno delanteras'
        WHEN 1 THEN 'Pastillas de freno traseras'
        WHEN 2 THEN 'Filtro de aceite'
        WHEN 3 THEN 'Filtro de aire'
        WHEN 4 THEN 'Filtro de combustible'
        WHEN 5 THEN 'Filtro de cabina'
        WHEN 6 THEN 'Bujia de encendido'
        WHEN 7 THEN 'Bateria automotriz'
        WHEN 8 THEN 'Amortiguador delantero'
        WHEN 9 THEN 'Amortiguador trasero'
        WHEN 10 THEN 'Correa de distribucion'
        WHEN 11 THEN 'Correa auxiliar'
        WHEN 12 THEN 'Bomba de agua'
        WHEN 13 THEN 'Radiador'
        WHEN 14 THEN 'Termostato'
        WHEN 15 THEN 'Kit de embrague'
        WHEN 16 THEN 'Disco de freno'
        WHEN 17 THEN 'Alternador'
        WHEN 18 THEN 'Sensor de temperatura'
        ELSE 'Bobina de encendido'
    END,

    'AUT-' || LPAD(g::TEXT,6,'0'),

    'PN-' || LPAD((100000 + g)::TEXT,6,'0'),

    (
        SELECT id
        FROM manufacturers
        ORDER BY id
        OFFSET ((g - 1) % 20)
        LIMIT 1
    ),

    (
        SELECT id
        FROM categories
        ORDER BY id
        OFFSET ((g - 1) % 15)
        LIMIT 1
    ),

    (
        SELECT id
        FROM states
        WHERE code = 'ACTIVE'
    )

FROM generate_series(1,200) AS g
ON CONFLICT(sku) DO NOTHING;

-- ============================================================
-- PRODUCTO ↔ VEHICULO
-- ============================================================

INSERT INTO products_compatibilies(
    product_id,
    compatibility_id
)
SELECT DISTINCT
    p.id,
    c.id
FROM products p
JOIN LATERAL (
    SELECT id
    FROM compatibilities
    ORDER BY md5(p.id::text || id::text)
    LIMIT 3
) c ON true
ON CONFLICT(product_id,compatibility_id) DO NOTHING;

-- ============================================================
-- ESPECIFICACIONES DE PRODUCTOS
-- ============================================================

INSERT INTO products_specifications(
    product_id,
    specification_id,
    specification_unit_id,
    value
)
SELECT
    p.id,
    us.specification_id,
    us.id,
    CASE
        WHEN s.name = 'Voltaje' THEN '12'
        WHEN s.name = 'Peso' THEN '4.5'
        WHEN s.name = 'Longitud' THEN '120'
        WHEN s.name = 'Diámetro' THEN '55'
        WHEN s.name = 'Ancho' THEN '80'
        WHEN s.name = 'Altura' THEN '70'
        ELSE '1'
    END
FROM (
    SELECT
        id,
        ROW_NUMBER() OVER (ORDER BY id) AS rn
    FROM products
) p
JOIN units_specifications us
    ON us.id = (
        SELECT id
        FROM units_specifications
        ORDER BY id
        OFFSET ((p.rn - 1) % 7)
        LIMIT 1
    )
JOIN specifications s
    ON s.id = us.specification_id
ON CONFLICT DO NOTHING;

-- ============================================================
-- INVENTARIO
--
-- Cada sucursal tendrá productos.
-- Esto permite comparar un mismo producto entre vendedores.
-- ============================================================

INSERT INTO inventory(
    branch_id,
    product_id,
    stock,
    price
)
SELECT
    b.id,
    p.id,

    5 + (
        (ROW_NUMBER() OVER (
            ORDER BY b.id,p.id
        ) * 7) % 96
    ),

    (
        25000 +
        (
            (ROW_NUMBER() OVER (
                ORDER BY b.id,p.id
            ) * 13500) % 650000
        )
    )::DECIMAL(12,2)

FROM branches b
CROSS JOIN (
    SELECT id
    FROM products
    ORDER BY id
    LIMIT 50
) p
ON CONFLICT(branch_id,product_id) DO NOTHING;

-- ============================================================
-- DIRECCIONES DE CLIENTES
-- ============================================================

INSERT INTO person_addresses(
    person_id,
    location_id,
    label,
    is_default
)
SELECT
    p.id,
    l.id,
    'Casa',
    true
FROM (
    SELECT id
    FROM persons
    WHERE identification LIKE 'TEST%'
    ORDER BY identification
    LIMIT 100
) p
CROSS JOIN LATERAL (
    SELECT id
    FROM locations
    ORDER BY random()
    LIMIT 1
) l;

-- ============================================================
-- PEDIDOS
-- ============================================================

INSERT INTO orders(
    person_id,
    state_id
)
SELECT
    p.id,

    (
        SELECT id
        FROM states
        WHERE code =
            CASE
                WHEN g % 10 = 0 THEN 'CANCELLED'
                WHEN g % 5 = 0 THEN 'DELIVERED'
                WHEN g % 3 = 0 THEN 'SHIPPED'
                WHEN g % 2 = 0 THEN 'CONFIRMED'
                ELSE 'PENDING'
            END
    )

FROM generate_series(1,100) AS g

JOIN (
    SELECT
        id,
        ROW_NUMBER() OVER (ORDER BY identification) AS rn
    FROM persons
    WHERE identification LIKE 'TEST%'
    LIMIT 100
) p
    ON p.rn = g;

-- ============================================================
-- DIRECCION SNAPSHOT DE CADA PEDIDO
-- ============================================================

INSERT INTO order_addresses(
    order_id,
    address_line,
    address_complement,
    city,
    department,
    country,
    postal_code,
    latitude,
    longitude
)
SELECT
    o.id,
    l.address_line,
    l.address_complement,
    l.city,
    l.department,
    l.country,
    l.postal_code,
    l.latitude,
    l.longitude
FROM orders o
JOIN LATERAL (
    SELECT
        l.*
    FROM locations l
    ORDER BY random()
    LIMIT 1
) l ON true
ON CONFLICT(order_id) DO NOTHING;

-- ============================================================
-- VENTAS
--
-- La mitad de los pedidos tienen productos de un vendedor.
-- Algunos pedidos tienen dos vendedores.
-- ============================================================

INSERT INTO sales(
    order_id,
    branch_id,
    state_id,
    subtotal,
    shipping_cost,
    total
)
SELECT
    o.id,
    b.id,

    CASE
        WHEN o.state_id = (
            SELECT id FROM states WHERE code = 'CANCELLED'
        )
        THEN (
            SELECT id FROM states WHERE code = 'CANCELLED'
        )
        ELSE (
            SELECT id FROM states WHERE code = 'CONFIRMED'
        )
    END,

    0,
    15000,
    15000

FROM orders o
JOIN LATERAL (
    SELECT id
    FROM branches
    ORDER BY md5(o.id::text || id::text)
    LIMIT 1
) b ON true

WHERE o.id IS NOT NULL
ON CONFLICT(order_id,branch_id) DO NOTHING;

-- ============================================================
-- SEGUNDA VENTA PARA PARTE DE LOS PEDIDOS
-- SIMULA MULTI-VENDEDOR
-- ============================================================

INSERT INTO sales(
    order_id,
    branch_id,
    state_id,
    subtotal,
    shipping_cost,
    total
)
SELECT
    o.id,
    b.id,
    (
        SELECT id
        FROM states
        WHERE code = 'CONFIRMED'
    ),
    0,
    15000,
    15000

FROM orders o
JOIN LATERAL (
    SELECT id
    FROM branches
    WHERE id <> (
        SELECT branch_id
        FROM sales
        WHERE order_id = o.id
        LIMIT 1
    )
    ORDER BY md5(o.id::text || id::text)
    LIMIT 1
) b ON true

WHERE o.order_number % 3 = 0
ON CONFLICT(order_id,branch_id) DO NOTHING;

-- ============================================================
-- DETALLES DE PEDIDO
-- ============================================================

INSERT INTO order_details(
    order_id,
    sale_id,
    inventory_id,
    units,
    unit_price
)
SELECT
    s.order_id,
    s.id,
    i.id,
    1 + ((ROW_NUMBER() OVER (
        ORDER BY s.id,i.id
    )) % 3),
    i.price
FROM sales s
JOIN LATERAL (
    SELECT
        i.id,
        i.price
    FROM inventory i
    WHERE i.branch_id = s.branch_id
      AND i.stock > 3
    ORDER BY md5(s.id::text || i.id::text)
    LIMIT 3
) i ON true;

-- ============================================================
-- ACTUALIZAR TOTALES DE VENTAS
-- ============================================================

UPDATE sales s
SET
    subtotal = x.subtotal,
    total = x.subtotal + s.shipping_cost
FROM (
    SELECT
        sale_id,
        SUM(units * unit_price) AS subtotal
    FROM order_details
    GROUP BY sale_id
) x
WHERE s.id = x.sale_id;

-- ============================================================
-- PAGOS
-- ============================================================

INSERT INTO payments(
    order_id,
    payment_method_id,
    payment_status_id,
    provider,
    provider_reference,
    amount,
    currency,
    paid_at
)
SELECT
    o.id,

    (
        SELECT id
        FROM payment_methods
        ORDER BY md5(o.id::text || id::text)
        LIMIT 1
    ),

    (
        SELECT id
        FROM payment_statuses
        WHERE code =
            CASE
                WHEN o.state_id = (
                    SELECT id FROM states WHERE code = 'CANCELLED'
                )
                THEN 'REJECTED'
                WHEN o.order_number % 4 = 0
                THEN 'PENDING'
                ELSE 'APPROVED'
            END
    ),

    'SIMULATED',

    'PAY-' || o.order_number,

    COALESCE(
        (
            SELECT SUM(
                od.units * od.unit_price
            )
            FROM order_details od
            WHERE od.order_id = o.id
        ),
        0
    ),

    'COP',

    CASE
        WHEN o.order_number % 4 = 0
        THEN NULL
        ELSE now()
    END

FROM orders o
ON CONFLICT(provider,provider_reference) DO NOTHING;

-- ============================================================
-- EVENTOS DE PAGO
-- ============================================================

INSERT INTO payment_events(
    payment_id,
    payment_status_id,
    message
)
SELECT
    p.id,
    p.payment_status_id,
    'Estado inicial del pago simulado'
FROM payments p;

-- ============================================================
-- RECIBOS
-- ============================================================

INSERT INTO receipts(
    order_id,
    payment_id,
    receipt_number,
    total
)
SELECT
    p.order_id,
    p.id,
    'REC-' || o.order_number,
    p.amount
FROM payments p
JOIN orders o
    ON o.id = p.order_id
JOIN payment_statuses ps
    ON ps.id = p.payment_status_id
WHERE ps.code = 'APPROVED'
ON CONFLICT(receipt_number) DO NOTHING;

-- ============================================================
-- ENVIOS
-- ============================================================

INSERT INTO shipments(
    sale_id,
    shipment_status_id,
    carrier,
    tracking_number,
    shipped_at,
    delivered_at
)
SELECT
    s.id,

    (
        SELECT id
        FROM shipment_statuses
        WHERE code =
            CASE
                WHEN o.state_id = (
                    SELECT id FROM states WHERE code = 'DELIVERED'
                )
                THEN 'DELIVERED'

                WHEN o.state_id = (
                    SELECT id FROM states WHERE code = 'SHIPPED'
                )
                THEN 'IN_TRANSIT'

                ELSE 'PREPARING'
            END
    ),

    'SIMULATED',

    'TRK-' || o.order_number || '-' ||
        SUBSTRING(s.id::TEXT,1,8),

    CASE
        WHEN o.state_id IN (
            SELECT id
            FROM states
            WHERE code IN ('SHIPPED','DELIVERED')
        )
        THEN now() - INTERVAL '2 days'
        ELSE NULL
    END,

    CASE
        WHEN o.state_id = (
            SELECT id
            FROM states
            WHERE code = 'DELIVERED'
        )
        THEN now() - INTERVAL '1 day'
        ELSE NULL
    END

FROM sales s
JOIN orders o
    ON o.id = s.order_id
JOIN payments p
    ON p.order_id = o.id
JOIN payment_statuses ps
    ON ps.id = p.payment_status_id
WHERE ps.code = 'APPROVED'
ON CONFLICT(sale_id) DO NOTHING;

-- ============================================================
-- HISTORIAL DE ENVIOS
-- ============================================================

INSERT INTO shipment_events(
    shipment_id,
    shipment_status_id,
    notes,
    event_at
)
SELECT
    sh.id,
    ss.id,
    'Evento generado para pruebas',
    now()
FROM shipments sh
CROSS JOIN LATERAL (
    SELECT id
    FROM shipment_statuses
    ORDER BY random()
    LIMIT 1
) ss;

-- ============================================================
-- RESEÑAS
-- ============================================================

INSERT INTO store_reviews(
    store_id,
    person_id,
    order_id,
    rating,
    comment
)
SELECT DISTINCT
    b.store_id,
    o.person_id,
    o.id,
    3 + ((o.order_number % 3)::INTEGER),
    CASE
        WHEN o.order_number % 2 = 0
        THEN 'Buen servicio y producto recibido correctamente.'
        ELSE 'Buena experiencia de compra.'
    END
FROM orders o
JOIN sales s
    ON s.order_id = o.id
JOIN branches b
    ON b.id = s.branch_id
WHERE o.state_id = (
    SELECT id
    FROM states
    WHERE code = 'DELIVERED'
)
ON CONFLICT(store_id,person_id,order_id) DO NOTHING;

-- ============================================================
-- NOTIFICACIONES
-- ============================================================

INSERT INTO notifications(
    account_id,
    title,
    message
)
SELECT
    a.id,
    'Pedido actualizado',
    'Tu pedido de prueba ha cambiado de estado.'
FROM accounts a
JOIN persons p
    ON p.id = a.id_person
JOIN orders o
    ON o.person_id = p.id
LIMIT 100;

-- ============================================================
-- RECLAMOS
-- ============================================================

INSERT INTO claims(
    person_id,
    order_id,
    store_id,
    reason,
    description,
    state_id
)
SELECT
    o.person_id,
    o.id,
    b.store_id,
    'Producto',
    'Reclamo ficticio generado para pruebas del backend.',
    (
        SELECT id
        FROM states
        WHERE code = 'PENDING'
    )
FROM orders o
JOIN sales sa
    ON sa.order_id = o.id
JOIN branches b
    ON b.id = sa.branch_id
JOIN store s
    ON s.id = b.store_id
WHERE o.order_number % 10 = 0;

-- ============================================================
-- AUDITORIA
-- ============================================================

INSERT INTO audit_logs(
    account_id,
    action,
    entity_type,
    entity_id,
    metadata
)
SELECT
    a.id,
    'TEST_DATA_CREATED',
    'ORDER',
    o.id,
    jsonb_build_object(
        'source', 'data-init',
        'test_data', true
    )
FROM orders o
JOIN persons p
    ON p.id = o.person_id
JOIN accounts a
    ON a.id_person = p.id;

-- ============================================================
-- MOVIMIENTOS DE INVENTARIO
-- ============================================================

INSERT INTO inventory_movements(
    inventory_id,
    order_detail_id,
    movement_type,
    quantity,
    previous_stock,
    new_stock
)
SELECT
    od.inventory_id,
    od.id,
    'SALE',
    od.units,
    i.stock + od.units,
    i.stock
FROM order_details od
JOIN inventory i
    ON i.id = od.inventory_id;

-- ============================================================
-- VERIFICACIONES
-- ============================================================

DO $$
DECLARE
    total_records BIGINT;
BEGIN

    SELECT
        (
            (SELECT COUNT(*) FROM persons) +
            (SELECT COUNT(*) FROM accounts) +
            (SELECT COUNT(*) FROM store) +
            (SELECT COUNT(*) FROM branches) +
            (SELECT COUNT(*) FROM make) +
            (SELECT COUNT(*) FROM model) +
            (SELECT COUNT(*) FROM versions) +
            (SELECT COUNT(*) FROM products) +
            (SELECT COUNT(*) FROM inventory) +
            (SELECT COUNT(*) FROM orders) +
            (SELECT COUNT(*) FROM order_details) +
            (SELECT COUNT(*) FROM payments) +
            (SELECT COUNT(*) FROM shipments)
        )
    INTO total_records;

    RAISE NOTICE '==============================================';
    RAISE NOTICE 'AUTOPARTCO DATA INIT';
    RAISE NOTICE 'Registros principales: %', total_records;
    RAISE NOTICE '==============================================';

    IF total_records < 500 THEN
        RAISE EXCEPTION
            'data-init no genero los 500 registros minimos. Total: %',
            total_records;
    END IF;

END $$;

COMMIT;

-- ============================================================
-- CONSULTAS DE COMPROBACION
-- ============================================================

-- SELECT COUNT(*) FROM persons;
-- SELECT COUNT(*) FROM accounts;
-- SELECT COUNT(*) FROM store;
-- SELECT COUNT(*) FROM branches;
-- SELECT COUNT(*) FROM products;
-- SELECT COUNT(*) FROM inventory;
-- SELECT COUNT(*) FROM orders;
-- SELECT COUNT(*) FROM order_details;
-- SELECT COUNT(*) FROM payments;
-- SELECT COUNT(*) FROM shipments;

-- Comparar un producto entre vendedores:
--
-- SELECT
--     p.name AS producto,
--     s.bussnies_name AS vendedor,
--     b.name AS sucursal,
--     i.price,
--     i.stock,
--     l.city
-- FROM inventory i
-- JOIN products p ON p.id = i.product_id
-- JOIN branches b ON b.id = i.branch_id
-- JOIN store s ON s.id = b.store_id
-- LEFT JOIN locations l ON l.id = b.location_id
-- WHERE p.sku = 'AUT-000001';

-- ============================================================
-- FIN data-init.sql
-- ============================================================
