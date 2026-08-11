import Link from "next/link";
import type { NavItem } from "./use-nav-items";

export function NavLinks({ items, className }: { items: NavItem[]; className?: string }) {
  return (
    <>
      {items.map((item) => (
        <Link key={item.id} href={item.href} className={className} aria-current={item.active ? "page" : undefined} data-active={item.active || undefined}>
          {item.label}
        </Link>
      ))}
    </>
  );
}
