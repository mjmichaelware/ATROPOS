import { render, screen, waitFor, fireEvent, act } from '@testing-library/react'
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
    // ADD-W-001/W-005: no stream claim before a real connection opens. The
    // old assertion expected a live pulse on an unconnected EventSource,
    // which was exactly the fabricated stream this component removed.
    expect(screen.queryByText(/Streaming/i)).toBeNull()
    expect(screen.queryByText(/cannot stream/i)).toBeNull()
  })

  it('shows the streaming indicator only after the connection opens', () => {
    let openHandler: (() => void) | null = null
    ;(global as any).EventSource = vi.fn(() => ({
      addEventListener: (kind: string, handler: () => void) => {
        if (kind === 'open') openHandler = handler
      },
      close: vi.fn(),
    }))

    render(<MessageStream eventSourceUrl="http://localhost/events" />)
    expect(screen.queryByText(/Streaming/i)).toBeNull()

    // The engine accepts the connection — only now may the surface say "live".
    act(() => {
      openHandler?.()
    })
    expect(screen.getByText(/Streaming/i)).toBeTruthy()
  })

  it('says unsupported rather than streaming when EventSource is absent', () => {
    ;(global as any).EventSource = undefined
    render(<MessageStream eventSourceUrl="http://localhost/events" />)
    expect(screen.getByText(/cannot stream/i)).toBeTruthy()
    expect(screen.queryByText(/Streaming/i)).toBeNull()
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

// F-WEB-011: a text response carries a copy affordance with text confirmation.
describe('StreamingApprovalCards copy affordance', () => {
  class FakeEventSource {
    static last: FakeEventSource | null = null
    listeners = new Map<string, (event: { data: string }) => void>()
    constructor(public url: string) {
      FakeEventSource.last = this
    }
    addEventListener(kind: string, handler: (event: { data: string }) => void) {
      this.listeners.set(kind, handler)
    }
    close() {}
    emit(data: string) {
      this.listeners.get('message')?.({ data })
    }
  }

  it('copies the response body and confirms in words', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, { clipboard: { writeText } })
    vi.stubGlobal('EventSource', FakeEventSource)

    render(<StreamingApprovalCards eventSourceUrl="http://bridge.test/v1/events" />)
    FakeEventSource.last?.emit(JSON.stringify({ type: 'text', data: 'hello proof', timestamp: 1 }))

    // The response sits inside a collapsed disclosure row (HOE-A08 default);
    // expand it before the copy affordance is on the page at all.
    fireEvent.click(await screen.findByRole('button', { name: /Expand Stream item 1/ }))
    const button = await screen.findByRole('button', { name: 'Copy response' })
    fireEvent.click(button)
    await waitFor(() => expect(writeText).toHaveBeenCalledWith('hello proof'))
    expect(await screen.findByText('Copied')).toBeInTheDocument()

    vi.unstubAllGlobals()
  })
})
