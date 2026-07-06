import { ImageResponse } from "next/og";
import { SITE_NAME, SITE_TAGLINE } from "@/lib/site";

export const runtime = "nodejs";
export const alt = `${SITE_NAME} — ${SITE_TAGLINE}`;
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

// Rendered at build time to a static PNG — the social preview card.
export default function OpengraphImage() {
  return new ImageResponse(
    (
      <div
        style={{
          height: "100%",
          width: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "center",
          padding: "80px",
          background: "#2A363B",
          color: "#F6F5F1",
          fontFamily: "sans-serif",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: "24px" }}>
          <div
            style={{
              width: "88px",
              height: "88px",
              borderRadius: "22px",
              background: "linear-gradient(135deg, #99B898, #FF847C)",
            }}
          />
          <div style={{ fontSize: "40px", fontWeight: 700 }}>{SITE_NAME}</div>
        </div>
        <div style={{ fontSize: "66px", fontWeight: 800, lineHeight: 1.1, marginTop: "48px", maxWidth: "900px" }}>
          {SITE_TAGLINE}
        </div>
        <div style={{ fontSize: "30px", color: "#99B898", marginTop: "28px" }}>
          Register · Evaluate · Benchmark · Operate
        </div>
      </div>
    ),
    { ...size },
  );
}
