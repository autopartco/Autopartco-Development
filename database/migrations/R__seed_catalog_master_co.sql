-- =============================================================================
-- R__seed_catalog_master_co.sql  (repetible; Flyway lo reaplica si cambia su contenido)
-- Autopartco | Datos maestros del catálogo para Colombia.
--
-- 1. categories    (code solo [A-Za-z0-9_-]; el frontend lo humaniza)
-- 2. manufacturers (marcas de repuestos; name único)
-- 3. make / model / versions / compatibilities (marcas populares en Colombia)
--
-- Idempotente: ON CONFLICT DO NOTHING SIN objetivo (cubre cualquier UNIQUE:
-- constraints e índices funcionales/parciales como uq_categories__code_lower,
-- uq_manufacturers__name_lower y uq_manufacturers__name_normalized, que
-- ignora mayúsculas/acentos: 'BOSCH' o 'Bosch ' sugeridos por un almacén no
-- rompen el seed). En categories y manufacturers se añade además NOT EXISTS
-- con la misma normalización de los índices. No toca filas existentes (ni
-- reactiva las que tengan deleted_at). Solo usa columnas base de init.sql; no usa
-- year_from/year_to (V202610141200) para no depender de esa migración.
-- Orden: categories, manufacturers, make, model, versions, compatibilities.
-- Transacción: SÍ.
-- =============================================================================

INSERT INTO categories (code)
SELECT v.code
FROM (VALUES ('FRENOS'), ('SUSPENSION'), ('MOTOR'), ('FILTROS'), ('ELECTRICO'),
             ('TRANSMISION'), ('DIRECCION'), ('REFRIGERACION'), ('ESCAPE'),
             ('CARROCERIA'), ('ILUMINACION'), ('ACEITES_LUBRICANTES')) AS v(code)
WHERE NOT EXISTS (SELECT 1 FROM categories c WHERE lower(c.code) = lower(v.code))
ON CONFLICT DO NOTHING;

INSERT INTO manufacturers (name)
SELECT v.name
FROM (VALUES ('Bosch'), ('Brembo'), ('Gates'), ('Mahle'), ('NGK'), ('Denso'),
             ('Valeo'), ('SKF'), ('Monroe'), ('Sachs'), ('TRW'), ('Mobil'),
             ('Castrol'), ('Genuine/OEM'), ('Philips')) AS v(name)
WHERE NOT EXISTS (
    SELECT 1 FROM manufacturers m
    WHERE m.deleted_at IS NULL
      AND public.immutable_unaccent(lower(m.name)) = public.immutable_unaccent(lower(v.name)))
ON CONFLICT DO NOTHING;

INSERT INTO make (name)
VALUES ('Chevrolet'), ('Renault'), ('Mazda'), ('Kia'), ('Hyundai'),
       ('Toyota'), ('Nissan'), ('Suzuki'), ('Ford'), ('Volkswagen')
ON CONFLICT DO NOTHING;

WITH m(make_name, model_name) AS (VALUES
    ('Chevrolet', 'Spark GT'), ('Chevrolet', 'Onix'), ('Chevrolet', 'Tracker'),
    ('Renault', 'Logan'), ('Renault', 'Duster'), ('Renault', 'Sandero'),
    ('Mazda', 'Mazda 2'), ('Mazda', 'Mazda 3'), ('Mazda', 'CX-30'),
    ('Kia', 'Picanto'), ('Kia', 'Sportage'), ('Kia', 'Rio'),
    ('Hyundai', 'Accent'), ('Hyundai', 'Tucson'), ('Hyundai', 'Creta'),
    ('Toyota', 'Hilux'), ('Toyota', 'Corolla'), ('Toyota', 'Prado'),
    ('Nissan', 'Versa'), ('Nissan', 'Kicks'), ('Nissan', 'Frontier'),
    ('Suzuki', 'Swift'), ('Suzuki', 'Vitara'), ('Suzuki', 'Jimny'),
    ('Ford', 'Fiesta'), ('Ford', 'Ranger'), ('Ford', 'Escape'),
    ('Volkswagen', 'Gol'), ('Volkswagen', 'Polo'), ('Volkswagen', 'Tiguan')
)
INSERT INTO model (make_id, name)
SELECT mk.id, m.model_name
FROM m
JOIN make mk ON mk.name = m.make_name
ON CONFLICT DO NOTHING;

