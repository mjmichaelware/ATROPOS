#!/usr/bin/env python3
"""Optional, free-tier ingestion enrichment for SpecGraph Foundry.

Runs after extraction, reads a document's atoms through the real API, and:

  1. Embeds each atom's canonical_statement via Jina Embeddings (free tier:
     1M tokens/month, no credit card).
  2. Upserts those embeddings into a Pinecone index (free Starter plan: 2GB
     storage, no credit card) under the project's namespace, enabling a
     genuine "find similar atoms" semantic search - use --query to try it.
  3. Optionally cross-checks each atom's stored kind/modality tag against a
     free HuggingFace zero-shot classification model and PRINTS a report of
     any disagreements. It never rewrites the atom - this codebase has no
     update-atom endpoint at all, deliberately: atoms are write-once,
     extracted-from-source records. This step is advisory only, the same
     way research evidence supports a conclusion without ever mutating
     source authority.

Nothing here touches specgraph-foundry's own database or backend code -
it is a standalone client of the public API plus two external free
services, so it carries zero migration or extraction-path risk.

Auth: same as research_autopilot.py - give it your email + password and it
signs in itself (no bearer token to find or paste), or pass a pre-obtained
--token / SPECGRAPH_BEARER_TOKEN directly.

Setup (run once per project, registers Jina/Pinecone/HuggingFace in the
project's Routing system so they show up in the app's Routing tab, next to
the research providers registered by research_autopilot.py --setup):
    python scripts/ingestion_enrichment.py --setup

Usage:
    export SPECGRAPH_API_BASE=https://your-api-host
    export SUPABASE_URL=https://your-project.supabase.co
    export SUPABASE_ANON_KEY=...
    export SPECGRAPH_EMAIL=you@example.com
    export SPECGRAPH_PASSWORD=...
    export SPECGRAPH_PROJECT_ID=...             # only needed for --setup
    export JINA_API_KEY=...
    export PINECONE_API_KEY=...
    export PINECONE_INDEX_HOST=...              # from the Pinecone console, after creating a free index
    export HUGGINGFACE_API_KEY=...              # optional, enables the tag consistency report

    python scripts/ingestion_enrichment.py --setup                       # once, registers providers in Routing
    python scripts/ingestion_enrichment.py --document-id <id>            # embed + upsert + advisory report
    python scripts/ingestion_enrichment.py --query "must authenticate"   # semantic search over what's indexed
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


JINA_EMBEDDINGS_URL = "https://api.jina.ai/v1/embeddings"
JINA_MODEL = "jina-embeddings-v3"
HUGGINGFACE_ZERO_SHOT_MODEL = "facebook/bart-large-mnli"
KIND_LABELS = ["FUNCTIONAL", "UX", "SECURITY", "PERFORMANCE", "OBSERVABILITY", "OTHER"]

# Territory used for visibility only - unlike research_autopilot.py's
# providers, these three aren't interchangeable alternatives routed by
# precedence; each has a fixed, distinct job (embed / store / tag-check).
# Registering them still makes them real rows in provider_configs, visible
# in the app's Routing tab, which is the whole point: so "is this hooked up"
# is answered by looking at the UI instead of an env var no one can see.
INGESTION_TERRITORY = "INGESTION_ENRICHMENT"

INGESTION_PROVIDER_REGISTRY = [
    {"name": "jina-embeddings", "backend": "jina"},
    {"name": "pinecone-vectorstore", "backend": "pinecone"},
    {"name": "huggingface-tagcheck", "backend": "huggingface"},
]


class ApiError(RuntimeError):
    def __init__(self, status: int, body: str) -> None:
        super().__init__(f"HTTP {status}: {body[:500]}")
        self.status = status
        self.body = body


def _http_json(url: str, *, method: str = "GET", headers: dict[str, str] | None = None, body: dict[str, object] | None = None, timeout: float = 60.0) -> tuple[dict[str, object], dict[str, str]]:
    data = json.dumps(body).encode("utf-8") if body is not None else None
    request_headers = dict(headers or {})
    if data is not None:
        request_headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, headers=request_headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
            payload = json.loads(raw) if raw else {}
            return payload, {key.lower(): value for key, value in response.headers.items()}
    except urllib.error.HTTPError as error:
        raise ApiError(error.code, error.read().decode("utf-8", errors="replace")) from error


def get_access_token(args: argparse.Namespace) -> str:
    if args.token:
        return str(args.token)
    request = urllib.request.Request(
        f"{args.supabase_url}/auth/v1/token?grant_type=password",
        data=json.dumps({"email": args.email, "password": args.password}).encode("utf-8"),
        headers={"Content-Type": "application/json", "apikey": args.supabase_anon_key or ""},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=30.0) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raise ApiError(error.code, error.read().decode("utf-8", errors="replace")) from error
    return str(payload["access_token"])


def run_setup(args: argparse.Namespace, token: str) -> int:
    api_base = args.api_base.rstrip("/")
    for entry in INGESTION_PROVIDER_REGISTRY:
        _http_json(
            f"{api_base}/v1/projects/{args.project_id}/providers",
            method="POST",
            headers={
                "Authorization": f"Bearer {token}",
                "Accept": "application/json",
                "Idempotency-Key": str(uuid.uuid4()),
            },
            body={
                "name": entry["name"],
                "provider_class": "FREE_READY_PROVIDER",
                "cost_class": "FREE",
                "territories": [INGESTION_TERRITORY],
                "priority": 0,
                "metadata": {"backend": entry["backend"]},
                "enabled": True,
            },
        )
        print(f"[setup] registered provider: {entry['name']} (FREE_READY_PROVIDER)")
    print("[setup] done - open the project's Routing tab to see these providers.")
    return 0


def fetch_atoms(api_base: str, token: str, document_id: str) -> list[dict[str, object]]:
    atoms: list[dict[str, object]] = []
    cursor: str | None = None
    while True:
        url = f"{api_base}/v1/documents/{document_id}/atoms"
        if cursor:
            url += f"?cursor={urllib.parse.quote(cursor)}"
        payload, headers = _http_json(url, headers={"Authorization": f"Bearer {token}", "Accept": "application/json"})
        atoms.extend(item for item in payload.get("items", []) if isinstance(item, dict))
        if headers.get("x-has-more", "").lower() != "true":
            break
        cursor = headers.get("x-next-cursor")
        if not cursor:
            break
    return atoms


def embed_with_jina(texts: list[str], api_key: str) -> list[list[float]]:
    if not texts:
        return []
    payload, _ = _http_json(
        JINA_EMBEDDINGS_URL,
        method="POST",
        headers={"Authorization": f"Bearer {api_key}"},
        body={"model": JINA_MODEL, "input": texts},
    )
    data = sorted(payload["data"], key=lambda item: item["index"])
    return [item["embedding"] for item in data]


def upsert_to_pinecone(vectors: list[tuple[str, list[float]]], *, index_host: str, api_key: str, namespace: str) -> None:
    if not vectors:
        return
    _http_json(
        f"https://{index_host}/vectors/upsert",
        method="POST",
        headers={"Api-Key": api_key},
        body={
            "namespace": namespace,
            "vectors": [{"id": atom_id, "values": embedding} for atom_id, embedding in vectors],
        },
    )


def query_pinecone(embedding: list[float], *, index_host: str, api_key: str, namespace: str, top_k: int = 5) -> list[dict[str, object]]:
    payload, _ = _http_json(
        f"https://{index_host}/query",
        method="POST",
        headers={"Api-Key": api_key},
        body={"namespace": namespace, "vector": embedding, "topK": top_k, "includeMetadata": False},
    )
    matches = payload.get("matches", [])
    return [match for match in matches if isinstance(match, dict)]


def check_tag_with_huggingface(text: str, stated_kind: str, api_key: str) -> str | None:
    """Returns the model's best-guess label if it disagrees with stated_kind, else None."""
    try:
        payload, _ = _http_json(
            f"https://api-inference.huggingface.co/models/{HUGGINGFACE_ZERO_SHOT_MODEL}",
            method="POST",
            headers={"Authorization": f"Bearer {api_key}"},
            body={"inputs": text, "parameters": {"candidate_labels": KIND_LABELS}},
            timeout=30.0,
        )
    except (ApiError, urllib.error.URLError, TimeoutError):
        return None
    labels = payload.get("labels")
    if not isinstance(labels, list) or not labels:
        return None
    best = str(labels[0]).upper()
    return best if best != stated_kind.upper() else None


