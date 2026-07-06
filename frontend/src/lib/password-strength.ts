/**
 * Heuristic password-strength estimate, aligned with the backend @StrongPassword
 * policy (length + character-class variety). Returns a 1–4 level with a label and
 * a palette indicator class for the strength meter. Empty input scores 0.
 */
export interface PasswordStrength {
  level: 0 | 1 | 2 | 3 | 4;
  label: string;
  percent: number;
  indicatorClassName: string;
}

export function evaluatePasswordStrength(password: string): PasswordStrength {
  if (!password) {
    return { level: 0, label: "", percent: 0, indicatorClassName: "bg-muted" };
  }

  let raw = 0;
  if (password.length >= 8) raw++;
  if (password.length >= 12) raw++;
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) raw++;
  if (/\d/.test(password)) raw++;
  if (/[^A-Za-z0-9]/.test(password)) raw++;

  // Collapse the 0–5 raw score into a 1–4 level for a four-step meter.
  const level = ([1, 1, 2, 3, 4, 4] as const)[raw] as 1 | 2 | 3 | 4;
  const label = ["", "Weak", "Fair", "Good", "Strong"][level];
  const indicatorClassName = ["", "bg-destructive", "bg-warning", "bg-chart-2", "bg-success"][level];
  // Ensure a visible bar even at the lowest level.
  const percent = Math.max(20, Math.round((raw / 5) * 100));

  return { level, label, percent, indicatorClassName };
}
