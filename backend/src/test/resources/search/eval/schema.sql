-- Esquema MINIMO solo para la evaluacion de busqueda (Testcontainers + postgres:17-alpine). No es una migracion:
-- reproduce de produccion lo que leen SearchIndexer, SearchDictionary, SearchSql y la publicacion de ListingQueries.
create extension if not exists pg_trgm;
create extension if not exists unaccent;

create function public.immutable_unaccent(text) returns text
    language sql immutable parallel safe strict
    as 'select public.unaccent(''public.unaccent'', $1)';

create text search configuration public.spanish_unaccent (copy = spanish);
alter text search configuration public.spanish_unaccent
    alter mapping for hword, hword_part, word with unaccent, spanish_stem;

create table states (id uuid primary key, code text not null, deleted_at timestamptz);
create table categories (id uuid primary key, code text not null, deleted_at timestamptz);
create table manufacturers (id uuid primary key, name text not null, approved boolean not null default true,
    deleted_at timestamptz);
create table make (id uuid primary key, name text not null, deleted_at timestamptz);
create table model (id uuid primary key, make_id uuid not null references make (id), name text not null,
    deleted_at timestamptz);
create table versions (id uuid primary key, model_id uuid not null references model (id), version text,
    year_from smallint, year_to smallint, deleted_at timestamptz);
create table geo_municipalities (code char(5) primary key, department_code char(2) not null, name text not null,
    is_department_capital boolean not null default false, population integer);
create table search_synonyms (term text primary key, expands_to text[] not null);

create table store (id uuid primary key, business_name text not null, state uuid not null references states (id),
    deleted_at timestamptz);
create table locations (id uuid primary key, municipality_code char(5), latitude numeric, longitude numeric);
create table branches (id uuid primary key, store_id uuid not null references store (id), name text not null,
    state uuid not null references states (id), location_id uuid references locations (id), deleted_at timestamptz);
create table products (id uuid primary key, store_id uuid not null references store (id), name text not null,
    sku text, part_number text, description text, manufacturer_id uuid references manufacturers (id),
    category_id uuid references categories (id), state_id uuid not null references states (id),
    is_universal boolean not null default false, attributes jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(), deleted_at timestamptz);
create table inventory (id uuid primary key, product_id uuid not null references products (id),
    branch_id uuid not null references branches (id), price numeric not null, stock integer not null,
    deleted_at timestamptz);
create table product_vehicle_rules (id uuid primary key, product_id uuid not null references products (id),
    make_id uuid references make (id), model_id uuid references model (id), version_id uuid references versions (id),
    deleted_at timestamptz);
create table store_reviews (id uuid primary key, store_id uuid not null references store (id),
    rating integer not null, deleted_at timestamptz, hidden_at timestamptz);
create table product_reviews (id uuid primary key, product_id uuid not null references products (id),
    rating integer not null, deleted_at timestamptz, hidden_at timestamptz);

create table search_listings (
    listing_key text primary key,
    product_id uuid not null,
    branch_id uuid not null,
    store_id uuid not null,
    category_id uuid,
    category_path uuid[] not null,
    manufacturer_id uuid,
    name text not null,
    price numeric not null,
    stock integer not null,
    has_promotion boolean not null,
    city_code char(5),
    department_code char(2),
    latitude double precision,
    longitude double precision,
    store_rating numeric,
    universal boolean not null,
    vehicle_make_ids uuid[] not null,
    vehicle_model_ids uuid[] not null,
    vehicle_version_ids uuid[] not null,
    year_from smallint,
    year_to smallint,
    document tsvector not null,
    search_text text not null,
    published_at timestamptz not null,
    updated_at timestamptz not null,
    product_rating numeric,
    product_review_count integer not null default 0,
    mentioned_make_ids uuid[] not null default '{}'::uuid[],
    mentioned_model_ids uuid[] not null default '{}'::uuid[]
);
create index ix_sl_mentioned_makes on search_listings using gin (mentioned_make_ids);
create index ix_sl_mentioned_models on search_listings using gin (mentioned_model_ids);
create table search_query_category (
    normalized_query text not null,
    category_id uuid not null references categories (id) on delete cascade,
    clicks int not null default 0 check (clicks >= 0),
    impressions int not null default 0 check (impressions >= 0),
    updated_at timestamptz not null default now(),
    primary key (normalized_query, category_id)
);
create index ix_sl_document on search_listings using gin (document);
create index ix_sl_search_text on search_listings using gin (search_text gin_trgm_ops);
create index ix_sl_category_path on search_listings using gin (category_path);
create index ix_sl_make_ids on search_listings using gin (vehicle_make_ids);
create index ix_sl_model_ids on search_listings using gin (vehicle_model_ids);
create index ix_sl_geo on search_listings (latitude, longitude) where latitude is not null;
create index ix_sl_price on search_listings (price);
create index ix_sl_product on search_listings (product_id);
create index ix_sl_store on search_listings (store_id);
create index ix_sl_branch on search_listings (branch_id);
create index ix_sl_city on search_listings (city_code) where city_code is not null;
create index ix_sl_manufacturer on search_listings (manufacturer_id) where manufacturer_id is not null;
