"use client";

import { NavLinks } from "@/components/navigation/nav-links";
import { useNavItems } from "@/components/navigation/use-nav-items";

export function AppSidebar() {
  const { global, project, developer } = useNavItems();
  return (
    <aside className="sg-sidebar" aria-label="Application sections">
      <p className="sg-sidebar-kicker">ATROPOS</p>
      <NavLinks items={global} className="sg-pressable" />
      {project.length > 0 ? (
        <>
          <p className="sg-sidebar-kicker">Project</p>
          <NavLinks items={project} className="sg-pressable" />
        </>
      ) : null}
      {/* §2.10: absent entirely unless the operator has opted in. */}
      {developer.length > 0 ? (
        <>
          <p className="sg-sidebar-kicker">Developer</p>
          <NavLinks items={developer} className="sg-pressable" />
        </>
      ) : null}
    </aside>
  );
}
