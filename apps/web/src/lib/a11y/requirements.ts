import { colourOnlyStates, type StatePresentation } from './conformance';

export const ACCESSIBILITY_REQUIREMENTS = {
  keyboard: true,
  focusVisibility: true,
  screenReaderLabels: true,
  reducedMotion: true,
  contrastChecks: true,
} as const;

export interface AccessibilitySurfaceInput {
  states: readonly StatePresentation[];
  keyboardReachable: boolean;
  focusVisible: boolean;
  labelledControls: boolean;
  reducedMotionSupported: boolean;
}

export function accessibilityRequirementsSatisfied(input: AccessibilitySurfaceInput): boolean {
  return input.keyboardReachable &&
    input.focusVisible &&
    input.labelledControls &&
    input.reducedMotionSupported &&
    colourOnlyStates(input.states).length === 0;
}
