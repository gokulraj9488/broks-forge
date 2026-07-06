import type { MetadataRoute } from "next";
import { SITE_DESCRIPTION, SITE_NAME } from "@/lib/site";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: SITE_NAME,
    short_name: "Brok's Forge",
    description: SITE_DESCRIPTION,
    start_url: "/",
    display: "standalone",
    background_color: "#2A363B",
    theme_color: "#2A363B",
    categories: ["developer", "productivity", "utilities"],
    icons: [
      { src: "/icon.svg", type: "image/svg+xml", sizes: "any", purpose: "any" },
    ],
  };
}
