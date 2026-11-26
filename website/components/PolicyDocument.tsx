import type { ReactNode } from "react";
import AnimatedArt from "./AnimatedArt";
import site from "@/app/landing.module.css";
import styles from "@/app/supporting.module.css";

type PolicySection = { id: string; title: string; content: ReactNode };

export default function PolicyDocument({
  title,
  summary,
  artwork,
  updated,
  sections,
}: {
  title: string;
  summary: string;
  artwork: "voice-masking" | "quiet-landing";
  updated: string;
  sections: PolicySection[];
}) {
  const updateDate = new Intl.DateTimeFormat("en", { dateStyle: "long", timeZone: "UTC" }).format(new Date(updated));

  return (
    <article>
      <header className={styles.pageHeader}>
        <div className={site.wrap}>
          <div className={styles.policyHeader}>
            <div>
              <h1 className={styles.pageTitle}>{title}</h1>
              <p className={styles.lead}>{summary}</p>
              <p className={styles.updated}>Last updated <time dateTime={updated}>{updateDate}</time></p>
            </div>
            <AnimatedArt asset={artwork} className={styles.policyArt} />
          </div>
        </div>
      </header>
      <div className={site.wrap}>
        <div className={styles.documentLayout}>
          <nav className={styles.contents} aria-label="On this page">
            <p>On this page</p>
            <ul>
              {sections.map(({ id, title }) => <li key={id}><a href={"#" + id}>{title}</a></li>)}
            </ul>
          </nav>
          <div>
            {sections.map(({ id, title, content }) => (
              <section key={id} id={id} className={styles.documentSection}>
                <h2>{title}</h2>
                {content}
              </section>
            ))}
          </div>
        </div>
      </div>
    </article>
  );
}
