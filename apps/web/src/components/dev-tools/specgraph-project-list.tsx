/* SPDX-License-Identifier: AGPL-3.0-only */

import { type SpecGraphProject } from '@/lib/specgraph/client';

interface SpecGraphProjectListProps {
  projects: SpecGraphProject[];
  selectedProject: string | null;
  onSelectProject: (id: string) => void;
}

export function SpecGraphProjectList({
  projects,
  selectedProject,
  onSelectProject,
}: SpecGraphProjectListProps) {
  return (
    <nav className="sg-devtools-project-list" aria-label="SpecGraph projects">
      <h2 className="sg-devtools-section-title">Projects</h2>
      {projects.length === 0 ? (
        <p className="sg-devtools-empty">
          No SpecGraph projects yet. Create one from the CLI or API.
        </p>
      ) : (
        <ul className="sg-devtools-project-list-items" role="listbox" aria-label="Projects">
          {projects.map((project) => (
            <li key={project.id}>
              <button
                type="button"
                role="option"
                aria-selected={selectedProject === project.id}
                onClick={() => onSelectProject(project.id)}
                className={`sg-devtools-project-item ${
                  selectedProject === project.id ? 'sg-devtools-project-selected' : ''
                }`}
              >
                <span className="sg-devtools-project-name">{project.name}</span>
                <span className="sg-devtools-project-id">{project.id.slice(0, 8)}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </nav>
  );
}
