import { Card } from "@/components/ui/card";
import { ProjectCreateForm } from "@/components/projects/project-create-form";

export default function NewProjectPage() {
  return (
    <Card>
      <h1>Create project</h1>
      <p>Create a project record owned by the authenticated account.</p>
      <ProjectCreateForm />
    </Card>
  );
}
