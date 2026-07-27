#!/usr/bin/env python3
"""Automated, free-only research worker for SpecGraph Foundry.

Walks a project's research task queue (the same claim -> add evidence ->
complete workflow the frontend drives) and resolves each open atom/dimension
gap using one of several free LLM providers, routed through this app's
*existing* Routing system (src/specgraph_foundry/routing.py) instead of a
one-off provider list - so provider health, priority, and cooldown state are
real rows in `provider_configs` and are visible in the app's Routing tab
while this runs.

Only genuinely free, no-credit-card providers are used (verified against
current published limits, not guessed):
  - ollama      local, zero cost, zero rate limit - tried first
  - sambanova   free tier, ~30 RPM on 8B models, no documented daily cap
                below that - primary cloud lane
  - gemini      free tier, ~15 RPM / 1,500 requests/day on Flash models
  - groq        free tier, ~30 RPM but a hard 14,400 requests/day cap on
                llama-3.1-8b-instant - second lane / overflow
  - cerebras    free tier, ~5-30 RPM / 1M tokens/day depending on model
  - openrouter  free tier, only on models explicitly tagged ":free" -
                ~20 RPM, 50-1,000 requests/day depending on account age
  - mistral     free "Experiment" tier, ~1B tokens/month, no card
  - nvidia      free with NVIDIA Developer Program signup, ~40 RPM
  - github      free with any GitHub account (models: read PAT scope),
                low but real limits (10-15 RPM / 50-150 requests/day)
  - cloudflare  free tier, 10,000 "Neurons"/day (~1,300 responses),
                resets daily, no card - needs both an API token and
                your Cloudflare account ID
  - huggingface free tier via the Inference Providers router, 100K
                monthly credits, no card - rate limits are informal and
                enforced inconsistently per HF's own docs, so expect it
                to be flakier than the others
  - siliconflow free tier, 30 RPM / 60K TPM, no card - Qwen3-8B and a
                couple of other small models are free-forever with no
                usage cap on top of that

Not included: Ollama Cloud (the *hosted* service, distinct from local
ollama above) - it does have a genuine $0 free tier, but its direct REST
API endpoint isn't clearly documented anywhere verifiable from here, so
it's left out rather than wiring in a guessed URL. Check
https://docs.ollama.com/cloud (or your Ollama account settings) for the
real base URL/auth shape if you want it added.

No paid provider is included or called by this script, ever. OpenAI,
Anthropic, xAI, Together AI, DeepSeek, and FAL were all deliberately left
out even when a session's key inventory included them: none has a real,
no-card free tier for API access - adding any of them would risk a real
bill for a script whose entire premise is "free only," so they're
excluded rather than silently wired in. Cohere is free but its trial key
explicitly prohibits production/commercial use - also excluded, since
this script is not a demo. If Gemini is one you're pointing at a key
with both a free and a paid variant, use the one you know is free - this
script has no way to tell those apart from the key alone.

Each registered provider has its own independent rate limit, so running
several tasks at once (--concurrency N) lets the script use more of the
combined free-tier headroom across providers instead of being bottlenecked
by any single one's per-minute cap.

The model's output is recorded as the task's automated CONCLUSION, not as
"evidence" - this app's domain model requires evidence to cite something
real (a source_uri/source_title/excerpt), and an LLM reasoning about an
atom in isolation is not that. The evidence record this script attaches
is explicitly labeled as an automated note, not a disguised citation.

Auth: give it your email + password (the same ones you sign into the app
with) and it fetches and refreshes a real Supabase access token itself -
you never need to find or paste a bearer token. A pre-obtained token also
works via --token / SPECGRAPH_BEARER_TOKEN if you already have one.

Setup (run once per project, registers every provider above that you've
supplied a key for in the Routing system so it shows up in the app's
Routing tab):
    python scripts/research_autopilot.py --setup

Usage:
    export SPECGRAPH_API_BASE=https://your-api-host
    export SUPABASE_URL=https://your-project.supabase.co
    export SUPABASE_ANON_KEY=...
    export SPECGRAPH_EMAIL=you@example.com
    export SPECGRAPH_PASSWORD=...
    export SPECGRAPH_PROJECT_ID=...
    export SAMBANOVA_API_KEY=...       # at least one cloud provider key
    export GROQ_API_KEY=...            # recommended alongside it
    export GEMINI_API_KEY=...          # or GOOGLE_API_KEY - another free lane

    python scripts/research_autopilot.py --setup                      # once
    python scripts/research_autopilot.py --limit 5                    # smoke test
    python scripts/research_autopilot.py --concurrency 4              # run to empty queue, 4 at a time

Resumable: every claim comes from the server's PENDING queue, so killing
and re-running this script picks up wherever the queue actually is - no
local checkpoint file needed.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import json
import os
import re
import socket
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass, field


DEFAULT_LEASE_SECONDS = 900
DEFAULT_OLLAMA_URL = "http://localhost:11434"
DEFAULT_OLLAMA_MODEL = "llama3.2:1b"
RESEARCH_TERRITORY = "RESEARCH_CLASSIFICATION"

# provider_class -> precedence rank, lower is tried first. Mirrors the
# canonical route law in routing.py (LOCAL_TOOLCHAIN -> FREE_READY_PROVIDER
# -> FREE_FALLBACK_PROVIDER); this script has no PAID_EMERGENCY entry and
# never will.
PROVIDER_CLASS_RANK = {
    "LOCAL_TOOLCHAIN": 0,
    "FREE_READY_PROVIDER": 1,
    "FREE_FALLBACK_PROVIDER": 2,
}

# Registered by --setup. "backend" (metadata) tells this script which
# call_* function actually talks to the provider; metadata is validated
# server-side to reject anything secret-shaped, so this is safe to store.
PROVIDER_REGISTRY = [
    {
        "name": "ollama-local",
        "provider_class": "LOCAL_TOOLCHAIN",
        "cost_class": "LOCAL",
        "priority": 0,
        "backend": "ollama",
    },
    {
        "name": "sambanova-8b",
        "provider_class": "FREE_READY_PROVIDER",
        "cost_class": "FREE",
        "priority": 0,
        "backend": "sambanova",
    },
    {
        "name": "gemini-flash",
        "provider_class": "FREE_READY_PROVIDER",
        "cost_class": "FREE",
        "priority": 1,
        "backend": "gemini",
    },
    {
        "name": "groq-8b",
        "provider_class": "FREE_FALLBACK_PROVIDER",
        "cost_class": "FREE",
        "priority": 0,
        "backend": "groq",
    },
    {
        "name": "cerebras-8b",
        "provider_class": "FREE_FALLBACK_PROVIDER",
        "cost_class": "FREE",
        "priority": 1,
        "backend": "cerebras",
    },
    {
        "name": "openrouter-free",
        "provider_class": "FREE_FALLBACK_PROVIDER",
        "cost_class": "FREE",
        "priority": 2,
        "backend": "openrouter",
    },
    {
        "name": "mistral-small",
        "provider_class": "FREE_READY_PROVIDER",
        "cost_class": "FREE",
        "priority": 2,
        "backend": "mistral",
    },
    {
        "name": "nvidia-nim",
        "provider_class": "FREE_FALLBACK_PROVIDER",
        "cost_class": "FREE",
        "priority": 3,
        "backend": "nvidia",
    },
    {
        "name": "github-models",
        "provider_class": "FREE_FALLBACK_PROVIDER",
        "cost_class": "FREE",
        "priority": 4,
        "backend": "github",
    },
    {
        "name": "cloudflare-workers-ai",
        "provider_class": "FREE_FALLBACK_PROVIDER",
        "cost_class": "FREE",
        "priority": 5,
        "backend": "cloudflare",
    },
    {
        "name": "huggingface-router",
        "provider_class": "FREE_FALLBACK_PROVIDER",
        "cost_class": "FREE",
        "priority": 6,
        "backend": "huggingface",
    },
    {
        "name": "siliconflow-free",
        "provider_class": "FREE_FALLBACK_PROVIDER",
        "cost_class": "FREE",
        "priority": 7,
        "backend": "siliconflow",
    },
]

KNOWN_BACKENDS = {
    "ollama", "sambanova", "gemini", "groq", "cerebras", "openrouter",
    "mistral", "nvidia", "github", "cloudflare", "huggingface", "siliconflow",
}


@dataclass
class Config:
    api_base: str
    project_id: str
    worker_id: str
    limit: int | None
    dry_run: bool
    setup: bool
    request_timeout: float
    # Auth: either a static token, or email/password for auto-refresh.
    token: str | None
    supabase_url: str | None
    supabase_anon_key: str | None
    email: str | None
    password: str | None
    # Provider backends.
    ollama_url: str
    ollama_model: str
    sambanova_api_key: str | None
    sambanova_model: str
    gemini_api_key: str | None
    gemini_model: str
    groq_api_key: str | None
    groq_model: str
    cerebras_api_key: str | None
    cerebras_model: str
    openrouter_api_key: str | None
    openrouter_model: str
    mistral_api_key: str | None
    mistral_model: str
    nvidia_api_key: str | None
    nvidia_model: str
    github_models_token: str | None
    github_models_model: str
    cloudflare_api_token: str | None
    cloudflare_account_id: str | None
    cloudflare_model: str
    huggingface_api_key: str | None
    huggingface_model: str
    siliconflow_api_key: str | None
    siliconflow_model: str
    # If set, skip the LOCAL_TOOLCHAIN > FREE_READY_PROVIDER > FREE_FALLBACK_PROVIDER
    # precedence entirely and only ever consider this one backend.
    only_backend: str | None
    # Number of tasks to work on concurrently. Each provider has its own
    # independent rate limit, so this lets the script use more of the
    # combined free-tier headroom instead of serializing everything behind
    # one in-flight request at a time.
    concurrency: int
    # Mutable token cache, populated lazily. Guarded by _token_lock since
    # concurrent workers can race to refresh it.
    _token_cache: dict[str, object] = field(default_factory=dict)
    _token_lock: threading.Lock = field(default_factory=threading.Lock)


def parse_args(argv: list[str]) -> Config:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--api-base", default=os.environ.get("SPECGRAPH_API_BASE"))
    parser.add_argument("--project-id", default=os.environ.get("SPECGRAPH_PROJECT_ID"))
    parser.add_argument("--token", default=os.environ.get("SPECGRAPH_BEARER_TOKEN"))
    parser.add_argument("--supabase-url", default=os.environ.get("SUPABASE_URL"))
    parser.add_argument("--supabase-anon-key", default=os.environ.get("SUPABASE_ANON_KEY"))
    parser.add_argument("--email", default=os.environ.get("SPECGRAPH_EMAIL"))
    parser.add_argument("--password", default=os.environ.get("SPECGRAPH_PASSWORD"))
    parser.add_argument("--ollama-url", default=os.environ.get("OLLAMA_BASE_URL", DEFAULT_OLLAMA_URL))
    parser.add_argument("--ollama-model", default=os.environ.get("OLLAMA_MODEL", DEFAULT_OLLAMA_MODEL))
    parser.add_argument("--sambanova-api-key", default=os.environ.get("SAMBANOVA_API_KEY"))
    parser.add_argument("--sambanova-model", default=os.environ.get("SAMBANOVA_MODEL", "Meta-Llama-3.1-8B-Instruct"))
    parser.add_argument("--gemini-api-key", default=os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY"))
    parser.add_argument("--gemini-model", default=os.environ.get("GEMINI_MODEL", "gemini-2.5-flash"))
    parser.add_argument("--groq-api-key", default=os.environ.get("GROQ_API_KEY"))
    parser.add_argument("--groq-model", default=os.environ.get("GROQ_MODEL", "llama-3.1-8b-instant"))
    parser.add_argument("--cerebras-api-key", default=os.environ.get("CEREBRAS_API_KEY"))
    parser.add_argument("--cerebras-model", default=os.environ.get("CEREBRAS_MODEL", "llama3.1-8b"))
    parser.add_argument("--openrouter-api-key", default=os.environ.get("OPENROUTER_API_KEY"))
    # Pinning a specific ":free" model here has failed twice live within
    # hours of being verified - OpenRouter's free-model roster genuinely
    # shifts that fast as providers add/pull/reprice models. "openrouter/free"
    # is OpenRouter's own documented Free Models Router: it auto-selects
    # whatever's actually free at request time instead of a static ID that
    # can go stale between one run and the next.
    parser.add_argument("--openrouter-model", default=os.environ.get("OPENROUTER_MODEL", "openrouter/free"))
    parser.add_argument("--mistral-api-key", default=os.environ.get("MISTRAL_API_KEY"))
    parser.add_argument("--mistral-model", default=os.environ.get("MISTRAL_MODEL", "mistral-small-latest"))
    parser.add_argument("--nvidia-api-key", default=os.environ.get("NVIDIA_API_KEY"))
    parser.add_argument("--nvidia-model", default=os.environ.get("NVIDIA_MODEL", "meta/llama-3.1-8b-instruct"))
    parser.add_argument("--github-models-token", default=os.environ.get("GITHUB_MODELS_TOKEN") or os.environ.get("GH_MODELS_TOKEN"))
    # openai/gpt-4o-mini shows up in the catalog listing for every token
    # (GET /catalog/models lists metadata, not entitlement) but actually
    # calling it 403s "No access" for accounts without an extra OpenAI-model
    # enrollment - confirmed live. Meta's models on GitHub Models don't sit
    # behind that gate, so meta/meta-llama-3.1-8b-instruct is a safer
    # default; override with --github-models-model if you have OpenAI-model
    # access and want to use it.
    parser.add_argument("--github-models-model", default=os.environ.get("GITHUB_MODELS_MODEL", "meta/meta-llama-3.1-8b-instruct"))
    parser.add_argument("--cloudflare-api-token", default=os.environ.get("CLOUDFLARE_API_TOKEN"))
    parser.add_argument("--cloudflare-account-id", default=os.environ.get("CLOUDFLARE_ACCOUNT_ID"))
    parser.add_argument("--cloudflare-model", default=os.environ.get("CLOUDFLARE_MODEL", "@cf/meta/llama-3.1-8b-instruct"))
    parser.add_argument("--huggingface-api-key", default=os.environ.get("HUGGINGFACE_API_KEY") or os.environ.get("HF_TOKEN"))
    parser.add_argument("--huggingface-model", default=os.environ.get("HUGGINGFACE_MODEL", "meta-llama/Llama-3.1-8B-Instruct"))
    parser.add_argument("--siliconflow-api-key", default=os.environ.get("SILICONFLOW_API_KEY"))
    parser.add_argument("--siliconflow-model", default=os.environ.get("SILICONFLOW_MODEL", "Qwen/Qwen3-8B"))
    parser.add_argument(
        "--only-backend",
        default=os.environ.get("RESEARCH_ONLY_BACKEND"),
        choices=sorted(KNOWN_BACKENDS),
        help="Skip routing precedence and only ever use this one backend (e.g. groq), ignoring the other registered providers entirely",
    )
    parser.add_argument(
        "--concurrency",
        type=int,
        default=int(os.environ.get("RESEARCH_CONCURRENCY", "1")),
        help="Number of tasks to work on at once (default: 1, sequential). Each provider has its own rate limit, so this helps once more than one is registered.",
    )
    parser.add_argument("--worker-id", default=os.environ.get("RESEARCH_WORKER_ID", f"autopilot-{socket.gethostname()}-{os.getpid()}"))
    parser.add_argument("--limit", type=int, default=None, help="Stop after resolving this many tasks (default: run until the queue is empty)")
    parser.add_argument("--dry-run", action="store_true", help="Claim and print what would be submitted, without actually calling evidence/complete")
    parser.add_argument("--setup", action="store_true", help="Register every provider in PROVIDER_REGISTRY in the project's Routing system, then exit")
    parser.add_argument("--request-timeout", type=float, default=60.0)
    args = parser.parse_args(argv)

    if args.concurrency < 1:
        parser.error("--concurrency must be at least 1")

    missing = [
        name
        for name, value in (
            ("--api-base / SPECGRAPH_API_BASE", args.api_base),
            ("--project-id / SPECGRAPH_PROJECT_ID", args.project_id),
        )
        if not value
    ]
    if missing:
        parser.error(f"missing required: {', '.join(missing)}")

    has_static_token = bool(args.token)
    has_password_auth = bool(args.supabase_url and args.supabase_anon_key and args.email and args.password)
    if not has_static_token and not has_password_auth:
        parser.error(
            "provide either --token / SPECGRAPH_BEARER_TOKEN, or all of "
            "--supabase-url/--supabase-anon-key/--email/--password "
            "(SUPABASE_URL/SUPABASE_ANON_KEY/SPECGRAPH_EMAIL/SPECGRAPH_PASSWORD) "
            "so this script can sign in and refresh its own token"
        )

    # Cloud provider keys are optional - running local-only against Ollama
    # (with `ollama serve` running) is a fully supported mode. If no
    # provider ends up eligible at runtime, list_available_providers()
    # surfaces that clearly instead of failing here.

    if args.cloudflare_api_token and not args.cloudflare_account_id:
        print(
            "[warn] CLOUDFLARE_API_TOKEN is set but CLOUDFLARE_ACCOUNT_ID is not - "
            "every Cloudflare Workers AI request will fail with a generic 401 "
            "Authentication error rather than a clear 'missing account ID' message, "
            "since the account ID is part of the request URL itself. Find it on "
            "your Cloudflare dashboard's right sidebar and set CLOUDFLARE_ACCOUNT_ID.",
            file=sys.stderr,
        )

    for env_name, value in (
        ("SPECGRAPH_PASSWORD", args.password),
        ("SAMBANOVA_API_KEY", args.sambanova_api_key),
        ("GEMINI_API_KEY/GOOGLE_API_KEY", args.gemini_api_key),
        ("GROQ_API_KEY", args.groq_api_key),
        ("CEREBRAS_API_KEY", args.cerebras_api_key),
        ("OPENROUTER_API_KEY", args.openrouter_api_key),
        ("MISTRAL_API_KEY", args.mistral_api_key),
        ("NVIDIA_API_KEY", args.nvidia_api_key),
        ("GITHUB_MODELS_TOKEN", args.github_models_token),
        ("CLOUDFLARE_API_TOKEN", args.cloudflare_api_token),
        ("HUGGINGFACE_API_KEY/HF_TOKEN", args.huggingface_api_key),
        ("SILICONFLOW_API_KEY", args.siliconflow_api_key),
    ):
        # A non-ASCII character in a credential is almost always a phone
        # keyboard's autocorrect swapping a plain quote/apostrophe for a
        # "smart" one (' -> ’ etc). That crashes deep inside header
        # encoding with a cryptic UnicodeEncodeError - catch it here with a
        # clear message instead of waiting for that crash mid-run.
        if value and not value.isascii():
            print(
                f"[warn] {env_name} contains a non-ASCII character - this is almost always "
                "a smart quote or similar substitution from a phone keyboard's autocorrect. "
                "Re-check the value; requests using it as-is will likely fail.",
                file=sys.stderr,
            )

    return Config(
        api_base=args.api_base.rstrip("/"),
        project_id=args.project_id,
        worker_id=args.worker_id,
        limit=args.limit,
        dry_run=args.dry_run,
        setup=args.setup,
        request_timeout=args.request_timeout,
        token=args.token,
        supabase_url=(args.supabase_url.rstrip("/") if args.supabase_url else None),
        supabase_anon_key=args.supabase_anon_key,
        email=args.email,
        password=args.password,
        ollama_url=args.ollama_url.rstrip("/"),
        ollama_model=args.ollama_model,
        sambanova_api_key=args.sambanova_api_key,
        sambanova_model=args.sambanova_model,
        gemini_api_key=args.gemini_api_key,
        gemini_model=args.gemini_model,
        groq_api_key=args.groq_api_key,
        groq_model=args.groq_model,
        cerebras_api_key=args.cerebras_api_key,
        cerebras_model=args.cerebras_model,
        openrouter_api_key=args.openrouter_api_key,
        openrouter_model=args.openrouter_model,
        mistral_api_key=args.mistral_api_key,
        mistral_model=args.mistral_model,
        nvidia_api_key=args.nvidia_api_key,
        nvidia_model=args.nvidia_model,
        github_models_token=args.github_models_token,
        github_models_model=args.github_models_model,
        cloudflare_api_token=args.cloudflare_api_token,
        cloudflare_account_id=args.cloudflare_account_id,
        cloudflare_model=args.cloudflare_model,
        huggingface_api_key=args.huggingface_api_key,
        huggingface_model=args.huggingface_model,
        siliconflow_api_key=args.siliconflow_api_key,
        siliconflow_model=args.siliconflow_model,
        only_backend=args.only_backend,
        concurrency=args.concurrency,
    )


class ApiError(RuntimeError):
    def __init__(self, status: int, body: str) -> None:
        super().__init__(f"HTTP {status}: {body[:500]}")
        self.status = status
        self.body = body


# --------------------------------------------------------------------------
# Auth: fetch and transparently refresh a Supabase access token from email +
# password, so the caller never has to locate or paste a raw bearer token.
# --------------------------------------------------------------------------


def _supabase_token_request(config: Config, grant_type: str, body: dict[str, str]) -> dict[str, object]:
    url = f"{config.supabase_url}/auth/v1/token?grant_type={grant_type}"
    request = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "apikey": config.supabase_anon_key or "",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=config.request_timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8", errors="replace")
        raise ApiError(error.code, raw) from error


def get_access_token(config: Config) -> str:
    if config.token:
        return config.token

    cache = config._token_cache
    with config._token_lock:
        now = time.time()
        if cache.get("access_token") and float(cache.get("expires_at", 0)) - now > 60:
            return str(cache["access_token"])

        if cache.get("refresh_token"):
            try:
                payload = _supabase_token_request(
                    config, "refresh_token", {"refresh_token": str(cache["refresh_token"])}
                )
            except ApiError:
                payload = _supabase_token_request(
                    config, "password", {"email": config.email, "password": config.password}
                )
        else:
            payload = _supabase_token_request(
                config, "password", {"email": config.email, "password": config.password}
            )

        cache["access_token"] = payload["access_token"]
        cache["refresh_token"] = payload.get("refresh_token")
        cache["expires_at"] = now + float(payload.get("expires_in", 3600))
        return str(cache["access_token"])


# --------------------------------------------------------------------------
# SpecGraph API client
# --------------------------------------------------------------------------


def api_request(
    config: Config,
    method: str,
    path: str,
    *,
    body: dict[str, object] | None = None,
    idempotency_key: str | None = None,
) -> tuple[dict[str, object], dict[str, str]]:
    url = f"{config.api_base}{path}"
    headers = {
        "Authorization": f"Bearer {get_access_token(config)}",
        "Accept": "application/json",
    }
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode("utf-8")
    if idempotency_key:
        headers["Idempotency-Key"] = idempotency_key

    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=config.request_timeout) as response:
            raw = response.read().decode("utf-8")
            payload = json.loads(raw) if raw else {}
            return payload, {key.lower(): value for key, value in response.headers.items()}
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8", errors="replace")
        raise ApiError(error.code, raw) from error


def claim_next_task(config: Config) -> dict[str, object] | None:
    body, _ = api_request(
        config,
        "POST",
        f"/v1/projects/{config.project_id}/research-tasks/claim",
        body={"worker_id": config.worker_id, "lease_seconds": DEFAULT_LEASE_SECONDS},
        idempotency_key=str(uuid.uuid4()),
    )
    task = body.get("task")
    return task if isinstance(task, dict) else None


def submit_evidence(config: Config, task_id: str, excerpt: str, source_uri: str) -> str:
    body, _ = api_request(
        config,
        "POST",
        f"/v1/research-tasks/{task_id}/evidence",
        body={
            "worker_id": config.worker_id,
            "source_uri": source_uri,
            "source_title": "Automated dimension classification",
            "excerpt": excerpt[:4000],
            "publisher": "research_autopilot",
            "evidence_type": "OTHER",
            "reliability": 0.5,
        },
        idempotency_key=str(uuid.uuid4()),
    )
    return str(body["id"])


def complete_task(config: Config, task_id: str, conclusion: str, applicability: str, confidence: float, evidence_id: str) -> None:
    api_request(
        config,
        "POST",
        f"/v1/research-tasks/{task_id}/complete",
        body={
            "worker_id": config.worker_id,
            "conclusion": conclusion[:4000],
            "applicability": applicability,
            "confidence": confidence,
            "evidence_ids": [evidence_id],
        },
        idempotency_key=str(uuid.uuid4()),
    )


# --------------------------------------------------------------------------
# Routing system integration (src/specgraph_foundry/routing.py). We can't
# call RoutingService.route() directly - it's not exposed as an HTTP
# endpoint, only used internally - so this script replicates the same
# LOCAL_TOOLCHAIN > FREE_READY_PROVIDER > FREE_FALLBACK_PROVIDER precedence
# client-side using GET /providers, and reports outcomes back through the
# real POST /providers/{id}/health endpoint so status/cooldown and this
# script's decisions are visible in the app's Routing tab exactly as if a
# human had made them there.
# --------------------------------------------------------------------------


def setup_providers(config: Config) -> None:
    # A provider name that already exists is treated by the API as an edit
    # of that resource, which requires an If-Match header this script never
    # sends (it only ever creates) - re-running --setup after the registry
    # grows used to crash on the first already-registered entry, before ever
    # reaching any new ones after it. Skip names that already exist instead.
    existing_body, _ = api_request(config, "GET", f"/v1/projects/{config.project_id}/providers")
    existing_names = {
        str(item["name"])
        for item in existing_body.get("items", [])
        if isinstance(item, dict) and item.get("name") is not None
    }

    for entry in PROVIDER_REGISTRY:
        if entry["name"] in existing_names:
            print(f"[setup] already registered, skipping: {entry['name']}")
            continue
        api_request(
            config,
            "POST",
            f"/v1/projects/{config.project_id}/providers",
            body={
                "name": entry["name"],
                "provider_class": entry["provider_class"],
                "cost_class": entry["cost_class"],
                "territories": [RESEARCH_TERRITORY],
                "priority": entry["priority"],
                "metadata": {"backend": entry["backend"]},
                "enabled": True,
            },
            idempotency_key=str(uuid.uuid4()),
        )
        print(f"[setup] registered provider: {entry['name']} ({entry['provider_class']})")
    print("[setup] done - open the project's Routing tab to see these providers.")


def list_available_providers(config: Config) -> list[dict[str, object]]:
    body, _ = api_request(config, "GET", f"/v1/projects/{config.project_id}/providers")
    items = body.get("items", [])
    now = time.time()

    def is_cooling(provider: dict[str, object]) -> bool:
        cooldown_until = provider.get("cooldown_until")
        if not cooldown_until:
            return False
        try:
            import datetime

            parsed = datetime.datetime.fromisoformat(str(cooldown_until).replace("Z", "+00:00"))
            if parsed.tzinfo is None:
                parsed = parsed.replace(tzinfo=datetime.timezone.utc)
            return parsed.timestamp() > now
        except ValueError:
            return False

    eligible = [
        provider
        for provider in items
        if isinstance(provider, dict)
        and provider.get("enabled")
        and ({RESEARCH_TERRITORY, "*"} & set(provider.get("territories") or []))
        and str(provider.get("status", "UNKNOWN")).upper() not in {"DOWN"}
        and not is_cooling(provider)
        and str((provider.get("metadata") or {}).get("backend", "")) in KNOWN_BACKENDS
    ]
    if config.only_backend:
        eligible = [
            provider
            for provider in eligible
            if str((provider.get("metadata") or {}).get("backend", "")) == config.only_backend
        ]
    eligible.sort(key=lambda p: (PROVIDER_CLASS_RANK.get(str(p.get("provider_class")), 99), p.get("priority", 0)))
    return eligible


def report_health(config: Config, provider_id: str, *, success: bool, latency_ms: float, error_message: str = "", cooldown_seconds: int | None = None) -> None:
    body: dict[str, object] = {
        "status": "READY" if success else ("COOLDOWN" if cooldown_seconds else "DEGRADED"),
        "latency_ms": latency_ms,
    }
    if error_message:
        body["error_message"] = error_message[:500]
    if cooldown_seconds:
        body["cooldown_seconds"] = cooldown_seconds
    try:
        api_request(config, "POST", f"/v1/providers/{provider_id}/health", body=body, idempotency_key=str(uuid.uuid4()))
    except (ApiError, urllib.error.URLError, TimeoutError) as error:
        print(f"[warn] failed to report health for provider {provider_id}: {error}", file=sys.stderr)


# --------------------------------------------------------------------------
# Model backends - all free tiers, no paid provider is ever called here.
# --------------------------------------------------------------------------


PROMPT_TEMPLATE = """You are auditing one requirement (an "atom") extracted from a technical \
specification, against a single research dimension. Decide whether this dimension \
genuinely applies to this atom, and give a short reason.

