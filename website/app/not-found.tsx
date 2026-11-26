import Link from "next/link";
import AnimatedArt from "@/components/AnimatedArt";
import site from "./landing.module.css";
import styles from "./supporting.module.css";

export default function NotFound() {
  return (
    <div className={styles.notFound}>
      <div className={site.wrap}>
        <div className={styles.policyHeader}>
          <div>
            <h1 className={styles.pageTitle}>We couldn’t find that page.</h1>
            <p className={styles.lead}>You can head back to Ongero or find urgent support here.</p>
            <div className={site.actions}>
              <Link href="/" className={site.primary}>Back to Ongero</Link>
              <Link href="/crisis" className={site.textLink}>Find urgent support</Link>
            </div>
          </div>
          <AnimatedArt asset="quiet-landing" className={styles.policyArt} />
        </div>
      </div>
    </div>
  );
}
