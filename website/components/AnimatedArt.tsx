"use client";

import { useEffect, useRef, useState } from "react";
import Image from "next/image";

const artworkSizes = {
  "voice-masking": [504, 312],
  "two-voices": [672, 416],
  "room-to-talk": [504, 312],
  "quiet-landing": [504, 312],
  "take-your-time": [504, 312],
  "taking-turns": [672, 416],
} as const;

type MotionAsset = keyof typeof artworkSizes;

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
      if (hasAnimated.current) return;

      function startAnimation() {
        if (preference.matches) return;
        hasAnimated.current = true;
        setAnimated(true);
        observer?.disconnect();
      }

      if (!("IntersectionObserver" in window)) {
        startAnimation();
        return;
      }

      const visibleFraction = 0.2;
      observer = new IntersectionObserver((entries) => {
        if (entries.some((entry) => entry.intersectionRatio >= visibleFraction)) startAnimation();
      }, { threshold: visibleFraction });

      if (target) observer.observe(target);
    }

    updateMotion();
    preference.addEventListener("change", updateMotion);
    return () => {
      observer?.disconnect();
      preference.removeEventListener("change", updateMotion);
    };
  }, []);

  const [width, height] = artworkSizes[asset];
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
