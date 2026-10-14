-- =============================================================================
-- R__seed_geo_divipola.sql  (repetible)
-- Autopartco | Diseño predictivo §5.1. Solo Colombia.
--
-- Siembra los 33 departamentos de DIVIPOLA (32 departamentos + Bogotá D.C.)
-- con sus códigos DANE de 2 dígitos. Son códigos oficiales públicos.
-- natural_region: región natural PREDOMINANTE (varios departamentos ocupan más
--   de una: Cauca, Nariño y Valle del Cauca son andinos y pacíficos; Meta es
--   andino y orinoquense). Revísela si un modelo depende de ella.
-- Los ~1.100 municipios NO se siembran aquí: se cargan desde el archivo
--   oficial del DANE con pending-approval/load_divipola_municipalities.sql
--   (A10: verificar antes la licencia de Datos Abiertos Colombia). No se
--   inventan códigos.
-- Requiere: V202610140920. Idempotente (ON CONFLICT DO UPDATE de nombre y región).
-- =============================================================================

INSERT INTO public.geo_departments (code, name, natural_region, country_code) VALUES
    ('05', 'Antioquia',                                             'ANDINA',    'CO'),
    ('08', 'Atlántico',                                             'CARIBE',    'CO'),
    ('11', 'Bogotá, D.C.',                                          'ANDINA',    'CO'),
    ('13', 'Bolívar',                                               'CARIBE',    'CO'),
    ('15', 'Boyacá',                                                'ANDINA',    'CO'),
    ('17', 'Caldas',                                                'ANDINA',    'CO'),
    ('18', 'Caquetá',                                               'AMAZONIA',  'CO'),
    ('19', 'Cauca',                                                 'PACIFICA',  'CO'),
    ('20', 'Cesar',                                                 'CARIBE',    'CO'),
    ('23', 'Córdoba',                                               'CARIBE',    'CO'),
    ('25', 'Cundinamarca',                                          'ANDINA',    'CO'),
    ('27', 'Chocó',                                                 'PACIFICA',  'CO'),
    ('41', 'Huila',                                                 'ANDINA',    'CO'),
    ('44', 'La Guajira',                                            'CARIBE',    'CO'),
    ('47', 'Magdalena',                                             'CARIBE',    'CO'),
    ('50', 'Meta',                                                  'ORINOQUIA', 'CO'),
    ('52', 'Nariño',                                                'PACIFICA',  'CO'),
    ('54', 'Norte de Santander',                                    'ANDINA',    'CO'),
    ('63', 'Quindío',                                               'ANDINA',    'CO'),
    ('66', 'Risaralda',                                             'ANDINA',    'CO'),
    ('68', 'Santander',                                             'ANDINA',    'CO'),
    ('70', 'Sucre',                                                 'CARIBE',    'CO'),
    ('73', 'Tolima',                                                'ANDINA',    'CO'),
    ('76', 'Valle del Cauca',                                       'PACIFICA',  'CO'),
    ('81', 'Arauca',                                                'ORINOQUIA', 'CO'),
    ('85', 'Casanare',                                              'ORINOQUIA', 'CO'),
    ('86', 'Putumayo',                                              'AMAZONIA',  'CO'),
    ('88', 'Archipiélago de San Andrés, Providencia y Santa Catalina', 'INSULAR', 'CO'),
    ('91', 'Amazonas',                                              'AMAZONIA',  'CO'),
    ('94', 'Guainía',                                               'AMAZONIA',  'CO'),
    ('95', 'Guaviare',                                              'AMAZONIA',  'CO'),
    ('97', 'Vaupés',                                                'AMAZONIA',  'CO'),
    ('99', 'Vichada',                                               'ORINOQUIA', 'CO')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    natural_region = EXCLUDED.natural_region;
