"""Conexión de solo lectura a la BD (credenciales del .env raíz) y ejecución de los SQL de variables compartidos."""
import os
import re
from pathlib import Path

import pandas as pd
import psycopg
from dotenv import load_dotenv

REPO = Path(__file__).resolve().parents[2]
RESOURCES = REPO / "backend" / "src" / "main" / "resources" / "ml"
TEST_RESOURCES = REPO / "backend" / "src" / "test" / "resources" / "ml"

_PARAM = re.compile(r"(?<![:\w]):([a-z_]+)\b")


def connect(read_only: bool = True) -> psycopg.Connection:
    load_dotenv(REPO / ".env")
    conn = psycopg.connect(
        host=os.environ["DB_HOST"], port=os.environ.get("DB_PORT", "5432"), dbname=os.environ["DB_NAME"],
        user=os.environ["DB_USERNAME"], password=os.environ["DB_PASSWORD"], sslmode="require",
        connect_timeout=20, prepare_threshold=None,
    )
    conn.read_only = read_only
    return conn


def features_sql(key: str) -> str:
    """SQL de variables del modelo con los parámetros :nombre convertidos al estilo de psycopg."""
    sql = (RESOURCES / key / "features.sql").read_text(encoding="utf-8")
    return _PARAM.sub(r"%(\1)s", sql)


def query(conn: psycopg.Connection, sql: str, params: dict | None = None) -> pd.DataFrame:
    with conn.cursor() as cur:
        cur.execute(sql, params or {})
        cols = [c.name for c in cur.description]
        return pd.DataFrame(cur.fetchall(), columns=cols)
