export function Toast({ message }: { message: string }) {
  return (
    <div className="sg-toast" role="status" aria-live="polite">
      {message}
    </div>
  );
}
