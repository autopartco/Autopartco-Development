"""CLI: ``python -m autopartco_ml train <modelo|all>``."""
import argparse

from . import db
from .models import demand, payment_anomaly, stockout

MODELS = {demand.KEY: demand, stockout.KEY: stockout, payment_anomaly.KEY: payment_anomaly}


def main() -> None:
    parser = argparse.ArgumentParser(prog="autopartco_ml")
    sub = parser.add_subparsers(dest="command", required=True)
    train = sub.add_parser("train", help="entrena, evalúa y exporta artefactos a backend/src/main/resources/ml/")
    train.add_argument("model", choices=[*MODELS, "all"])
    args = parser.parse_args()
    keys = list(MODELS) if args.model == "all" else [args.model]
    with db.connect() as conn:
        for key in keys:
            print(f"== {key}")
            MODELS[key].train(conn)


if __name__ == "__main__":
    main()
