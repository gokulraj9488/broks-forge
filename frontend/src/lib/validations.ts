import { z } from "zod";

// Mirrors the backend @StrongPassword policy: 8-72 chars, upper, lower, digit.
const strongPassword = z
  .string()
  .min(8, "At least 8 characters")
  .max(72, "At most 72 characters")
  .regex(/[A-Z]/, "Include an uppercase letter")
  .regex(/[a-z]/, "Include a lowercase letter")
  .regex(/[0-9]/, "Include a digit");

const slug = z
  .string()
  .regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/, "Lowercase letters, digits and single hyphens only")
  .max(64)
  .optional()
  .or(z.literal(""));

export const loginSchema = z.object({
  email: z.string().min(1, "Email is required").email("Enter a valid email"),
  password: z.string().min(1, "Password is required"),
});
export type LoginValues = z.infer<typeof loginSchema>;

export const registerSchema = z.object({
  firstName: z.string().max(100).optional(),
  lastName: z.string().max(100).optional(),
  email: z.string().min(1, "Email is required").email("Enter a valid email"),
  password: strongPassword,
});
export type RegisterValues = z.infer<typeof registerSchema>;

export const forgotPasswordSchema = z.object({
  email: z.string().min(1, "Email is required").email("Enter a valid email"),
});
export type ForgotPasswordValues = z.infer<typeof forgotPasswordSchema>;

export const resetPasswordSchema = z
  .object({
    newPassword: strongPassword,
    confirmPassword: z.string(),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });
export type ResetPasswordValues = z.infer<typeof resetPasswordSchema>;

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, "Current password is required"),
    newPassword: strongPassword,
    confirmPassword: z.string(),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });
export type ChangePasswordValues = z.infer<typeof changePasswordSchema>;

export const profileSchema = z.object({
  firstName: z.string().max(100).optional(),
  lastName: z.string().max(100).optional(),
});
export type ProfileValues = z.infer<typeof profileSchema>;

export const organizationSchema = z.object({
  name: z.string().min(2, "At least 2 characters").max(120),
  slug,
  description: z.string().max(1000).optional(),
});
export type OrganizationValues = z.infer<typeof organizationSchema>;

export const projectSchema = z.object({
  name: z.string().min(2, "At least 2 characters").max(120),
  slug,
  description: z.string().max(1000).optional(),
});
export type ProjectValues = z.infer<typeof projectSchema>;

export const addMemberSchema = z.object({
  email: z.string().min(1, "Email is required").email("Enter a valid email"),
  role: z.enum(["ADMIN", "MEMBER"]),
});
export type AddMemberValues = z.infer<typeof addMemberSchema>;

export const apiKeySchema = z.object({
  name: z.string().min(1, "Name is required").max(120),
  // Empty input -> undefined (no expiry); otherwise a positive integer of days.
  expiresInDays: z.preprocess(
    (v) => (v === "" || v === undefined || v === null ? undefined : Number(v)),
    z
      .number({ invalid_type_error: "Enter a number of days" })
      .int("Enter a whole number")
      .positive("Must be positive")
      .max(3650, "At most 3650 days")
      .optional(),
  ),
});
export type ApiKeyValues = z.infer<typeof apiKeySchema>;
