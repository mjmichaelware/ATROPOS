import { Skeleton } from "@/components/ui/skeleton";

export default function Loading() {
  return (
    <section aria-label="Loading foundation">
      <Skeleton style={{ width: "12rem", height: "2rem" }} />
      <Skeleton style={{ width: "80%", height: "8rem", marginTop: "1rem" }} />
    </section>
  );
}
