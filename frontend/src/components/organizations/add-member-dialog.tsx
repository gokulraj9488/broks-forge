"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { UserPlus } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { useAddMember } from "@/lib/hooks/use-organizations";
import { getApiErrorMessage } from "@/lib/api/client";
import { addMemberSchema, type AddMemberValues } from "@/lib/validations";

export function AddMemberDialog({ organizationId }: { organizationId: string }) {
  const [open, setOpen] = useState(false);
  const add = useAddMember(organizationId);
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<AddMemberValues>({
    resolver: zodResolver(addMemberSchema),
    defaultValues: { role: "MEMBER" },
  });

  const onSubmit = (values: AddMemberValues) => {
    add.mutate(values, {
      onSuccess: () => {
        toast.success("Member added");
        setOpen(false);
        reset({ email: "", role: "MEMBER" });
      },
      onError: (error) => toast.error(getApiErrorMessage(error)),
    });
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm">
          <UserPlus className="h-4 w-4" />
          Add member
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add a member</DialogTitle>
          <DialogDescription>
            Add an existing Brok&apos;s Forge user to this organization by email.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <Field label="Email" htmlFor="member-email" error={errors.email?.message} required>
            <Input id="member-email" type="email" placeholder="teammate@example.com" {...register("email")} />
          </Field>
          <Field label="Role" htmlFor="member-role" error={errors.role?.message} required>
            <Select id="member-role" {...register("role")}>
              <option value="MEMBER">Member</option>
              <option value="ADMIN">Admin</option>
            </Select>
          </Field>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={add.isPending}>
              Add member
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
