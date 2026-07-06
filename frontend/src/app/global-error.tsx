"use client";

import { useEffect } from "react";

/**
 * Last-resort boundary that replaces the root layout, so it must render its own
 * <html>/<body> and cannot rely on Tailwind (globals.css is loaded by the layout
 * it replaces). Kept deliberately minimal with inline styles.
 */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <html lang="en">
      <body
        style={{
          margin: 0,
          minHeight: "100vh",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          gap: "16px",
          background: "#1c2529",
          color: "#f6f5f1",
          fontFamily: "system-ui, -apple-system, Segoe UI, Roboto, sans-serif",
          textAlign: "center",
          padding: "24px",
        }}
      >
        <h1 style={{ fontSize: "20px", fontWeight: 600, margin: 0 }}>Something went wrong</h1>
        <p style={{ fontSize: "14px", color: "#9aa7ad", maxWidth: "24rem", margin: 0 }}>
          An unexpected error occurred. Please try again.
        </p>
        <button
          onClick={reset}
          style={{
            marginTop: "8px",
            padding: "8px 18px",
            borderRadius: "8px",
            border: "none",
            background: "#99B898",
            color: "#2A363B",
            fontSize: "14px",
            fontWeight: 600,
            cursor: "pointer",
          }}
        >
          Try again
        </button>
      </body>
    </html>
  );
}
