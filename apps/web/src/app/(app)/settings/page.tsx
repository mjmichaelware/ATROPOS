'use client';

import { ThemeCustomizer } from '@/components/settings/theme-customizer';

export default function SettingsPage() {
  return (
    <div className="settings-page">
      <h1>Settings</h1>

      <div className="settings-grid">
        <section className="settings-section">
          <h2>Theme & Appearance</h2>
          <ThemeCustomizer />
        </section>

        <section className="settings-section">
          <h2>Profile</h2>
          <div className="setting-item">
            <label>Email</label>
            <input type="email" disabled placeholder="user@example.com" />
          </div>
        </section>

        <section className="settings-section">
          <h2>Privacy & Security</h2>
          <div className="setting-item">
            <label>
              <input type="checkbox" />
              Share analytics
            </label>
          </div>
        </section>
      </div>

      <style jsx>{`
        .settings-page h1 { margin: 0 0 var(--sg-space-5); }

        .settings-grid {
          display: grid;
          gap: var(--sg-space-5);
        }

        .settings-section {
          padding: var(--sg-space-4);
          border: 1px solid var(--sg-border);
          border-radius: var(--sg-radius-lg);
          background: var(--sg-surface);
        }

        .settings-section h2 {
          margin: 0 0 var(--sg-space-3);
          font-size: var(--sg-type-lg);
        }

        .setting-item {
          display: grid;
          gap: var(--sg-space-2);
          margin-bottom: var(--sg-space-3);

          label {
            font-weight: var(--sg-weight-medium);
            font-size: var(--sg-type-sm);
          }

          input {
            padding: var(--sg-space-2) var(--sg-space-3);
            border: 1px solid var(--sg-border);
            border-radius: var(--sg-radius-md);
            background: var(--sg-elevated);
            color: var(--sg-text-primary);
          }
        }
      `}</style>
    </div>
  );
}
