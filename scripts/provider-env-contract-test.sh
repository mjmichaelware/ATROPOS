#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REGISTRY="$ROOT/src/main/kotlin/atropos/core/provider/StaticProviderDescriptorRegistry.kt"
DOC="$ROOT/docs/PROVIDER_ENVIRONMENT.md"

test -f "$REGISTRY"
test -f "$DOC"
python3 "$ROOT/scripts/provider-env-readme-generator.py" --check

# The descriptor registry is the runtime source of truth. Keep the human
# onboarding table complete without introducing a second provider catalogue.
missing=()
env_names="$(grep -oE '"[A-Z][A-Z0-9_]*"' "$REGISTRY" | tr -d '"' | sort -u)"
while IFS= read -r env_name; do
  [[ -z "$env_name" ]] && continue
  if ! grep -Fq "\`$env_name\`" "$DOC"; then
    missing+=("$env_name")
  fi
done <<< "$env_names"

if ((${#missing[@]} > 0)); then
  printf 'provider environment documentation missing: %s\n' "${missing[*]}" >&2
  exit 1
fi

# These aliases/globs are discovered by the onboarding owner rather than the
# descriptor list, so they receive an explicit contract too.
for alias in \
  OPENAI_API_KEY OPENAI_API_BASE ANTHROPIC_API_KEY GROQ_API_KEY \
  XAI_API_KEY GROK_API_KEY GOOGLE_API_KEY GEMINI_API_KEY \
  TOGETHER_API_KEY FIREWORKS_API_KEY DEEPSEEK_API_KEY MISTRAL_API_KEY \
  OPENROUTER_API_KEY AZURE_OPENAI_API_KEY AZURE_OPENAI_ENDPOINT \
  AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_REGION OLLAMA_HOST \
  CLAUDE_API_KEY CLAUDE_TOKEN GROK_TOKEN ATROPOS_PROVIDER_; do
  grep -Fq "\`$alias" "$DOC"
done

printf '%s\n' 'ATROPOS_PROVIDER_ENV_CONTRACT_OK'
