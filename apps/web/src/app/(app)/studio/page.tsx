'use client';

import { LivePreviewStudio } from '@/components/studio/live-preview-studio';

export default function StudioPage() {
  return (
    <div className="h-[calc(100vh-8rem)] w-full p-4 md:p-6">
      <LivePreviewStudio initialUrl="/" />
    </div>
  );
}
