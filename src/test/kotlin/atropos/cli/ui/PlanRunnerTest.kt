/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import org.junit.jupiter.api.Test

class PlanRunnerTest {
    @Test
    fun runPlan() {
        val prompt = """MUSIC MAKERLM — BUILD BLUEPRINT

A generate + analyze + teach music application built on a symbolic core. Built ${'$'}0 during development; monetization is a later config flip, not a rewrite.

====================================================================
CORE DESIGN PRINCIPLE
====================================================================

Every external dependency (LLM, embeddings, audio generation) sits behind an abstraction layer in app/providers/. Each has a free LOCAL implementation and a paid API implementation. The backend swaps between them via environment variables (LLM_PROVIDER, AUDIO_PROVIDER). Build and test entirely free; flip to paid providers only when monetizing.

Music lives as SYMBOLIC DATA (MIDI + MusicXML), never audio. Audio is generated last, from symbolic data, by a synthesizer. This is what makes editable parts, accurate analysis, and teaching possible — the things audio-first tools cannot do.

====================================================================
STACK
====================================================================

- Engine language: Python 3.11+
- UI: HTML/CSS/JS, with OpenSheetMusicDisplay for in-browser notation
- Web framework: FastAPI + Uvicorn (async, auto API docs)
- Database: SQLite (dev); SQLAlchemy keeps it Postgres-ready
- LLM, build phase (${'$'}0): Ollama (local); swap to OpenAI via LLM_PROVIDER
- Audio, build phase (${'$'}0): FluidSynth + free SoundFonts; swap to hosted gen via AUDIO_PROVIDER
- Hosting, build: localhost, then Oracle Cloud Always-Free VM
- Hosting, paid: add Cloudflare Pages (marketing/SEO)
- Payments, paid only: Stripe (${'$'}0 until a real sale occurs)

Not Vercel for the app backend (serverless can't hold FluidSynth/local LLM). Not "Google for everything" (Cloud Run free tier won't hold a local model affordably). Static marketing/demo goes to Cloudflare Pages.

====================================================================
LIBRARIES (all free; pip unless marked system)
====================================================================

Symbolic core: music21, pretty_midi, MIDIUtil, mido, python-rtmidi, partitura, lxml
Notation: verovio, MuseScore CLI (system), LilyPond (system), abjad, OpenSheetMusicDisplay (JS)
Audio synthesis: FluidSynth (system), pyfluidsynth, soundfile, pydub, ffmpeg (system), librosa
Generation (optional, local): audiocraft (MusicGen), note_seq
LLM / RAG (local now, ${'$'}0): Ollama (system), llama-cpp-python, chromadb, faiss-cpu, sentence-transformers
Web/app: fastapi, uvicorn, pydantic, pydantic-settings, jinja2, python-multipart, aiofiles, sqlalchemy, alembic, httpx
Dev: pytest, pytest-asyncio, black, ruff, mypy
Public-domain corpus sources: music21 built-in corpus, IMSLP, KernScores/Humdrum, MuseScore.com public-domain MusicXML

====================================================================
STARTING TREE (build this first)
====================================================================

musicmakerlm/
  AGENTS.md
  README.md
  requirements.txt
  requirements-dev.txt
  pyproject.toml
  .gitignore
  .env.example
  run.sh
  app/
    __init__.py
    main.py
    config.py                # reads LLM_PROVIDER, AUDIO_PROVIDER
    routes/
      __init__.py
      generate.py
      analyze.py
      teach.py
      health.py
    core/
      __init__.py
      data_model.py          # Piece/Part/Measure/Note/Chord
      generation.py
      analysis.py
      tutor.py
      render_notation.py
      render_audio.py
    providers/               # the swap layer — ${'$'}0 now, paid later
      __init__.py
      base.py                # LLMProvider, AudioProvider interfaces
      llm_local.py           # Ollama
      llm_api.py             # OpenAI (stub until paid)
      audio_local.py         # FluidSynth
      audio_api.py           # hosted gen (stub until paid)
    llm/
      __init__.py
      prompts.py
    db/
      __init__.py
      models.py
  corpus/.gitkeep
  soundfonts/.gitkeep
  models/.gitkeep
  static/
    index.html
    css/style.css
    js/app.js
  tests/
    test_generation.py
    test_analysis.py
    test_render.py
  scripts/
    setup_env.sh
    download_corpus.py
    pull_llm_model.sh

====================================================================
DEPLOYED TREE (complete product)
====================================================================

musicmakerlm/
  AGENTS.md
  README.md
  LICENSE
  requirements.txt
  requirements-dev.txt
  pyproject.toml
  .gitignore
  .env.example
  .env
  Dockerfile
  docker-compose.yml
  Makefile
  run.sh
  app/
    __init__.py
    main.py
    config.py
    dependencies.py
    middleware.py
    routes/
      __init__.py
      generate.py
      analyze.py
      teach.py
      library.py
      projects.py
      export.py
      auth.py                # paid phase
      billing.py             # paid phase (Stripe)
      health.py
    core/
      __init__.py
      data_model.py
      schema_validation.py
      generation/
        __init__.py
        composer.py
        harmony.py
        voice_leading.py
        orchestration.py
        rhythm.py
        form.py
        style_engine.py
        beats.py
      analysis/
        __init__.py
        key_mode.py
        roman_numerals.py
        chord_extract.py
        form_detect.py
        part_split.py
        motif.py
        counterpoint.py
        style_profile.py
      tutor/
        __init__.py
        lesson_builder.py
        progressive.py
        practice_plan.py
        explain.py
      render/
        __init__.py
        notation.py
        audio.py
        soundfont_mgr.py
        export.py
      ingest/
        __init__.py
        musicxml_in.py
        midi_in.py
        audio_transcribe.py
        corpus_loader.py
    providers/
      __init__.py
      base.py
      factory.py             # picks impl from env var
      llm_local.py
      llm_api.py
      embeddings_local.py
      embeddings_api.py
      audio_local.py
      audio_api.py
    llm/
      __init__.py
      prompts.py
      rag.py
      structured_output.py
    knowledge/
      __init__.py
      style_profiles/        # mahler.json, rachmaninoff.json, bach.json...
      theory_rules.py
      instrument_ranges.py
    db/
      __init__.py
      models.py
      crud.py
      session.py
    utils/
      __init__.py
      logging.py
      errors.py
      files.py
  corpus/                    # gitignored, downloaded
    beethoven/
    mahler/
    bach/
    rachmaninoff/
    index.db
  vector_index/              # gitignored
  soundfonts/
    FluidR3_GM.sf2
    MuseScore_General.sf3
  models/                    # gitignored (local LLM weights, build phase)
  static/
    index.html
    app.html
    css/style.css
    js/
      app.js
      osmd_render.js
      player.js
      editor.js
    img/
    vendor/opensheetmusicdisplay.min.js
  templates/
    base.html
    landing.html
    lesson.html
    studio.html
  marketing/                 # Cloudflare Pages, SEO
    index.html
    sitemap.xml
    robots.txt
    structured-data.json
    blog/
  tests/
    test_generation.py
    test_analysis.py
    test_tutor.py
    test_render.py
    test_ingest.py
    test_providers.py
    test_routes.py
    fixtures/
  scripts/
    setup_env.sh
    download_corpus.py
    build_style_profiles.py
    build_vector_index.py
    download_soundfonts.sh
    pull_llm_model.sh
  docs/
    architecture.md
    data_model.md
    api.md

====================================================================
DATA FLOW
====================================================================

Generate: prompt -> route -> providers.llm (local now) -> structured JSON (validated against data_model) -> generation/ builds MIDI -> render/notation (Verovio) + render/audio (FluidSynth) -> UI.

Analyze / Teach a known work: request -> ingest/ loads public-domain score -> analysis/ extracts key, form, roman numerals, motifs, part split (deterministic, accurate) -> render/ notation + audio -> tutor/ narrates via LLM. Facts come from music21; the LLM only explains them.

Compose "in the style of": scripts/build_style_profiles.py precomputes mahler.json etc. from the corpus via analysis/style_profile.py + counterpoint.py (measurable tendencies: who carries melody, frequency of independent simultaneous lines, voicing/spacing, harmonic rhythm, modal usage). generation/style_engine.py biases output toward those stats. The LLM proposes structure within theory rules the code enforces.

Mahler vs Rachmaninoff comparison: diff the two style-profile JSONs; the LLM narrates the contrast. Factual basis = extracted stats; prose quality = whichever LLM provider is active.

====================================================================
BUILD ORDER
====================================================================

1. data_model.py + walking skeleton: prompt -> JSON -> MIDI -> FluidSynth audio (4 bars).
2. providers/ abstraction with local implementations; OpenAI implementations stubbed.
3. analysis/ engine; test on the music21 corpus.
4. tutor/ layered over analysis output.
5. render/ notation (Verovio) + export (MIDI / MusicXML / PDF / MP3).
6. scripts/download_corpus.py + build_style_profiles.py.
7. generation/style_engine.py.
8. Frontend (OSMD viewer + player).
9. Paid phase: flip LLM_PROVIDER=api, add auth/billing routes, deploy marketing site.

====================================================================
BILLING POSTURE
====================================================================

${'$'}0 through steps 1-8: Ollama + FluidSynth + SQLite + localhost/Oracle free VM. No card, no API keys, no charges. Monetization is a later config flip plus the auth/billing routes — not a rebuild. Stripe costs nothing until a real sale.

====================================================================
${'$'}0-NOW LIMITS (honest)
====================================================================

- Local LLM is weaker and slower than a hosted API; style explanations are decent now, expert-grade after the paid flip.
- Realistic vocal/full-mix audio needs the optional audio API (paid) or a local GPU. FluidSynth gives clean instrumental audio for free.
- audio_transcribe.py (arbitrary recordings -> notation) is unreliable; keep symbolic input for accuracy.
- "Completely free" and "API-quality intelligence" cannot both be true at once. The abstraction layer lets you defer that choice without rework."""
        val output = AppFactoryPlanRenderer().renderPlan(prompt)
        println("=== FACTORY PLAN OUTPUT ===")
        println(output)
        println("===========================")
    }
}
