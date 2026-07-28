'use client';

import { MessageCircle, Send } from 'lucide-react';
import { useState } from 'react';

interface Message {
  id: string;
  author: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: string;
  evidence?: {
    type: string;
    link?: string;
  };
}

interface ConversationViewProps {
  conversationId: string;
  messages?: Message[];
  loading?: boolean;
}

export function ConversationView({
  conversationId,
  messages = [],
  loading = false,
}: ConversationViewProps) {
  const [input, setInput] = useState('');

  const handleSend = () => {
    if (!input.trim()) return;
    // TODO: Send message via API
    setInput('');
  };

  return (
    <div className="flex flex-col h-full bg-sg-neutral-50 dark:bg-sg-neutral-900 rounded-lg border border-sg-neutral-200 dark:border-sg-neutral-800 overflow-hidden">
      {/* Messages */}
      <div className="flex-1 overflow-y-auto space-y-4 p-4">
        {loading ? (
          <div className="flex items-center justify-center h-full">
            <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Loading conversation...</p>
          </div>
        ) : messages.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full text-center space-y-3">
            <MessageCircle className="w-12 h-12 text-sg-neutral-400" />
            <div>
              <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
                No messages yet
              </p>
              <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
                Start a conversation to begin collaborating
              </p>
            </div>
          </div>
        ) : (
          messages.map((message) => (
            <div
              key={message.id}
              className={`flex ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}
            >
              <div
                className={`max-w-xs lg:max-w-md px-4 py-3 rounded-lg ${
                  message.role === 'user'
                    ? 'bg-sg-red-600 text-white'
                    : message.role === 'system'
                      ? 'bg-sg-neutral-200 dark:bg-sg-neutral-700 text-sg-neutral-900 dark:text-sg-neutral-50'
                      : 'bg-white dark:bg-sg-neutral-800 text-sg-neutral-900 dark:text-sg-neutral-50 border border-sg-neutral-200 dark:border-sg-neutral-700'
                }`}
              >
                <p className="text-xs font-semibold opacity-75 mb-1">{message.author}</p>
                <p className="text-sm">{message.content}</p>
                {message.evidence && (
                  <button className="text-xs mt-2 opacity-75 hover:opacity-100 transition-opacity underline">
                    View evidence
                  </button>
                )}
                <p className="text-xs opacity-50 mt-1">
                  {new Date(message.timestamp).toLocaleTimeString()}
                </p>
              </div>
            </div>
          ))
        )}
      </div>

      {/* Input */}
      <div className="border-t border-sg-neutral-200 dark:border-sg-neutral-800 p-4 bg-white dark:bg-sg-neutral-900">
        <div className="flex gap-2">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyPress={(e) => e.key === 'Enter' && handleSend()}
            placeholder="Type a message..."
            className="flex-1 px-4 py-2 border border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg bg-white dark:bg-sg-neutral-800 text-sg-neutral-900 dark:text-sg-neutral-50 placeholder-sg-neutral-500"
          />
          <button
            onClick={handleSend}
            className="px-4 py-2 bg-sg-red-600 text-white rounded-lg hover:bg-sg-red-700 transition-colors flex items-center gap-2"
          >
            <Send className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
