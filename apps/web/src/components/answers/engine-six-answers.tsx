/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import { type EngineAnswer } from '@/lib/engine/client';
import { useAnswersStream } from '@/lib/engine/use-answers-stream';

/**
 * The six continuous answers, read from the engine.
 *
 * This replaces a panel whose six answers were literal strings in the page and
 * whose health row was hard-coded `true`. Source Doc 4 §0.1 wants six answers
 * that are true; §0 forbids reporting an unverified state as verified. A
 * permanently-green cockpit satisfies neither — it is exactly the "cockpit that
 * guesses" the CLI's own provider warns is more dangerous than one that admits
 * it cannot see.
 *
 * So every value here comes from `GET /v1/answers`, and when the bridge is not
 * running the panel says so and stops. It never falls back to placeholder text,
 * because a placeholder that looks like an answer is indistinguishable from one.
 */
export function EngineSixAnswers() {
  const { payload, failure, loading, streaming } = useAnswersStream();

  if (loading) {
    return <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">Reading engine state…</p>;
  }

  if (failure) {
    return (
      <div
        role="status"
        className="space-y-1 rounded-lg border border-sg-amber-300 bg-sg-amber-50 p-4 dark:border-sg-amber-900 dark:bg-sg-amber-900/20"
      >
        <p className="font-semibold text-sg-amber-900 dark:text-sg-amber-100">
          Engine not answering — no state to show
        </p>
        <p className="text-sm text-sg-amber-800 dark:text-sg-amber-200">{failure.detail}</p>
        <p className="text-sm text-sg-amber-800 dark:text-sg-amber-200">{failure.remedy}</p>
      </div>
    );
  }

  if (!payload) return null;

  const rows: Array<{ question: string; answer: EngineAnswer }> = [
    { question: 'What am I trying to accomplish?', answer: payload.answers.objective },
    { question: 'What is happening now?', answer: payload.answers.doing },
    { question: 'Why is it happening?', answer: payload.answers.why },
    { question: 'How far along is it?', answer: payload.answers.progress },
    { question: 'What happens next?', answer: payload.answers.next },
    { question: 'Where is the evidence?', answer: payload.answers.evidence },
  ];

  return (
    <div className="space-y-3">
      <dl className="space-y-3">
        {rows.map(({ question, answer }) => (
          <div
            key={question}
            className="rounded-lg border border-sg-neutral-200 p-4 dark:border-sg-neutral-800"
          >
            <dt className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">{question}</dt>
            <dd className="mt-1 flex items-start justify-between gap-3">
              <span className="font-medium text-sg-neutral-900 dark:text-sg-neutral-50">
                {answer.value}
              </span>
              {/* §E: the status word travels with the colour, never instead of it. */}
              <span
                data-health={answer.health}
                className="shrink-0 rounded border border-sg-neutral-300 px-2 py-0.5 text-xs uppercase tracking-wide text-sg-neutral-700 dark:border-sg-neutral-700 dark:text-sg-neutral-300"
              >
                {answer.signal}
              </span>
            </dd>
          </div>
        ))}
      </dl>

      {/* Streaming is stated rather than implied: a surface that shows a
          one-shot read as though it were live is claiming freshness it does
          not have. */}
      <p className="text-xs text-sg-neutral-500 dark:text-sg-neutral-500">
        {streaming ? 'Live — pushed by the engine.' : 'Snapshot — not receiving live updates.'}
      </p>

      {/* An unreadable queue is a fault, not an idle state, and says so. */}
      {!payload.queue.readable && (
        <p className="text-sm font-medium text-sg-red-700 dark:text-sg-red-300">
          Queue unreadable — the counts below are not a report of an empty queue.
        </p>
      )}
      {!payload.projectsReadable && (
        <p className="text-sm font-medium text-sg-red-700 dark:text-sg-red-300">
          Project registry unreadable.
        </p>
      )}
    </div>
  );
}
