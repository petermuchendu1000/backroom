"use client";

import { useState } from "react";
import Link from "next/link";

/**
 * Links follow the order of the page itself, so the nav reads as a table of
 * contents rather than a menu of features.
 */
const navLinks = [
  { href: "/#how-it-works", label: "How it works" },
  { href: "/#listen", label: "Listen" },
  { href: "/#faq", label: "FAQ" },
];

export default function Navbar() {
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <header
      className="sticky top-0 z-50 bg-paper/90 backdrop-blur-sm border-b border-line"
      role="banner"
    >
      <nav
        className="max-w-5xl mx-auto px-5 sm:px-8 h-16 flex items-center justify-between"
        aria-label="Main"
      >
        <Link href="/" className="flex items-center gap-2.5" aria-label="Ongero home">
          {/* The app's own mark. Someone who installs after reading this should
              recognise the icon on their home screen. */}
          <img src="/ongero-mark.png" alt="" width={28} height={28} aria-hidden="true" />
          <span className="font-serif text-xl text-ink">Ongero</span>
        </Link>

        <div className="hidden sm:flex items-center gap-8">
          {navLinks.map((link) => (
            <Link
              key={link.label}
              href={link.href}
              className="text-sm text-muted hover:text-ink transition-colors"
            >
              {link.label}
            </Link>
          ))}
          {/* Urgent help is never behind a menu, and it is the one link that gets
              colour, so it can be found without reading. */}
          <Link
            href="/crisis"
            className="text-sm font-medium text-danger hover:underline underline-offset-4"
          >
            Urgent support
          </Link>
        </div>

        <button
          onClick={() => setMobileOpen(!mobileOpen)}
          className="sm:hidden -mr-2 p-2 text-ink"
          aria-label={mobileOpen ? "Close menu" : "Open menu"}
          aria-expanded={mobileOpen}
          aria-controls="mobile-menu"
        >
          <span className="block w-5 space-y-1.5" aria-hidden="true">
            <span className="block h-px bg-ink" />
            <span className="block h-px bg-ink" />
            <span className="block h-px bg-ink" />
          </span>
        </button>
      </nav>

      {mobileOpen && (
        <div id="mobile-menu" className="sm:hidden border-t border-line bg-paper">
          <div className="px-5 py-4 flex flex-col">
            {navLinks.map((link) => (
              <Link
                key={link.label}
                href={link.href}
                onClick={() => setMobileOpen(false)}
                className="py-3 text-muted hover:text-ink transition-colors"
              >
                {link.label}
              </Link>
            ))}
            <Link
              href="/crisis"
              onClick={() => setMobileOpen(false)}
              className="py-3 font-medium text-danger"
            >
              Urgent support
            </Link>
          </div>
        </div>
      )}
    </header>
  );
}
