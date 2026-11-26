import type { Metadata } from "next";
import Link from "next/link";
import AnimatedArt from "@/components/AnimatedArt";
import { SearchableFAQ } from "@/components/SupportingInteractions";
import site from "../landing.module.css";
import styles from "../supporting.module.css";

export const metadata: Metadata = {
  title: "Frequently Asked Questions",
  description: "Find out who answers an Ongero call, how the preview works, and what you can keep private.",
  alternates: { canonical: "/faq" },
};

export default function FAQPage() {
  return (
    <>
      <header className={styles.pageHeader}>
        <div className={site.wrap}>
          <div className={styles.policyHeader}>
            <div>
              <h1 className={styles.pageTitle}>A few things you might be wondering.</h1>
              <p className={styles.lead}>Find out what happens before you join a call and what you can choose to share.</p>
            </div>
            <AnimatedArt asset="room-to-talk" className={styles.policyArt} />
          </div>
        </div>
      </header>

      <section className={styles.pageSection} aria-label="Frequently asked questions">
        <div className={site.wrap}>
          <div className={styles.questionLayout}>
            <SearchableFAQ />
            <aside className={styles.questionAside}>
              <h2>Want to see how it works?</h2>
              <p>Walk through a sample call at your own pace.</p>
              <Link href="/#how-it-works" className={site.textLink}>See what a call is like</Link>
            </aside>
          </div>
        </div>
      </section>
    </>
  );
}
