import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { DeveloperToolsContainer } from './developer-tools-container';

describe('DeveloperToolsContainer', () => {
  it('marks developer tools as an isolated surface', () => {
    render(
      <DeveloperToolsContainer
        projects={[]}
        selectedProject={null}
        onSelectProject={vi.fn()}
      />,
    );
    expect(screen.getByTestId('developer-tools-container')).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: 'Developer Tools' }),
    ).toBeInTheDocument();
  });
});
