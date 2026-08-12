import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { DeveloperToolsContainer } from './developer-tools-container';

describe('DeveloperToolsContainer', () => {
  it('marks developer tools as an isolated surface', () => {
    render(<DeveloperToolsContainer><span>inspector</span></DeveloperToolsContainer>);
    expect(screen.getByRole('region', { name: 'Developer Tools' })).toHaveAttribute(
      'data-surface',
      'developer-tools',
    );
    expect(screen.getByText('inspector')).toBeInTheDocument();
  });
});
