import Image from "next/image";
import overview from "@/Screenshots/markuphero-6a5fc2b2603670e424f78d07.jpg";
import agents from "@/Screenshots/markuphero-6a5fba2c603670e424f78c1e.jpg";
import benchmarkGallery from "@/Screenshots/markuphero-6a5d3af68b0e136ec01b6319.jpg";
import rootCause from "@/Screenshots/markuphero-6a5fbf6a603670e424f78cd8.jpg";
import knowledge from "@/Screenshots/markuphero-6a5fb6fe603670e424f78be2.jpg";
import { Reveal } from "./reveal";
import { cn } from "@/lib/utils";

const SHOWCASE = [
  {
    image: overview,
    title: "One workspace, every project",
    description:
      "Organizations, agents, evaluations and provider health in one dashboard — the operational roll-up for whoever owns the AI system, not a chatbot demo.",
  },
  {
    image: rootCause,
    title: "See why a run failed, not just that it did",
    description:
      "Every run keeps its exact rendered prompt, per-metric results and a stage-by-stage execution timeline — the difference between a red X and an answer. This is the part a wrapper around an LLM API can't do.",
  },
  {
    image: agents,
    title: "A registry for every agent you run",
    description:
      "Register agents regardless of framework, track health per provider, and see what's actually deployed across projects instead of tribal knowledge.",
  },
  {
    image: benchmarkGallery,
    title: "Start from a real benchmark, not a blank page",
    description:
      "Curated templates — RAG, coding, hallucination, safety, summarization — each provisioning a starter dataset, prompt and scoring profile in one click.",
  },
  {
    image: knowledge,
    title: "A knowledge base that grows with your evaluations",
    description:
      "Failure modes, regressions and recommendations, linked by cause and effect — so the same mistake gets caught faster the second time.",
  },
];

export function ScreenshotShowcase() {
  return (
    <section id="screenshots" className="border-b border-border/60 py-24">
      <div className="container">
        <Reveal className="mx-auto max-w-2xl text-center">
          <h2 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
            The platform, not a mockup
          </h2>
          <p className="mt-4 text-lg text-muted-foreground">
            Every screen below is the actual application you get after signing in.
          </p>
        </Reveal>

        <div className="mt-20 flex flex-col gap-28">
          {SHOWCASE.map((item, i) => {
            const reversed = i % 2 === 1;
            return (
              <Reveal key={item.title} delay={0.05}>
                <div
                  className={cn(
                    "grid items-center gap-10 lg:grid-cols-2 lg:gap-20",
                    reversed && "lg:[direction:rtl]",
                  )}
                >
                  <div className="overflow-hidden rounded-xl border border-border/60 bg-card shadow-sm lg:[direction:ltr]">
                    <Image
                      src={item.image}
                      alt={item.title}
                      placeholder="blur"
                      loading="lazy"
                      sizes="(min-width: 1024px) 50vw, 100vw"
                      className="h-auto w-full object-contain"
                    />
                  </div>
                  <div className="lg:[direction:ltr]">
                    <h3 className="text-2xl font-semibold tracking-tight text-foreground">
                      {item.title}
                    </h3>
                    <p className="mt-3 leading-relaxed text-muted-foreground">{item.description}</p>
                  </div>
                </div>
              </Reveal>
            );
          })}
        </div>
      </div>
    </section>
  );
}
