import type { Metadata } from "next";
import Image from "next/image";
import Link from "next/link";
import { Ear, Heart, MessageCircle } from "lucide-react";
import AnimatedArt from "@/components/AnimatedArt";
import { ListenerPractice } from "@/components/SupportingInteractions";
import site from "../landing.module.css";
import styles from "../supporting.module.css";

export const metadata: Metadata = {
  title: "Volunteer to Listen",
  description: "Learn how to give someone a few minutes to talk on Ongero, and try a few simple ways to respond.",
  alternates: { canonical: "/volunteer" },
};

const steps = [
  { icon: Ear, title: "Choose Listen", copy: "Switch to Listen mode when you have a little time and somewhere you can focus." },
  { icon: MessageCircle, title: "Read the preview", copy: "You’ll see the topic, tone, and call length. Choose a conversation you feel ready for." },
  { icon: Heart, title: "Hear them out", copy: "Give them room to finish. Ask before offering advice and let them decide how much to share." },
];

export default function VolunteerPage() {
  return (
    <>
      <header className={styles.pageHeader}>
        <div className={site.wrap}>
          <div className={styles.splitHeader}>
            <div>
              <p className={site.eyebrow}>For volunteer listeners</p>
              <h1 className={styles.pageTitle}>Sometimes listening <em>is enough.</em></h1>
              <p className={styles.lead}>When someone is finding the words, give them time. A patient “I’m listening” can make it easier to finish a thought.</p>
              <a href="#listening-examples" className={site.primary}>Try a few ways to respond</a>
            </div>
            <div className={styles.listenerPortrait}>
              <Image src="/ongero/room-to-listen.webp" alt="A man wearing an earbud listening quietly by a window." fill sizes="(max-width: 760px) 94vw, 50vw" fetchPriority="high" />
            </div>
          </div>
        </div>
      </header>

      <section id="listening-examples" className={styles.softSection} aria-labelledby="practice-title">
        <div className={site.wrap}>
          <h2 id="practice-title" className={styles.sectionTitle}>What could I say?</h2>
          <p className={styles.lead}>Choose a moment to see one way to respond. Use words that feel natural to you.</p>
          <ListenerPractice />
        </div>
      </section>

      <section className={styles.pageSection} aria-labelledby="volunteer-steps">
        <div className={site.wrap}>
          <div className={styles.volunteerSteps}>
            <div>
              <h2 id="volunteer-steps" className={styles.sectionTitle}>When you’re ready to listen.</h2>
              <ul className={styles.iconList}>
                {steps.map(({ icon: Icon, title, copy }) => (
                  <li key={title}><span><Icon size={21} aria-hidden="true" /></span><div><h3>{title}</h3><p>{copy}</p></div></li>
                ))}
              </ul>
              <Link href="/#download" className={site.secondary}>Ongero for Android</Link>
            </div>
            <AnimatedArt asset="two-voices" className={styles.listenerArt} />
          </div>
        </div>
      </section>

      <section className={styles.boundarySection} aria-labelledby="boundary-title">
        <div className={site.wrap}>
          <div className={styles.boundaryCard}>
            <h2 id="boundary-title">Look after yourself, too.</h2>
            <p>Volunteers offer a listening ear. Leave diagnosis and treatment to professionals. If a call becomes too much for you, it’s okay to end it. You can point someone who needs urgent help to the support resources.</p>
            <Link href="/crisis" className={site.textLink}>Find urgent support</Link>
          </div>
        </div>
      </section>
    </>
  );
}
