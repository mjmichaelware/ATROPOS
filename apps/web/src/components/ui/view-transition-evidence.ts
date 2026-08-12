import { runViewTransition } from '@/components/ui/view-transition';

/** Keeps evidence state attached while an evidence card changes view. */
export const ViewTransitionEvidence = {
  morph(update: () => void, reducedMotion = false): void {
    runViewTransition(update, { reducedMotion });
  },
};