def run_enrich(args: argparse.Namespace, token: str) -> int:
    api_base = args.api_base.rstrip("/")
    atoms = fetch_atoms(api_base, token, args.document_id)
    if not atoms:
        print("[enrich] no atoms found for this document")
        return 0

    texts = [str(atom.get("canonical_statement") or "") for atom in atoms]
    embeddings = embed_with_jina(texts, args.jina_api_key)
    print(f"[enrich] embedded {len(embeddings)} atom(s) via Jina")

    if args.pinecone_api_key and args.pinecone_index_host:
        if len(embeddings) != len(atoms):
            raise ValueError(
                f"embedding count ({len(embeddings)}) does not match atom count ({len(atoms)}) - "
                "refusing to upsert, this would silently misalign atom IDs with the wrong vectors"
            )
        vectors = [(str(atom["id"]), embedding) for atom, embedding in zip(atoms, embeddings)]
        upsert_to_pinecone(vectors, index_host=args.pinecone_index_host, api_key=args.pinecone_api_key, namespace=args.document_id)
        print(f"[enrich] upserted {len(vectors)} vector(s) into Pinecone namespace={args.document_id}")
    else:
        print("[enrich] Pinecone not configured, skipping semantic index")

    if args.huggingface_api_key:
        disagreements = 0
        for index, atom in enumerate(atoms):
            stated_kind = str(atom.get("kind") or "")
            text = str(atom.get("canonical_statement") or "")
            if not stated_kind or not text:
                continue
            suggestion = check_tag_with_huggingface(text, stated_kind, args.huggingface_api_key)
            if suggestion:
                disagreements += 1
                print(f"[advisory] atom {atom['id']}: stored kind={stated_kind!r}, model suggests {suggestion!r} (not applied - atoms are write-once)")
            if index < len(atoms) - 1:
                # HuggingFace's free tier is a soft, unpublished "few hundred
                # requests/hour" limit - a small pause between calls is
                # cheaper and more robust than firing hundreds of atoms at
                # once and hoping the response codes tell us to back off.
                time.sleep(0.2)
        print(f"[enrich] tag consistency check complete, {disagreements} disagreement(s) noted (advisory only)")
    else:
        print("[enrich] HuggingFace not configured, skipping tag consistency check")

    return 0


