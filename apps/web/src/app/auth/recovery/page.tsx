import { AuthCard } from "@/components/auth/auth-card";
import { RecoveryForm } from "@/components/auth/recovery-form";

export default function RecoveryPage() {
  return (
    <AuthCard title="Recover account">
      <p>Enter your email. If the account can recover access, instructions will be sent.</p>
      <RecoveryForm />
    </AuthCard>
  );
}
