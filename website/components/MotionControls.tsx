"use client";

import { createContext, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";
import styles from "@/app/landing.module.css";

export const MotionContext = createContext({ paused: false, toggle: () => {} });

export function MotionProvider({ children }: { children: ReactNode }) {
  const [paused, setPaused] = useState(false);

  useEffect(() => {
    document.documentElement.toggleAttribute("data-motion-paused", paused);
  }, [paused]);

  return (
    <MotionContext.Provider value={{ paused, toggle: () => setPaused((value) => !value) }}>
      {children}
    </MotionContext.Provider>
  );
}

export function MotionToggle() {
  const { paused, toggle } = useContext(MotionContext);
  return (
    <button type="button" className={styles.motionToggle} onClick={toggle}>
      {paused ? "Resume animations" : "Pause animations"}
    </button>
  );
}
