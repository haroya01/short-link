#!/usr/bin/env python3
"""kurl-publish — 마크다운 파일(Obsidian vault 노트)을 kurl 블로그로 발행하는 CLI.

저장 원문이 곧 마크다운이라는 kurl 의 계약 위에 그대로 얹힌다: 본문은 PUT /posts/{id}/markdown
으로 변환 없이 들어가고, 서버가 블록으로 변환한다(웹·iOS 와 동일한 서버 소유 변환기).

사용:
    export KURL_API_KEY=kurl_xxxxx        # 설정 > API 키에서 발급
    python3 kurl-publish.py note.md               # 초안으로 올리기(생성 또는 갱신)
    python3 kurl-publish.py note.md --publish     # 올리고 바로 발행
    python3 kurl-publish.py note.md --dry-run     # 무엇을 할지 보여만 준다

frontmatter (전부 선택, 없는 키는 건드리지 않는다):
    ---
    title: 글 제목            # 없으면 첫 H1, 그것도 없으면 파일명
    slug: my-post             # 없으면 파일명을 케밥으로
    tags: [개발, 아키텍처]     # 또는 "개발, 아키텍처"
    excerpt: 한 줄 요약
    lang: ko
    kurl_post_id: 12          # 첫 성공 때 이 스크립트가 적어 넣는다 — 재실행 = 같은 글 갱신
    ---

주의: 본문 안의 로컬 이미지(![](./img.png))는 아직 업로드하지 않는다 — 발견하면 목록을 보여주고
중단한다(--ignore-local-images 로 무시 가능). 웹 주소 이미지는 그대로 발행된다.
"""

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

DEFAULT_BASE = "https://kurl.me"


def parse_frontmatter(text):
    """단순 키:값 frontmatter 파서 — 이 CLI 가 쓰는 평평한 키만 지원(중첩 YAML 아님)."""
    if not text.startswith("---\n"):
        return {}, text
    end = text.find("\n---", 4)
    if end < 0:
        return {}, text
    head, body = text[4:end], text[end + 4 :].lstrip("\n")
    meta = {}
    for line in head.splitlines():
        if ":" not in line:
            continue
        key, _, value = line.partition(":")
        meta[key.strip()] = value.strip().strip("\"'")
    return meta, body


def serialize_frontmatter(meta, body):
    lines = ["---"]
    for key, value in meta.items():
        lines.append(f"{key}: {value}")
    lines.append("---")
    return "\n".join(lines) + "\n\n" + body


def parse_tags(raw):
    if not raw:
        return None
    raw = raw.strip()
    if raw.startswith("[") and raw.endswith("]"):
        raw = raw[1:-1]
    tags = [t.strip().strip("\"'").lstrip("#") for t in raw.split(",")]
    return [t for t in tags if t]


def kebab(name):
    slug = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
    return slug[:200] if len(slug) >= 2 else f"post-{slug}"


def first_h1(body):
    m = re.search(r"^#\s+(.+)$", body, re.MULTILINE)
    return m.group(1).strip() if m else None


def local_images(body):
    """웹 주소가 아닌 이미지 경로 — 아직 업로드 미지원이라 사전에 알려준다."""
    paths = re.findall(r"!\[[^\]]*\]\(\s*([^()\s]+?)\s*(?:\"[^\"]*\")?\)", body)
    return [p for p in paths if not re.match(r"^https?://", p)]


def request(base, api_key, method, path, payload=None):
    req = urllib.request.Request(
        base + path,
        data=json.dumps(payload).encode() if payload is not None else None,
        method=method,
        headers={"X-API-Key": api_key, "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as res:
            raw = res.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors="replace")[:300]
        sys.exit(f"오류: {method} {path} -> {e.code}\n{detail}")


def main():
    ap = argparse.ArgumentParser(description="마크다운 파일을 kurl 블로그로 발행")
    ap.add_argument("file", help="마크다운 파일 경로")
    ap.add_argument("--publish", action="store_true", help="올린 뒤 바로 발행")
    ap.add_argument("--base", default=os.environ.get("KURL_BASE", DEFAULT_BASE))
    ap.add_argument("--dry-run", action="store_true", help="계획만 출력, 아무것도 안 보냄")
    ap.add_argument("--ignore-local-images", action="store_true")
    args = ap.parse_args()

    api_key = os.environ.get("KURL_API_KEY", "")
    if not api_key and not args.dry_run:
        sys.exit("KURL_API_KEY 환경변수가 필요합니다 (설정 > API 키에서 발급).")

    path = Path(args.file)
    text = path.read_text(encoding="utf-8")
    meta, body = parse_frontmatter(text)

    title = meta.get("title") or first_h1(body) or path.stem
    slug = meta.get("slug") or kebab(path.stem)
    tags = parse_tags(meta.get("tags"))
    excerpt = meta.get("excerpt")
    lang = meta.get("lang")
    post_id = meta.get("kurl_post_id")

    locals_found = local_images(body)
    if locals_found and not args.ignore_local_images:
        listing = "\n".join(f"  {p}" for p in locals_found)
        sys.exit(
            "로컬 이미지는 아직 업로드하지 않습니다 — 웹 주소로 바꾸거나 "
            f"--ignore-local-images 로 무시하세요:\n{listing}"
        )

    plan = "갱신" if post_id else "생성"
    print(f"[{plan}] {title!r} (slug={slug}, tags={tags or '변경 없음'}, {len(body)}자)")
    if args.dry_run:
        print("드라이런 — 여기까지.")
        return

    if not post_id:
        created = request(args.base, api_key, "POST", "/api/v1/posts", {"slug": slug, "title": title})
        post_id = created["id"]
        print(f"초안 생성: id={post_id}")

    patch = {"title": title}
    if tags is not None:
        patch["tags"] = tags
    if excerpt:
        patch["excerpt"] = excerpt
    if lang:
        patch["languageTag"] = lang
    request(args.base, api_key, "PATCH", f"/api/v1/posts/{post_id}", patch)
    request(args.base, api_key, "PUT", f"/api/v1/posts/{post_id}/markdown", {"markdown": body})
    print("본문 업로드 완료 (서버 변환 왕복 OK)")

    post = request(args.base, api_key, "GET", f"/api/v1/posts/{post_id}")
    if args.publish and post.get("status") in ("DRAFT",):
        post = request(args.base, api_key, "POST", f"/api/v1/posts/{post_id}/publish")
        print("발행 완료")
    elif args.publish:
        print(f"이미 {post.get('status')} 상태 — 발행 호출 생략")

    me = request(args.base, api_key, "GET", "/api/v1/users/me")
    username = me.get("username", "")
    final_slug = post.get("slug", slug)
    if post.get("status") == "PUBLISHED":
        print(f"공개 주소: https://blog.kurl.me/@{username}/{final_slug}")
        print(
            "마크다운 원문: "
            f"{args.base}/api/v1/public/profiles/{username}/posts/{final_slug}/markdown"
        )
    else:
        print(f"초안 상태로 저장됨 — 앱/웹 글쓰기 목록에서 확인: id={post_id}")

    # 재실행 = 같은 글 갱신이 되도록 post id 를 frontmatter 에 적어 둔다.
    if meta.get("kurl_post_id") != str(post_id):
        meta["kurl_post_id"] = str(post_id)
        path.write_text(serialize_frontmatter(meta, body), encoding="utf-8")
        print("frontmatter 에 kurl_post_id 기록 — 다음 실행부터 갱신 모드")


if __name__ == "__main__":
    main()
