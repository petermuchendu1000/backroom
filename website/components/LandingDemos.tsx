"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import { Clock, Ear, MessageCircle, Phone, Play, Square } from "lucide-react";
import styles from "@/app/landing.module.css";

const steps = [
  {
    id: "choose",
    icon: MessageCircle,
    title: "Choose what you need",
    copy: "Choose Share to talk or Listen to hear someone out. If you’re sharing, pick a topic, how it feels, and how long you’d like to talk.",
  },
  {
    id: "preview",
    icon: Ear,
    title: "Take a moment before you join",
    copy: "The listener sees the topic, tone, and length in a short preview. That gives them a moment to decide whether to join.",
  },
  {
    id: "talk",
    icon: Phone,
    title: "Talk at your own pace",
    copy: "Start anywhere. Ask for a pause or leave a detail out. If you’d just like someone to hear you out, you can say so.",
  },
] as const;

function CallExample({ step }: { step: (typeof steps)[number]["id"] }) {
  if (step === "choose") {
    return (
      <>
        <p className={styles.demoLabel}>Choose a role</p>
        <h3>What feels right today?</h3>
        <div className={styles.demoRoles}>
          <div><MessageCircle size={24} aria-hidden="true" /><strong>Share</strong><span>I’d like to talk</span></div>
          <div><Ear size={24} aria-hidden="true" /><strong>Listen</strong><span>I can hear someone out</span></div>
        </div>
      </>
    );
  }

  if (step === "preview") {
    return (
      <>
        <p className={styles.demoLabel}>Soft Preview</p>
        <h3>A moment to decide.</h3>
        <dl className={styles.demoProperties}>
          <div><dt>Topic</dt><dd>Just talking</dd></div>
          <div><dt>Tone</dt><dd>A little heavy</dd></div>
          <div><dt>Length</dt><dd><Clock size={16} aria-hidden="true" /> 10 minutes</dd></div>
        </dl>
        <p className={styles.demoQuote}>“I’d like someone to listen.”</p>
      </>
    );
  }

  return (
    <div className={styles.demoCall}>
      <div className={styles.demoOrb} aria-hidden="true">
        <span className={styles.waveform}><i /><i /><i /><i /><i /><i /><i /></span>
      </div>
      <p className={styles.demoLabel}>Volunteer listener</p>
      <h3>Take your time.</h3>
      <p className={styles.demoQuote}>Voice masking is on.</p>
    </div>
  );
}

export function CallWalkthrough() {
  const [step, setStep] = useState<(typeof steps)[number]["id"]>("choose");

  return (
    <div className={styles.walkthroughGrid}>
      <ul className={styles.steps} aria-label="Explore the steps of a call">
        {steps.map(({ id, icon: Icon, title, copy }) => (
          <li key={id}>
            <button
              type="button"
              aria-pressed={id === step}
              aria-controls="call-example"
              onClick={() => setStep(id)}
            >
              <span className={styles.stepIcon}><Icon size={22} aria-hidden="true" /></span>
              <span><strong>{title}</strong><span>{copy}</span></span>
            </button>
          </li>
        ))}
      </ul>
      <div id="call-example" className={styles.demoBackdrop} aria-live="polite" aria-atomic="true">
        <div className={styles.demoCard} key={step}>
          <CallExample step={step} />
        </div>
        <p className={styles.demoNote}>Example screen</p>
      </div>
    </div>
  );
}

export function VoiceMaskingDemo() {
  const [playCount, setPlayCount] = useState(0);
  const [playing, setPlaying] = useState(false);

  useEffect(() => {
    if (!playing) return;
    const timer = window.setTimeout(() => setPlaying(false), 4800);
    return () => window.clearTimeout(timer);
  }, [playing, playCount]);

  function togglePlayback() {
    if (!playing) setPlayCount((count) => count + 1);
    setPlaying(!playing);
  }

  const stillImage = "/ongero/voice-masking-still.webp";
  const image = playing ? "/ongero/voice-masking.webp?play=" + playCount : stillImage;

  return (
    <figure className={styles.maskDemo}>
      <Image
        src={image}
        alt="An illustration of two different waveforms on either side of translucent purple layers."
        width={504}
        height={312}
        unoptimized
        onError={() => setPlaying(false)}
      />
      <figcaption>
        <span>How voice masking looks</span>
        <button type="button" onClick={togglePlayback} aria-label={playing ? "Stop voice masking animation" : "Play voice masking animation"}>
          {playing ? <Square size={15} aria-hidden="true" /> : <Play size={16} aria-hidden="true" />}
          {playing ? "Stop" : "Play"}
        </button>
      </figcaption>
    </figure>
  );
}
