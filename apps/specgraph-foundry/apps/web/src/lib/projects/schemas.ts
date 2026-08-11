import { z } from "zod";

export const projectCreateSchema = z.object({
  slug: z
    .string()
    .min(1, "Slug is required.")
    .max(80)
    .regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/, "Use lowercase letters, numbers, and single hyphens."),
  name: z.string().min(1, "Name is required.").max(120),
  description: z.string().max(500),
});

export type ProjectCreateInput = z.infer<typeof projectCreateSchema>;

export type Project = {
  id: string;
  slug: string;
  name: string;
  description?: string;
  created_at?: string;
};

export type ProjectListResponse = {
  items: Project[];
};
