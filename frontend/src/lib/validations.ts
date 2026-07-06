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

// OTP password change (ADR 0017). Step 1: current password only.
export const changePasswordSchema = z.object({
  currentPassword: z.string().min(1, "Current password is required"),
});
export type ChangePasswordValues = z.infer<typeof changePasswordSchema>;

// Step 2: the 6-digit code from e-mail.
export const verifyOtpSchema = z.object({
  code: z.string().regex(/^\d{6}$/, "Enter the 6-digit code from your email"),
});
export type VerifyOtpValues = z.infer<typeof verifyOtpSchema>;

// Step 3 (and the legacy emailed link) share the reset-password shape: new + confirm.
export const confirmPasswordChangeSchema = resetPasswordSchema;
export type ConfirmPasswordChangeValues = ResetPasswordValues;

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

// ---------------------------------------------------------------------------
// Agent Registry (Phase 2)
// ---------------------------------------------------------------------------
function isHttpUrl(value: string): boolean {
  try {
    const url = new URL(value);
    return (url.protocol === "http:" || url.protocol === "https:") && !url.username && !!url.host;
  } catch {
    return false;
  }
}

export const agentSchema = z.object({
  name: z.string().min(2, "At least 2 characters").max(120),
  slug,
  description: z.string().max(1000).optional(),
  endpointUrl: z
    .string()
    .min(1, "Endpoint URL is required")
    .max(2048)
    .refine(isHttpUrl, "Must be a valid http(s) URL without embedded credentials"),
  visibility: z.enum(["PRIVATE", "ORGANIZATION", "PUBLIC"]),
  framework: z.enum([
    "SPRING_AI",
    "LANGGRAPH",
    "LANGCHAIN",
    "CREWAI",
    "AUTOGEN",
    "PYDANTIC_AI",
    "SEMANTIC_KERNEL",
    "LLAMA_INDEX",
    "CUSTOM_REST",
    "OTHER",
  ]),
  language: z.enum(["JAVA", "PYTHON", "NODE", "TYPESCRIPT", "GO", "RUST", "CSHARP", "OTHER"]),
  authType: z.enum(["NONE", "API_KEY", "BEARER_TOKEN", "BASIC_AUTH", "CUSTOM_HEADER"]),
  streaming: z.boolean().optional(),
  memory: z.boolean().optional(),
  rag: z.boolean().optional(),
  toolCalling: z.boolean().optional(),
  structuredOutput: z.boolean().optional(),
  reasoning: z.boolean().optional(),
  multiAgent: z.boolean().optional(),
  tags: z.string().max(500).optional(),
});
export type AgentValues = z.infer<typeof agentSchema>;

export const agentVersionSchema = z.object({
  versionNumber: z
    .string()
    .min(1, "Version number is required")
    .regex(/^[A-Za-z0-9][A-Za-z0-9._+-]{0,63}$/, "Letters, digits and . _ + - only"),
  model: z.string().min(1, "Model is required").max(128),
  provider: z.enum([
    "OPENAI",
    "ANTHROPIC",
    "AZURE_OPENAI",
    "AWS_BEDROCK",
    "GOOGLE_VERTEX",
    "GOOGLE_GEMINI",
    "COHERE",
    "MISTRAL",
    "META_LLAMA",
    "OLLAMA",
    "HUGGINGFACE",
    "CUSTOM",
    "OTHER",
  ]),
  frameworkVersion: z.string().max(64).optional(),
  gitCommitSha: z
    .string()
    .regex(/^[0-9a-fA-F]{7,64}$/, "7-64 hex characters")
    .optional()
    .or(z.literal("")),
  promptVersion: z.string().max(64).optional(),
  environment: z.enum(["DEVELOPMENT", "STAGING", "PRODUCTION"]),
  releaseNotes: z.string().max(2000).optional(),
  rollbackReady: z.boolean().optional(),
  activate: z.boolean().optional(),
});
export type AgentVersionValues = z.infer<typeof agentVersionSchema>;

export const setCredentialSchema = z
  .object({
    label: z.string().max(120).optional(),
    authType: z.enum(["NONE", "API_KEY", "BEARER_TOKEN", "BASIC_AUTH", "CUSTOM_HEADER"]),
    secret: z.string().max(4096).optional(),
    username: z.string().max(256).optional(),
    headerName: z.string().max(128).optional(),
    headerPrefix: z.string().max(64).optional(),
    // When editing an existing credential, a blank secret keeps the stored one.
    keepSecret: z.boolean().optional(),
  })
  .superRefine((data, ctx) => {
    if (data.authType === "NONE") return;
    if (!data.secret && !data.keepSecret) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["secret"], message: "A secret is required" });
    }
    if (data.authType === "BASIC_AUTH" && !data.username) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["username"], message: "Username is required" });
    }
    if (data.authType === "CUSTOM_HEADER" && !data.headerName) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["headerName"], message: "Header name is required" });
    }
  });
export type SetCredentialValues = z.infer<typeof setCredentialSchema>;
