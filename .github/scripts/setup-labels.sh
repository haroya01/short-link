#!/usr/bin/env bash
set -euo pipefail

REPO="${REPO:-haroya01/short-link}"

echo "==> Deleting default labels"
DEFAULTS=(
  "bug"
  "documentation"
  "duplicate"
  "enhancement"
  "good first issue"
  "help wanted"
  "invalid"
  "question"
  "wontfix"
)
for name in "${DEFAULTS[@]}"; do
  gh label delete "$name" --repo "$REPO" --yes 2>/dev/null || true
done

echo "==> Creating labels"
# name|color|description
LABELS=(
  "type/feat|1f883d|새 기능"
  "type/fix|d1242f|버그 수정"
  "type/chore|6e7781|빌드/설정/잡일"
  "type/docs|0969da|문서"
  "type/refactor|8250df|동작 변경 없는 구조 개선"
  "type/test|bf8700|테스트 추가/수정"

  "P0|b60205|즉시 처리"
  "P1|e99695|이번 사이클"
  "P2|fbca04|여유 있을 때"

  "status/blocked|d93f0b|외부 의존으로 진행 불가"
  "status/in-progress|0e8a16|작업 중"
  "status/needs-review|a371f7|리뷰 대기"

  "area/backend|006b75|애플리케이션 코드"
  "area/infra|5319e7|배포/CI/도커"
  "area/db|c5def5|스키마/마이그레이션"
)

for entry in "${LABELS[@]}"; do
  IFS='|' read -r name color desc <<< "$entry"
  gh label create "$name" --color "$color" --description "$desc" --repo "$REPO" --force >/dev/null
  echo "  + $name"
done

echo "==> Done"