Atom ({kind}/{modality}): {statement}

Dimension: {dimension}
Research question: {question}

Respond in EXACTLY this format, three lines, nothing else:
APPLICABLE: yes or no
CONFIDENCE: a number from 0.0 to 1.0
JUSTIFICATION: one or two sentences explaining the decision
"""

RESPONSE_PATTERN = re.compile(
    r"APPLICABLE:\s*(yes|no)\s*\n"
    r"CONFIDENCE:\s*([0-9]*\.?[0-9]+)\s*\n"
    r"JUSTIFICATION:\s*(.+)",
    re.IGNORECASE | re.DOTALL,
)


def build_prompt(task: dict[str, object]) -> str:
    return PROMPT_TEMPLATE.format(
        kind=task.get("kind") or "unknown",
        modality=task.get("modality") or "unknown",
        statement=task.get("canonical_statement") or "",
        dimension=task.get("dimension") or "",
        question=task.get("question") or "",
    )


def parse_model_output(raw: str) -> tuple[str, float, str]:
    match = RESPONSE_PATTERN.search(raw.strip())
    if not match:
        return "NOT_APPLICABLE", 0.3, f"Automated pass could not parse a structured response. Raw output: {raw[:300]!r}"
    applicable, confidence_str, justification = match.groups()
    applicability = "APPLICABLE" if applicable.strip().lower() == "yes" else "NOT_APPLICABLE"
    try:
        confidence = max(0.0, min(1.0, float(confidence_str)))
    except ValueError:
        confidence = 0.5
    return applicability, confidence, justification.strip()


def call_ollama(config: Config, prompt: str) -> str:
    request = urllib.request.Request(
        f"{config.ollama_url}/api/generate",
        data=json.dumps({"model": config.ollama_model, "prompt": prompt, "stream": False}).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=config.request_timeout) as response:
        payload = json.loads(response.read().decode("utf-8"))
    return str(payload.get("response", ""))


def _call_openai_compatible(url: str, api_key: str, model: str, prompt: str, timeout: float) -> str:
    request = urllib.request.Request(
        url,
        data=json.dumps(
            {
                "model": model,
                "messages": [{"role": "user", "content": prompt}],
                "temperature": 0.2,
            }
        ).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
            # Some providers' edge/WAF layers block urllib's default
            # "Python-urllib/x.y" User-Agent as bot traffic while allowing
            # curl's - a real key can otherwise get a bare 403 through this
            # client even though the exact same request succeeds via curl.
            "User-Agent": "specgraph-research-autopilot/1.0",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        payload = json.loads(response.read().decode("utf-8"))
    return str(payload["choices"][0]["message"]["content"])


def call_gemini(config: Config, prompt: str) -> str:
    # Auth via the x-goog-api-key header rather than Google's documented
    # ?key= query-string alternative, so the key never ends up embedded in
    # a URL that could get logged or echoed back in an error message.
    request = urllib.request.Request(
        f"https://generativelanguage.googleapis.com/v1beta/models/{config.gemini_model}:generateContent",
        data=json.dumps({"contents": [{"parts": [{"text": prompt}]}]}).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "x-goog-api-key": config.gemini_api_key or "",
            "User-Agent": "specgraph-research-autopilot/1.0",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=config.request_timeout) as response:
        payload = json.loads(response.read().decode("utf-8"))
    return str(payload["candidates"][0]["content"]["parts"][0]["text"])


def call_sambanova(config: Config, prompt: str) -> str:
    return _call_openai_compatible(
        "https://api.sambanova.ai/v1/chat/completions",
        config.sambanova_api_key or "",
        config.sambanova_model,
        prompt,
        config.request_timeout,
    )


def call_groq(config: Config, prompt: str) -> str:
    return _call_openai_compatible(
        "https://api.groq.com/openai/v1/chat/completions",
        config.groq_api_key or "",
        config.groq_model,
        prompt,
        config.request_timeout,
    )


def call_cerebras(config: Config, prompt: str) -> str:
    return _call_openai_compatible(
        "https://api.cerebras.ai/v1/chat/completions",
        config.cerebras_api_key or "",
        config.cerebras_model,
        prompt,
        config.request_timeout,
    )


def call_openrouter(config: Config, prompt: str) -> str:
    return _call_openai_compatible(
        "https://openrouter.ai/api/v1/chat/completions",
        config.openrouter_api_key or "",
        config.openrouter_model,
        prompt,
        config.request_timeout,
    )


def call_mistral(config: Config, prompt: str) -> str:
    return _call_openai_compatible(
        "https://api.mistral.ai/v1/chat/completions",
        config.mistral_api_key or "",
        config.mistral_model,
        prompt,
        config.request_timeout,
    )


def call_nvidia(config: Config, prompt: str) -> str:
    return _call_openai_compatible(
        "https://integrate.api.nvidia.com/v1/chat/completions",
        config.nvidia_api_key or "",
        config.nvidia_model,
        prompt,
        config.request_timeout,
    )


def call_github_models(config: Config, prompt: str) -> str:
    # GitHub Models needs one header beyond the shared OpenAI-compatible
    # shape (X-GitHub-Api-Version), so this doesn't reuse
    # _call_openai_compatible even though the body/response shape matches.
    request = urllib.request.Request(
        "https://models.github.ai/inference/chat/completions",
        data=json.dumps(
            {
                "model": config.github_models_model,
                "messages": [{"role": "user", "content": prompt}],
                "temperature": 0.2,
            }
        ).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {config.github_models_token or ''}",
            "X-GitHub-Api-Version": "2026-03-10",
            "User-Agent": "specgraph-research-autopilot/1.0",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=config.request_timeout) as response:
        payload = json.loads(response.read().decode("utf-8"))
    return str(payload["choices"][0]["message"]["content"])


def call_cloudflare(config: Config, prompt: str) -> str:
    return _call_openai_compatible(
        f"https://api.cloudflare.com/client/v4/accounts/{config.cloudflare_account_id or ''}/ai/v1/chat/completions",
        config.cloudflare_api_token or "",
        config.cloudflare_model,
        prompt,
        config.request_timeout,
    )


def call_huggingface(config: Config, prompt: str) -> str:
    return _call_openai_compatible(
        "https://router.huggingface.co/v1/chat/completions",
        config.huggingface_api_key or "",
        config.huggingface_model,
        prompt,
        config.request_timeout,
    )


def call_siliconflow(config: Config, prompt: str) -> str:
    return _call_openai_compatible(
        "https://api.siliconflow.com/v1/chat/completions",
        config.siliconflow_api_key or "",
        config.siliconflow_model,
        prompt,
        config.request_timeout,
    )


BACKEND_CALLERS = {
    "ollama": call_ollama,
    "sambanova": call_sambanova,
    "gemini": call_gemini,
    "groq": call_groq,
    "cerebras": call_cerebras,
    "openrouter": call_openrouter,
    "mistral": call_mistral,
    "nvidia": call_nvidia,
    "github": call_github_models,
    "cloudflare": call_cloudflare,
    "huggingface": call_huggingface,
    "siliconflow": call_siliconflow,
}


def call_backend(config: Config, backend: str, prompt: str) -> str:
    caller = BACKEND_CALLERS.get(backend)
    if caller is None:
        raise ValueError(f"unknown provider backend: {backend}")
    return caller(config, prompt)


# --------------------------------------------------------------------------
# Main loop
# --------------------------------------------------------------------------


def resolve_one_task(config: Config, task: dict[str, object], providers: list[dict[str, object]]) -> bool:
    """Try providers in routing precedence order; return True on success."""
    task_id = str(task["id"])
    dimension = str(task.get("dimension", "?"))
    prompt = build_prompt(task)

    for provider in providers:
        backend = str((provider.get("metadata") or {}).get("backend", ""))
        provider_id = str(provider["id"])
        started = time.monotonic()
        try:
            raw_output = call_backend(config, backend, prompt)
        except urllib.error.HTTPError as error:
            latency_ms = (time.monotonic() - started) * 1000
            try:
                # Bounded read: only the first 300 chars are ever used, and
                # an unbounded error.read() would fully buffer whatever the
                # server sends - a huge or slow/infinite error body could
                # spike memory or hang this worker thread on one bad response.
                detail = error.read(1024).decode("utf-8", errors="replace")[:300]
            except Exception:
                detail = ""
            message = f"{error}" + (f" - {detail}" if detail else "")
            if error.code == 429:
                retry_after = error.headers.get("Retry-After")
                cooldown = int(retry_after) if retry_after and retry_after.isdigit() else 60
                report_health(config, provider_id, success=False, latency_ms=latency_ms, error_message="rate limited (429)", cooldown_seconds=cooldown)
                print(f"[cooldown] {provider['name']} rate-limited, backing off {cooldown}s")
            else:
                report_health(config, provider_id, success=False, latency_ms=latency_ms, error_message=message, cooldown_seconds=30)
                print(f"[error] {provider['name']} failed for task {task_id} ({dimension}): {message}", file=sys.stderr)
            continue
        except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
            latency_ms = (time.monotonic() - started) * 1000
            report_health(config, provider_id, success=False, latency_ms=latency_ms, error_message=str(error), cooldown_seconds=30)
            print(f"[error] {provider['name']} unreachable for task {task_id} ({dimension}): {error}", file=sys.stderr)
            continue
        except (json.JSONDecodeError, KeyError, IndexError) as error:
            latency_ms = (time.monotonic() - started) * 1000
            report_health(config, provider_id, success=False, latency_ms=latency_ms, error_message=f"invalid response format: {error}", cooldown_seconds=30)
            print(f"[error] {provider['name']} returned an unparseable response for task {task_id} ({dimension}): {error}", file=sys.stderr)
            continue
        except UnicodeError as error:
            # A key/token containing a non-ASCII character (most often a
            # smart quote a phone keyboard substituted for a plain one)
            # crashes deep inside http.client's header encoding, before any
            # request is sent - catch it per-provider instead of letting it
            # kill the whole run, since one bad key shouldn't take down
            # every other worker thread with it.
            latency_ms = (time.monotonic() - started) * 1000
            report_health(config, provider_id, success=False, latency_ms=latency_ms, error_message=f"malformed request: {error}", cooldown_seconds=30)
            print(
                f"[error] {provider['name']} could not build a request for task {task_id} ({dimension}) - "
                f"its API key/token likely has a stray non-ASCII character (e.g. a curly quote "
                f"a phone keyboard substituted for a plain one): {error}",
                file=sys.stderr,
            )
            continue

        latency_ms = (time.monotonic() - started) * 1000
        report_health(config, provider_id, success=True, latency_ms=latency_ms)

        applicability, confidence, justification = parse_model_output(raw_output)
        source_uri = f"{backend}://{provider['name']}"

        if config.dry_run:
            print(f"[dry-run] task={task_id} dimension={dimension} via={provider['name']} -> {applicability} ({confidence:.2f}): {justification}")
            return True

        try:
            evidence_id = submit_evidence(config, task_id, justification, source_uri)
            complete_task(config, task_id, justification, applicability, confidence, evidence_id)
        except (ApiError, urllib.error.URLError, TimeoutError) as error:
            print(f"[error] failed to resolve task {task_id} ({dimension}): {error}", file=sys.stderr)
            return False

        print(f"[ok] task={task_id} dimension={dimension} via={provider['name']} -> {applicability} ({confidence:.2f})")
        return True

    print(f"[skip] task {task_id} ({dimension}): no eligible provider available right now", file=sys.stderr)
    return False


def run(config: Config) -> int:
    if config.setup:
        setup_providers(config)
        return 0

    # This API has no way to release a claimed task's lease early (no fail_task
    # HTTP endpoint) - a failed resolution just leaves the task CLAIMED until
    # its lease expires (DEFAULT_LEASE_SECONDS). If every provider is down
    # (network outage, all rate-limited, bad keys), looping straight back to
    # claim the next task would sweep the entire remaining queue into
    # unresolved 900s leases within seconds, and the queue would then look
    # "empty" (nothing PENDING) despite nothing being processed. Stop after a
    # few consecutive failures instead, with backoff in between. "processed"
    # and "consecutive_failures" are shared across every concurrent worker
    # (--concurrency), guarded by one lock, so the stop condition reflects
    # the whole run's health rather than any single worker's.
    max_consecutive_failures = 3
    state_lock = threading.Lock()
    stop_event = threading.Event()
    state = {"processed": 0, "consecutive_failures": 0, "exit_code": 0, "rr_index": 0}

    def worker() -> None:
        while not stop_event.is_set():
            # With concurrency > 1 several workers can pass this check in the
            # same instant and each resolve one more task before the others
            # notice - --limit can overshoot by up to (concurrency - 1) tasks.
            # Acceptable for what --limit is for (a quick smoke test), not
            # worth a distributed-reservation scheme to close that gap.
            with state_lock:
                if config.limit is not None and state["processed"] >= config.limit:
                    stop_event.set()
                    return

            try:
                providers = list_available_providers(config)
            except (ApiError, urllib.error.URLError, TimeoutError) as error:
                print(f"[error] could not list providers: {error}", file=sys.stderr)
                time.sleep(5)
                continue

            if not providers:
                with state_lock:
                    if not stop_event.is_set():
                        print("[error] no eligible providers registered - run --setup first, or check that at least one is enabled and not cooling down", file=sys.stderr)
                        state["exit_code"] = 1
                        stop_event.set()
                return

            try:
                task = claim_next_task(config)
            except (ApiError, urllib.error.URLError, TimeoutError) as error:
                print(f"[error] claim failed: {error}", file=sys.stderr)
                time.sleep(5)
                continue

            if task is None:
                with state_lock:
                    if not stop_event.is_set():
                        print("[done] queue is empty")
                        stop_event.set()
                return

            # Every worker fetches providers in the same routing-precedence
            # order, so without this they'd all try the same top provider
            # first and only spread out once it's failing/cooling down - one
            # provider absorbing most of the traffic while the others sit
            # idle, not the "all providers working in parallel" swarm this
            # is meant to be. Rotating the start point per task (round-robin,
            # shared across workers) spreads concurrent tasks across every
            # eligible provider instead. Priority order is preserved as the
            # fallback chain after the rotated pick - if a lower-priority
            # provider's turn comes up and it fails, resolve_one_task still
            # walks the rest of the (rotated) list rather than giving up.
            with state_lock:
                offset = state["rr_index"] % len(providers)
                state["rr_index"] += 1
            rotated_providers = providers[offset:] + providers[:offset]

            success = resolve_one_task(config, task, rotated_providers)
            sleep_seconds = 0.0
            with state_lock:
                if success:
                    state["processed"] += 1
                    state["consecutive_failures"] = 0
                else:
                    state["consecutive_failures"] += 1
                    if state["consecutive_failures"] >= max_consecutive_failures:
                        print(
                            f"[error] {state['consecutive_failures']} consecutive task failures - stopping instead of "
                            "sweeping the rest of the queue into unresolved leases. Check provider "
                            "connectivity/keys, then re-run once the failed tasks' leases expire "
                            f"(up to {DEFAULT_LEASE_SECONDS // 60} minutes).",
                            file=sys.stderr,
                        )
                        state["exit_code"] = 1
                        stop_event.set()
                        return
                    sleep_seconds = min(10 * state["consecutive_failures"], 60)
            if sleep_seconds:
                time.sleep(sleep_seconds)

    with concurrent.futures.ThreadPoolExecutor(max_workers=config.concurrency) as executor:
        futures = [executor.submit(worker) for _ in range(config.concurrency)]
        concurrent.futures.wait(futures)
        for future in futures:
            future.result()  # re-raise anything a worker thread raised unexpectedly

    print(f"[summary] processed {state['processed']} task(s)")
    return state["exit_code"]


def main(argv: list[str]) -> int:
    config = parse_args(argv)
    return run(config)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
