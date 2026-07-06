import { ExternalLink, LifeBuoy } from "lucide-react";
import { Button, type ButtonProps } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/** The developer's contact / portfolio site. */
export const DEVELOPER_URL = "https://gokul.quest";

/** Inline "Contact Developer" link (opens in a new tab). */
export function ContactDeveloperLink({ className }: { className?: string }) {
  return (
    <a
      href={DEVELOPER_URL}
      target="_blank"
      rel="noopener noreferrer"
      className={cn(
        "inline-flex items-center gap-1 font-medium text-primary underline-offset-4 transition-colors hover:underline",
        className,
      )}
    >
      Contact Developer
      <ExternalLink className="h-3 w-3" aria-hidden="true" />
    </a>
  );
}

/** "Need help? Contact Developer" one-liner for footers. */
export function ContactDeveloper({ className }: { className?: string }) {
  return (
    <p className={cn("text-xs text-muted-foreground", className)}>
      Need help? <ContactDeveloperLink />
    </p>
  );
}

/** Button variant of the contact link, for pages and empty states. */
export function ContactDeveloperButton({
  className,
  variant = "outline",
  size,
}: {
  className?: string;
  variant?: ButtonProps["variant"];
  size?: ButtonProps["size"];
}) {
  return (
    <Button asChild variant={variant} size={size} className={className}>
      <a href={DEVELOPER_URL} target="_blank" rel="noopener noreferrer">
        <LifeBuoy className="h-4 w-4" aria-hidden="true" />
        Contact Developer
      </a>
    </Button>
  );
}
