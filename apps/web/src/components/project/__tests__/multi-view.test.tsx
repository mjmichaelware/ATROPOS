/* SPDX-License-Identifier: AGPL-3.0-only */
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { ConversationView } from '../conversation-view';
import { ExecutionMonitor } from '../execution-monitor';
import { TimelineView } from '../timeline-view';

describe('Project Multi-View', () => {
  it('renders ConversationView successfully', () => {
    render(<ConversationView projectId="test-project" />);
    expect(screen.getByText('Conversation')).toBeDefined();
  });

  it('renders ExecutionMonitor loading state successfully', () => {
    render(<ExecutionMonitor projectId="test-project" />);
    expect(screen.getByText('Loading execution status...')).toBeDefined();
  });

  it('renders TimelineView successfully', () => {
    render(<TimelineView projectId="test-project" />);
    expect(screen.getByText('Project Timeline')).toBeDefined();
  });
});
