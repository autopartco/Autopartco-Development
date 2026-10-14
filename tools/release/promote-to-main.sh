#!/usr/bin/env bash
# Publica en main una copia de <ref> (por defecto origin/develop) con solo lo necesario para deploy.
# main nunca recibe merges: cada promocion es un commit cuyo arbol es el filtrado de <ref> y cuyo
# padre es el main anterior, asi que el historial de main es lineal y no hace falta force push.
#
# Uso: tools/release/promote-to-main.sh [ref]
#   DRY_RUN=1      muestra el arbol resultante y no publica.
#   RELEASE_LABEL  etiqueta del commit (en CI, el nombre del tag).
set -euo pipefail

SRC_REF="${1:-origin/develop}"
REMOTE="${REMOTE:-origin}"
TARGET_BRANCH="${TARGET_BRANCH:-main}"

# Lista blanca: lo unico que llega a main.
INCLUDE=(backend frontend database .gitignore README.md)
# Excepciones dentro de las rutas incluidas.
EXCLUDE=(backend/bin backend/src/test)

src_sha=$(git rev-parse --verify "$SRC_REF^{commit}")

# Indice temporal: no toca el indice ni el arbol de trabajo del usuario.
GIT_INDEX_FILE="$(mktemp -d)/index"
export GIT_INDEX_FILE
trap 'rm -rf "$(dirname "$GIT_INDEX_FILE")"' EXIT

git read-tree --empty
for path in "${INCLUDE[@]}"; do
  if git cat-file -e "$src_sha:$path" 2>/dev/null; then
    git ls-tree -r -z --full-tree "$src_sha" -- "$path" | git update-index -z --index-info
  fi
done
for path in "${EXCLUDE[@]}"; do
  git rm -r -q --cached --ignore-unmatch -- "$path" >/dev/null
done

tree=$(git write-tree)
parent=$(git rev-parse --verify -q "$REMOTE/$TARGET_BRANCH^{commit}" || true)

if [[ -n "$parent" && "$tree" == "$(git rev-parse "$parent^{tree}")" ]]; then
  echo "main ya coincide con ${src_sha:0:7}; no hay nada que promover."
  exit 0
fi

if [[ "${DRY_RUN:-0}" == "1" ]]; then
  git ls-tree --name-only "$tree"
  echo "DRY_RUN: arbol $tree listo; no se publico."
  exit 0
fi

label="${RELEASE_LABEL:-manual}"
commit=$(git commit-tree "$tree" ${parent:+-p "$parent"} \
  -m "release: $label desde develop@${src_sha:0:7}" \
  -m "Source-Commit: $src_sha")

git push "$REMOTE" "$commit:refs/heads/$TARGET_BRANCH"
echo "main -> ${commit:0:7} (develop@${src_sha:0:7})"
