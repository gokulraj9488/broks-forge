"use client";

import { Clock } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

interface Props {
  open: boolean;
  secondsLeft: number;
  onStay: () => void;
  onLogout: () => void;
}

/**
 * Warns the user 60 seconds before an idle logout, with a live countdown.
 * Dismissing the dialog (Escape / close / "Stay signed in") keeps the session.
 */
export function IdleWarningDialog({ open, secondsLeft, onStay, onLogout }: Props) {
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) onStay();
      }}
    >
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <div className="mb-1 flex h-10 w-10 items-center justify-center rounded-full bg-warning/15">
            <Clock className="h-5 w-5 text-warning" aria-hidden="true" />
          </div>
          <DialogTitle>Your session is about to expire</DialogTitle>
          <DialogDescription>
            You&apos;ll be signed out in{" "}
            <span className="font-semibold tabular-nums text-foreground">{secondsLeft}s</span> due to
            inactivity.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter className="gap-2 sm:justify-end">
          <Button variant="outline" onClick={onLogout}>
            Log out
          </Button>
          <Button onClick={onStay}>Stay signed in</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
