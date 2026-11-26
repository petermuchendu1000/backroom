import Link from "next/link";
import styles from "@/app/landing.module.css";

export default function Footer() {
  return (
    <footer className={styles.footer}>
      <div className={styles.wrap}>
        <div className={styles.footerTop}>
          <Link href="/" className={styles.brand} aria-label="Ongero home">
            <span className={styles.brandMark} aria-hidden="true"><i /><i /><i /><i /><i /></span>
            Ongero
          </Link>
          <nav aria-label="Footer navigation">
            <Link href="/#how-it-works">How it works</Link>
            <Link href="/faq">FAQ</Link>
            <Link href="/volunteer">Volunteer</Link>
            <Link href="/privacy">Privacy</Link>
            <Link href="/terms">Terms</Link>
            <Link href="/crisis">Urgent help</Link>
          </nav>
        </div>
        <p>© {new Date().getFullYear()} Ongero. Built in Kenya.</p>
      </div>
    </footer>
  );
}