WITH v(make_name, model_name, version) AS (VALUES
    ('Chevrolet', 'Spark GT', '2016-2020 1.2L'), ('Chevrolet', 'Spark GT', '2021-2024 1.2L'),
    ('Chevrolet', 'Onix', '2020-2024 1.0L Turbo'), ('Chevrolet', 'Onix', '2020-2024 1.2L'),
    ('Chevrolet', 'Tracker', '2021-2024 1.2L Turbo'), ('Chevrolet', 'Tracker', '2014-2020 1.8L'),
    ('Renault', 'Logan', '2017-2021 1.6L'), ('Renault', 'Logan', '2022-2024 1.6L'),
    ('Renault', 'Duster', '2016-2021 2.0L'), ('Renault', 'Duster', '2022-2024 1.3L Turbo'),
    ('Renault', 'Sandero', '2016-2020 1.6L'), ('Renault', 'Sandero', '2021-2024 1.6L'),
    ('Mazda', 'Mazda 2', '2016-2020 1.5L'), ('Mazda', 'Mazda 2', '2021-2024 1.5L'),
    ('Mazda', 'Mazda 3', '2014-2018 2.0L'), ('Mazda', 'Mazda 3', '2019-2024 2.0L'),
    ('Mazda', 'CX-30', '2020-2024 2.0L'), ('Mazda', 'CX-30', '2021-2024 2.5L'),
    ('Kia', 'Picanto', '2017-2020 1.2L'), ('Kia', 'Picanto', '2021-2024 1.2L'),
    ('Kia', 'Sportage', '2016-2021 2.0L'), ('Kia', 'Sportage', '2022-2024 2.0L'),
    ('Kia', 'Rio', '2018-2022 1.4L'), ('Kia', 'Rio', '2023-2024 1.4L'),
    ('Hyundai', 'Accent', '2012-2017 1.6L'), ('Hyundai', 'Accent', '2018-2022 1.6L'),
    ('Hyundai', 'Tucson', '2016-2021 2.0L'), ('Hyundai', 'Tucson', '2022-2024 2.0L'),
    ('Hyundai', 'Creta', '2018-2022 1.6L'), ('Hyundai', 'Creta', '2023-2024 1.5L'),
    ('Toyota', 'Hilux', '2016-2020 2.4L Diésel'), ('Toyota', 'Hilux', '2021-2024 2.8L Diésel'),
    ('Toyota', 'Corolla', '2014-2019 1.8L'), ('Toyota', 'Corolla', '2020-2024 2.0L'),
    ('Toyota', 'Prado', '2010-2017 4.0L'), ('Toyota', 'Prado', '2018-2024 2.8L Diésel'),
    ('Nissan', 'Versa', '2012-2019 1.6L'), ('Nissan', 'Versa', '2020-2024 1.6L'),
    ('Nissan', 'Kicks', '2018-2022 1.6L'), ('Nissan', 'Kicks', '2023-2024 1.6L'),
    ('Nissan', 'Frontier', '2014-2020 2.5L Diésel'), ('Nissan', 'Frontier', '2021-2024 2.3L Diésel'),
    ('Suzuki', 'Swift', '2012-2017 1.2L'), ('Suzuki', 'Swift', '2018-2024 1.2L'),
    ('Suzuki', 'Vitara', '2016-2021 1.6L'), ('Suzuki', 'Vitara', '2022-2024 1.5L Híbrido'),
    ('Suzuki', 'Jimny', '2012-2018 1.3L'), ('Suzuki', 'Jimny', '2019-2024 1.5L'),
    ('Ford', 'Fiesta', '2012-2017 1.6L'), ('Ford', 'Fiesta', '2018-2020 1.6L'),
    ('Ford', 'Ranger', '2013-2022 3.2L Diésel'), ('Ford', 'Ranger', '2023-2024 2.0L Turbo Diésel'),
    ('Ford', 'Escape', '2013-2019 2.0L Turbo'), ('Ford', 'Escape', '2020-2024 1.5L Turbo'),
    ('Volkswagen', 'Gol', '2014-2019 1.6L'), ('Volkswagen', 'Gol', '2020-2023 1.6L'),
    ('Volkswagen', 'Polo', '2018-2022 1.6L'), ('Volkswagen', 'Polo', '2023-2024 1.0L TSI'),
    ('Volkswagen', 'Tiguan', '2017-2021 1.4L TSI'), ('Volkswagen', 'Tiguan', '2022-2024 2.0L TSI')
)
INSERT INTO versions (model_id, version)
SELECT md.id, v.version
FROM v
JOIN make mk ON mk.name = v.make_name
JOIN model md ON md.make_id = mk.id AND md.name = v.model_name
ON CONFLICT DO NOTHING;

-- Una compatibilidad por versión (marca, modelo y versión coherentes con las
-- FKs compuestas fk_compatibility_model_make / fk_compatibility_version_model).
INSERT INTO compatibilities (make_id, model_id, version_id)
SELECT mk.id, md.id, vr.id
FROM make mk
JOIN model md ON md.make_id = mk.id
JOIN versions vr ON vr.model_id = md.id
WHERE mk.name IN ('Chevrolet', 'Renault', 'Mazda', 'Kia', 'Hyundai',
                  'Toyota', 'Nissan', 'Suzuki', 'Ford', 'Volkswagen')
ON CONFLICT DO NOTHING;
