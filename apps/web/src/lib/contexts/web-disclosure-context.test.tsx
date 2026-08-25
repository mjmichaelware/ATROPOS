/* SPDX-License-Identifier: AGPL-3.0-only */
import { act, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import {
  WebDisclosureProvider,
  useOptionalWebDisclosure,
  useWebDisclosure,
} from './web-disclosure-context';

function Probe() {
  const { level, setLevel } = useWebDisclosure();
  return (
    <button type="button" onClick={() => setLevel(4)} aria-label="level">
      {level}
    </button>
  );
}

function OptionalProbe() {
  const value = useOptionalWebDisclosure();
  return <p>{value == null ? 'bare' : 'mounted'}</p>;
}

afterEach(() => window.localStorage.clear());

describe('ADD-W-004 web disclosure channel', () => {
  it('writes only the web key and reflects the level', () => {
    render(
      <WebDisclosureProvider>
        <Probe />
      </WebDisclosureProvider>,
    );
    // Default is L2 per the shared contract.
    expect(screen.getByRole('button', { name: 'level' }).textContent).toBe('2');
    act(() => {
      screen.getByRole('button', { name: 'level' }).click();
    });
    expect(screen.getByRole('button', { name: 'level' }).textContent).toBe('4');
    expect(window.localStorage.getItem('atropos.disclosure.web')).toBe('4');
  });

  it('exposes an optional hook so bare chrome does not crash', () => {
    render(<OptionalProbe />);
    expect(screen.getByText('bare')).toBeInTheDocument();
  });

  it('restores a persisted level, coercing corrupt values to default', () => {
    window.localStorage.setItem('atropos.disclosure.web', '3');
    render(
      <WebDisclosureProvider>
        <Probe />
      </WebDisclosureProvider>,
    );
    expect(screen.getByRole('button', { name: 'level' }).textContent).toBe('3');
  });
});
