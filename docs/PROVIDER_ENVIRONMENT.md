# Provider onboarding

ATROPOS discovers providers once at startup. A launch command does not need a
model flag or a list of exports. Environment values win over the local vault;
the vault is used by `/providers connect` when an operator deliberately enters
a key in the terminal. Keys are never written to `providers.json`, logs, or
evidence.

| Provider | Environment names discovered |
| --- | --- |
| OpenAI | `OPENAI_API_KEY`, `OPENAI_KEY`, `OPENAI_TOKEN`, `OPENAI_API_BASE` |
| Anthropic | `ANTHROPIC_API_KEY`, `ANTHROPIC_KEY`, `CLAUDE_API_KEY`, `CLAUDE_TOKEN`, `CLAUDE_*` |
| Groq | `GROQ_API_KEY`, `GROQ_KEY`, `GROQ_TOKEN` |
| xAI | `XAI_API_KEY`, `XAI_KEY`, `GROK_API_KEY`, `GROK_TOKEN`, `GROK_*` |
| Gemini | `GEMINI_API_KEY`, `GOOGLE_API_KEY`, `GOOGLE_GEMINI_API_KEY`, `GOOGLE_*` |
| OpenRouter | `OPENROUTER_API_KEY`, `OPENROUTER_KEY` |
| Together | `TOGETHER_API_KEY`, `TOGETHERAI_API_KEY` |
| DeepSeek | `DEEPSEEK_API_KEY`, `DEEPSEEK_KEY` |
| Mistral | `MISTRAL_API_KEY`, `MISTRAL_TOKEN` |
| Cohere | `COHERE_API_KEY` |
| Fireworks | `FIREWORKS_API_KEY`, `FIREWORKS_AI_API_KEY` |
| Azure OpenAI | `AZURE_OPENAI_API_KEY`, `AZURE_API_KEY`, `AZURE_OPENAI_ENDPOINT`, `AZURE_*` |
| Ollama | `OLLAMA_HOST`, `OLLAMA_MODEL` |
| GitHub Models | `GITHUB_MODELS_TOKEN` |
| GitHub Actions | `GITHUB_TOKEN` |
| Cloudflare | `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID` |
| Google application credentials | `GOOGLE_APPLICATION_CREDENTIALS` |
| Cerebras | `CEREBRAS_API_KEY` |
| DeepInfra | `DEEPINFRA_API_KEY` |
| Hugging Face | `HUGGINGFACE_API_KEY` |
| NVIDIA NIM | `NVIDIA_API_KEY` |
| SambaNova | `SAMBANOVA_API_KEY` |
| SiliconFlow | `SILICONFLOW_API_KEY` |
| Jina Reader | `JINA_API_KEY` |
| SerpAPI | `SERPAPI_API_KEY` |
| Supabase | `SUPABASE_URL`, `SUPABASE_ANON_KEY` |
| Pinecone | `PINECONE_API_KEY` |
| Fal.ai | `FAL_AI_API_KEY` |
| Replicate | `REPLICATE_API_TOKEN` |
| Generic ATROPOS provider | `ATROPOS_PROVIDER_*` when the suffix identifies a known descriptor |

AWS variables (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`) are
recorded for future Bedrock transport support but are not routed as a working
Bedrock provider until a signing/transport owner exists.

Useful commands:

```text
/providers list
/providers refresh
/providers test
/providers prefer <provider>
/providers disable <provider>
/providers connect <provider>
```

The cascade uses healthy, enabled providers only. Local/Ollama and free-tier
providers are attempted before paid providers. A paid transition stops with an
approval card; `/providers connect` alone does not approve spending.

If no provider is healthy, ATROPOS remains usable for local inspection and
prints one safe example such as `export GROQ_API_KEY=…`; it does not crash or
invent a successful route.
