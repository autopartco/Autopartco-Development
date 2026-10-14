-- =============================================================================
-- V202610141030__privacy_consents_pseudonyms.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §5.10 y §8 (Ley 1581 de 2012,
-- Decreto 1377 de 2013)
--
-- Propósito
--   - public.consent_purposes (catálogo + semilla). El texto de las
--     finalidades debe validarlo asesoría jurídica (§8.6).
--   - public.person_consents: solo inserción. Cada cambio es una fila nueva y
--     sirve como evidencia de la autorización.
--   - Vista public.person_consents_current: último estado por persona y finalidad.
--   - privacy.subject_pseudonyms: subject_key ALEATORIO (no derivado del id).
--     ON DELETE CASCADE desde persons. La supresión (habeas data) borra esta
--     fila y desvincula tracking/analytics/ml.
--   person_consents.ip_address y user_agent son PII de evidencia: acceso
--   restringido (A6).
--
-- Transacción: SÍ. Rollback: U202610141030__privacy_consents_pseudonyms.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE TABLE IF NOT EXISTS public.consent_purposes (
    code        VARCHAR(40)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    legal_basis VARCHAR(20)  NOT NULL,
    required    BOOLEAN      NOT NULL DEFAULT false,
    active      BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_consent_purposes PRIMARY KEY (code),
    CONSTRAINT chk_consent_purposes__legal_basis CHECK (legal_basis IN
        ('CONTRACT', 'CONSENT', 'LEGAL_OBLIGATION', 'LEGITIMATE_INTEREST'))
);
CREATE OR REPLACE TRIGGER trg_consent_purposes_updated_at
    BEFORE UPDATE ON public.consent_purposes FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

INSERT INTO public.consent_purposes (code, description, legal_basis, required) VALUES
    ('TERMS_OF_SERVICE',         'Aceptación de los términos y condiciones del servicio', 'CONTRACT', true),
    ('PERSONAL_DATA_PROCESSING', 'Autorización de tratamiento de datos personales (Ley 1581 de 2012)', 'CONSENT', true),
    ('ANALYTICS_TRACKING',       'Medición de uso y analítica de navegación', 'CONSENT', false),
    ('PERSONALIZATION',          'Personalización de resultados y recomendaciones', 'CONSENT', false),
    ('MARKETING_EMAIL',          'Comunicaciones comerciales por correo electrónico', 'CONSENT', false),
    ('MARKETING_WHATSAPP',       'Comunicaciones comerciales por WhatsApp', 'CONSENT', false),
    ('VEHICLE_DATA',             'Tratamiento de datos de vehículos (garaje, kilometraje)', 'CONSENT', false),
    ('ML_TRAINING',              'Uso de datos seudonimizados para entrenar modelos', 'CONSENT', false)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.person_consents (
    id             BIGINT GENERATED ALWAYS AS IDENTITY,
    person_id      UUID         NOT NULL,
    purpose_code   VARCHAR(40)  NOT NULL,
    granted        BOOLEAN      NOT NULL,
    policy_version VARCHAR(20)  NOT NULL,
    channel        VARCHAR(20)  NOT NULL,
    evidence       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    ip_address     INET,
    user_agent     VARCHAR(500),
    recorded_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_person_consents PRIMARY KEY (id),
    CONSTRAINT fk_person_consents__person_id FOREIGN KEY (person_id)
        REFERENCES public.persons (id) ON DELETE RESTRICT,
    CONSTRAINT fk_person_consents__purpose_code FOREIGN KEY (purpose_code)
        REFERENCES public.consent_purposes (code) ON DELETE RESTRICT,
    CONSTRAINT chk_person_consents__channel CHECK (channel IN ('WEB', 'APP', 'EMAIL', 'SUPPORT', 'IMPORT')),
    CONSTRAINT chk_person_consents__evidence CHECK (jsonb_typeof(evidence) = 'object' AND pg_column_size(evidence) <= 2048)
);
CREATE INDEX IF NOT EXISTS idx_person_consents__person_purpose
    ON public.person_consents (person_id, purpose_code, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_person_consents__purpose
    ON public.person_consents (purpose_code);

-- Solo inserción. Excepción: ip_address/user_agent pueden anularse
-- (minimización tras el plazo legal que se apruebe).
CREATE OR REPLACE TRIGGER trg_person_consents_append_only
    BEFORE UPDATE OR DELETE ON public.person_consents
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete('ip_address:null', 'user_agent:null');
CREATE OR REPLACE TRIGGER trg_person_consents_append_only_truncate
    BEFORE TRUNCATE ON public.person_consents
    FOR EACH STATEMENT EXECUTE FUNCTION public.forbid_update_delete();

-- security_invoker: la vista aplica los permisos (y la RLS futura) de quien
-- consulta, no los del dueño. Evita exponer person_consents a través de la
-- vista (p. ej. vía la Data API de Supabase). PG 15+.
CREATE OR REPLACE VIEW public.person_consents_current WITH (security_invoker = true) AS
SELECT DISTINCT ON (person_id, purpose_code)
       person_id, purpose_code, granted, policy_version, recorded_at
FROM public.person_consents
ORDER BY person_id, purpose_code, recorded_at DESC, id DESC;

CREATE TABLE IF NOT EXISTS privacy.subject_pseudonyms (
    person_id   UUID        NOT NULL,
    subject_key UUID        NOT NULL DEFAULT gen_random_uuid(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_subject_pseudonyms PRIMARY KEY (person_id),
    CONSTRAINT uq_subject_pseudonyms__subject_key UNIQUE (subject_key),
    CONSTRAINT fk_subject_pseudonyms__person_id FOREIGN KEY (person_id)
        REFERENCES public.persons (id) ON DELETE CASCADE
);

COMMENT ON TABLE privacy.subject_pseudonyms IS
    'Seudónimo aleatorio por persona. Borrar la fila desvincula tracking/analytics/ml (supresión, Ley 1581).';
