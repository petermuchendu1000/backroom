"use client";

import { useContext, useEffect, useRef, useState } from "react";
import Image from "next/image";
import { MotionContext } from "./MotionControls";

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
  const { paused } = useContext(MotionContext);
  const [allowsMotion, setAllowsMotion] = useState(false);
  const [hasAppeared, setHasAppeared] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const preference = window.matchMedia("(prefers-reduced-motion: reduce)");
    const update = () => setAllowsMotion(!preference.matches);
    update();
    preference.addEventListener("change", update);
    return () => preference.removeEventListener("change", update);
  }, []);

  useEffect(() => {
    const target = container.current;
    if (!target) return;

    if (!("IntersectionObserver" in window)) {
      setHasAppeared(true);
      return;
    }

    const visibleFraction = 0.2;
    const observer = new IntersectionObserver((entries) => {
      if (!entries.some((entry) => entry.intersectionRatio >= visibleFraction)) return;
      setHasAppeared(true);
      observer.disconnect();
    }, { threshold: visibleFraction });
    observer.observe(target);
    return () => observer.disconnect();
  }, []);

  const [width, height] = artworkSizes[asset];
  const animated = allowsMotion && hasAppeared && !paused && !failed;
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
          src={"/ongero/" + asset + ".webp?loop=continuous"}
          alt=""
          width={width}
          height={height}
          loading="eager"
          unoptimized
          onLoad={() => setLoaded(true)}
          onError={() => setFailed(true)}
          style={{ position: "absolute", inset: 0, opacity: showMotion ? 1 : 0 }}
        />
      )}
    </span>
  );
}
