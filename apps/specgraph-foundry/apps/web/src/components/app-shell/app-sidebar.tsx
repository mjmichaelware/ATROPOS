"use client";

import { NavLinks } from "@/components/navigation/nav-links";
import { useNavItems } from "@/components/navigation/use-nav-items";

export function AppSidebar() {
  const { global, project } = useNavItems();
  return (
    <aside className="sg-sidebar" aria-label="Application sections">
      <p className="sg-sidebar-kicker">Foundation</p>
      <NavLinks items={global} className="sg-pressable" />
      {project.length > 0 ? (
        <>
          <p className="sg-sidebar-kicker">Project</p>
          <NavLinks items={project} className="sg-pressable" />
        </>
      ) : null}
    </aside>
  );
}
