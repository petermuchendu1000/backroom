"use client";

import { useEffect, useRef, useState } from "react";
import Image from "next/image";

type MotionAsset = "voice-masking" | "two-voices" | "room-to-talk" | "quiet-landing";

export default function AnimatedArt({
  asset,
  alt = "",
  className,
}: {
  asset: MotionAsset;
  alt?: string;
  className?: string;
}) {
  const container = useRef<HTMLSpanElement>(null);
  const hasAnimated = useRef(false);
  const [animated, setAnimated] = useState(false);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    const target = container.current;
    if (!target) return;

    const preference = window.matchMedia("(prefers-reduced-motion: reduce)");
    let observer: IntersectionObserver | undefined;

    function updateMotion() {
      observer?.disconnect();

      if (preference.matches) {
        setAnimated(false);
        return;
      }
      if (hasAnimated.current || !("IntersectionObserver" in window)) return;

      observer = new IntersectionObserver((entries) => {
        if (!entries.some((entry) => entry.isIntersecting) || preference.matches) return;

        hasAnimated.current = true;
        setAnimated(true);
        observer?.disconnect();
      }, { threshold: 0.2 });

      if (target) observer.observe(target);
    }

    updateMotion();
    preference.addEventListener("change", updateMotion);
    return () => {
      observer?.disconnect();
      preference.removeEventListener("change", updateMotion);
    };
  }, []);

  const width = asset === "two-voices" ? 672 : 504;
  const height = asset === "two-voices" ? 416 : 312;
  const showMotion = animated && loaded;

  return (
    <span className={className} ref={container} style={{ display: "block", position: "relative" }}>
      <Image
        src={"/ongero/" + asset + "-still.webp"}
        alt={alt}
        width={width}
        height={height}
        unoptimized
        style={{ opacity: showMotion ? 0 : 1 }}
      />
      {animated && (
        <Image
          src={"/ongero/" + asset + ".webp"}
          alt=""
          width={width}
          height={height}
          loading="eager"
          unoptimized
          onLoad={() => setLoaded(true)}
          onError={() => setAnimated(false)}
          style={{ position: "absolute", inset: 0, opacity: showMotion ? 1 : 0 }}
        />
      )}
    </span>
  );
}
