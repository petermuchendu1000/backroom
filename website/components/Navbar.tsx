"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Heart, Menu, X } from "lucide-react";
import styles from "@/app/landing.module.css";

const navigation = [
  { href: "#how-it-works", label: "How it works" },
  { href: "#safety", label: "Privacy & safety" },
  { href: "#listen", label: "For listeners" },
];

export default function Navbar() {
  const [open, setOpen] = useState(false);
  const menuButton = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;

    function closeOnEscape(event: KeyboardEvent) {
      if (event.key !== "Escape") return;
      setOpen(false);
      menuButton.current?.focus();
    }

    document.addEventListener("keydown", closeOnEscape);
    return () => document.removeEventListener("keydown", closeOnEscape);
  }, [open]);

  return (
    <header className={styles.header}>
      <div className={styles.wrap}>
        <nav aria-label="Main navigation" className={styles.nav}>
          <Link href="/" className={styles.brand} aria-label="Ongero home" onClick={() => setOpen(false)}>
            <span className={styles.brandMark} aria-hidden="true"><i /><i /><i /><i /><i /></span>
            Ongero
          </Link>
          <div className={styles.desktopNav}>
            {navigation.map(({ href, label }) => <a key={href} href={href}>{label}</a>)}
          </div>
          <div className={styles.navActions}>
            <Link href="/crisis" className={styles.urgent}>
              <Heart size={16} aria-hidden="true" /> <span>Urgent help</span>
            </Link>
            <button
              type="button"
              className={styles.menuButton}
              ref={menuButton}
              aria-expanded={open}
              aria-controls="mobile-nav"
              aria-label={open ? "Close menu" : "Open menu"}
              onClick={() => setOpen((previous) => !previous)}
            >
              {open ? <X size={23} aria-hidden="true" /> : <Menu size={23} aria-hidden="true" />}
            </button>
          </div>
          <div className={styles.mobileNav} id="mobile-nav" hidden={!open}>
            {navigation.map(({ href, label }) => (
              <a key={href} href={href} onClick={() => setOpen(false)}>{label}</a>
            ))}
          </div>
        </nav>
      </div>
    </header>
  );
}
