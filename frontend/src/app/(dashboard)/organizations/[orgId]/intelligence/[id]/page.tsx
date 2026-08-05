"use client";

import { useParams } from "next/navigation";
import { KnowledgeObjectDetail } from "@/components/platform/knowledge-object-detail";

export default function KnowledgeObjectPage() {
  const params = useParams<{ orgId: string; id: string }>();
  // Next.js decodes the route segment, so the composite id (with its colons) arrives ready to use.
  const id = decodeURIComponent(params.id);
  return <KnowledgeObjectDetail organizationId={params.orgId} id={id} />;
}
