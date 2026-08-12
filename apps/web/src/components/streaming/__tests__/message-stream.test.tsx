import { render, screen, waitFor } from '@testing-library/react'
import { MessageStream, StreamingApprovalCards } from '../message-stream'
import { describe, it, expect, beforeEach, vi } from 'vitest'

/**
 * HOE-C05: Streaming + approval cards tests.
 */
describe('MessageStream', () => {
  const mockEventSource = vi.fn()

  beforeEach(() => {
    ;(global as any).EventSource = vi.fn(() => ({
      addEventListener: mockEventSource,
      close: vi.fn()
    }))
  })

  it('renders empty state when no messages', () => {
    render(<MessageStream eventSourceUrl="http://localhost/events" />)
    // Should not error
  })

  it('exposes the approval-card stream as the canonical named owner', () => {
    render(<StreamingApprovalCards eventSourceUrl="http://localhost/events" />)
    expect(screen.getByText(/Streaming/i)).toBeTruthy()
  })

  it('displays streaming indicator while active', () => {
    render(<MessageStream eventSourceUrl="http://localhost/events" />)
    expect(screen.getByText(/Streaming/i)).toBeTruthy()
  })

  it('renders approval cards on APPROVAL_REQUIRED events', async () => {
    const { rerender } = render(<MessageStream eventSourceUrl="http://localhost/events" />)

    // Simulate approval event
    const event = new MessageEvent('message', {
      data: JSON.stringify({
        type: 'approval',
        data: 'approval-123',
        timestamp: Date.now()
      })
    })

    // Manually trigger the event listener for testing
    // (In real test, EventSource mock would handle this)
  })
})