def run_query(args: argparse.Namespace) -> int:
    if not (args.pinecone_api_key and args.pinecone_index_host):
        print("[query] Pinecone must be configured to search", file=sys.stderr)
        return 1
    # Vectors are upserted under the document_id namespace (see run_enrich), so
    # a --query following --document-id must search that same namespace by
    # default, or it silently searches the empty namespace and finds nothing.
    namespace = args.namespace or args.document_id or ""
    [embedding] = embed_with_jina([args.query], args.jina_api_key)
    matches = query_pinecone(embedding, index_host=args.pinecone_index_host, api_key=args.pinecone_api_key, namespace=namespace, top_k=args.top_k)
    for match in matches:
        print(f"{match.get('score', 0):.3f}  atom={match.get('id')}")
    return 0


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--api-base", default=os.environ.get("SPECGRAPH_API_BASE"))
    parser.add_argument("--project-id", default=os.environ.get("SPECGRAPH_PROJECT_ID"), help="Only needed for --setup")
    parser.add_argument("--token", default=os.environ.get("SPECGRAPH_BEARER_TOKEN"))
    parser.add_argument("--supabase-url", default=os.environ.get("SUPABASE_URL"))
    parser.add_argument("--supabase-anon-key", default=os.environ.get("SUPABASE_ANON_KEY"))
    parser.add_argument("--email", default=os.environ.get("SPECGRAPH_EMAIL"))
    parser.add_argument("--password", default=os.environ.get("SPECGRAPH_PASSWORD"))
    parser.add_argument("--setup", action="store_true", help="Register jina-embeddings/pinecone-vectorstore/huggingface-tagcheck in the project's Routing system, then exit")
    parser.add_argument("--document-id", default=None)
    parser.add_argument("--query", default=None, help="Semantic-search Pinecone instead of enriching a document")
    parser.add_argument("--namespace", default=None, help="Pinecone namespace to search (defaults to matching --document-id when enriching)")
    parser.add_argument("--top-k", type=int, default=5)
    parser.add_argument("--jina-api-key", default=os.environ.get("JINA_API_KEY"))
    parser.add_argument("--pinecone-api-key", default=os.environ.get("PINECONE_API_KEY"))
    parser.add_argument("--pinecone-index-host", default=os.environ.get("PINECONE_INDEX_HOST"))
    parser.add_argument("--huggingface-api-key", default=os.environ.get("HUGGINGFACE_API_KEY"))
    args = parser.parse_args(argv)

    if not args.api_base:
        parser.error("--api-base/SPECGRAPH_API_BASE is required")

    has_static_token = bool(args.token)
    has_password_auth = bool(args.supabase_url and args.supabase_anon_key and args.email and args.password)
    if not has_static_token and not has_password_auth:
        parser.error(
            "provide either --token / SPECGRAPH_BEARER_TOKEN, or all of "
            "--supabase-url/--supabase-anon-key/--email/--password "
            "(SUPABASE_URL/SUPABASE_ANON_KEY/SPECGRAPH_EMAIL/SPECGRAPH_PASSWORD) "
            "so this script can sign in and refresh its own token"
        )

    if args.setup:
        if not args.project_id:
            parser.error("--project-id/SPECGRAPH_PROJECT_ID is required for --setup")
        return args

    if not args.jina_api_key:
        parser.error("--jina-api-key/JINA_API_KEY is required (embeddings are the base of every step here)")
    if not args.query and not args.document_id:
        parser.error("either --setup, --document-id (to enrich), or --query (to search) is required")

    return args


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    if args.setup:
        return run_setup(args, get_access_token(args))
    if args.query:
        return run_query(args)
    return run_enrich(args, get_access_token(args))


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
