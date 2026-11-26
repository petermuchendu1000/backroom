import Link from "next/link";

/**
 * One row of links, in the order the brief gives them.
 *
 * The previous footer had three columns of nine links, one of which pointed at
 * /faq — a page that does not exist. The FAQ lives on the landing page, so it is
 * linked there.
 */
const links = [
  { label: "How it works", href: "/#how-it-works" },
  { label: "Listen", href: "/#listen" },
  { label: "FAQ", href: "/#faq" },
  { label: "Urgent support", href: "/crisis" },
  { label: "Privacy", href: "/privacy" },
  { label: "Terms", href: "/terms" },
];

export default function Footer() {
  return (
    <footer className="border-t border-line" role="contentinfo">
      <div className="max-w-5xl mx-auto px-5 sm:px-8 py-14">
        <div className="flex items-center gap-2.5 mb-8">
          <img src="/ongero-mark.png" alt="" width={24} height={24} aria-hidden="true" />
          <span className="font-serif text-lg text-ink">Ongero</span>
        </div>

        <nav aria-label="Footer">
          <ul className="flex flex-wrap gap-x-7 gap-y-3 mb-10">
            {links.map((link) => (
              <li key={link.label}>
                <Link
                  href={link.href}
                  className="text-sm text-muted hover:text-ink transition-colors"
                >
                  {link.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>

        <p className="text-sm text-muted">
          © {new Date().getFullYear()} Ongero. Built in Kenya.
        </p>
        <p className="text-sm text-muted mt-2 max-w-xl">
          Ongero is peer support, not a medical or emergency service. If you are in
          immediate danger, call your local emergency services.
        </p>
      </div>
    </footer>
  );
}
