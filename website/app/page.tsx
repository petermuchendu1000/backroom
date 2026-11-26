import type { Metadata } from "next";
import Image from "next/image";
import Link from "next/link";
import { Check, Headphones, Heart, Lock, Shield, Smartphone } from "lucide-react";
import { CallWalkthrough, VoiceMaskingDemo } from "@/components/LandingDemos";
import FAQList from "@/components/FAQList";
import { questions } from "./faq-content";
import styles from "./landing.module.css";

const description =
  "Talk for a few minutes with a volunteer listener on Ongero. Share what’s on your mind, at your own pace. No account needed.";

export const metadata: Metadata = {
  title: { absolute: "Ongero | Someone to Talk To" },
  description,
  keywords: ["someone to talk to", "anonymous voice support", "peer support", "volunteer listener"],
  alternates: { canonical: "https://ongero.com/" },
  openGraph: {
    title: "Ongero | Someone to Talk To",
    description,
    url: "https://ongero.com/",
    siteName: "Ongero",
    type: "website",
    images: ["https://ongero.com/ongero/someone-to-talk-to.webp"],
  },
  twitter: {
    card: "summary_large_image",
    title: "Ongero | Someone to Talk To",
    description,
    images: ["https://ongero.com/ongero/someone-to-talk-to.webp"],
  },
};

const conversationStarters = [
  "I miss someone I’m still upset with.",
  "Something good happened, and I wanted to tell someone.",
  "I’ve had a hard day. Can you just listen for a bit?",
];

const privacyDetails = [
  {
    icon: Lock,
    title: "Your voice is masked",
    copy: "Ongero changes how your voice sounds before sending it. You choose which personal details to share.",
  },
  {
    icon: Headphones,
    title: "Ongero keeps no call recordings",
    copy: "Our privacy policy explains the limited data we use to run the service.",
  },
  {
    icon: Shield,
    title: "You can set a boundary",
    copy: "If someone makes you uncomfortable, you can block or report them in the app.",
  },
];

function Waveform() {
  return (
    <span className={styles.waveform} aria-hidden="true">
      <i /><i /><i /><i /><i /><i /><i />
    </span>
  );
}

export default function Home() {
  return (
    <>
      <section className={styles.hero} aria-labelledby="hero-title">
        <div className={styles.wrap}>
          <div className={styles.heroBody}>
            <div className={styles.heroCopy}>
              <p className={styles.eyebrow}>Anonymous voice support</p>
              <h1 id="hero-title">Want someone to <em>listen for a bit?</em></h1>
              <p className={styles.heroIntro}>
                Talk for a few minutes with a volunteer listener. Start wherever you can.
                Share as much or as little as you like.
              </p>

              <div className={styles.actions}>
                <a className={styles.primary} href="#how-it-works">See what a call is like</a>
                <a className={styles.textLink} href="#listen">I’d like to listen</a>
              </div>

              <ul className={styles.trust}>
                <li><Check size={17} aria-hidden="true" /> Free voice support</li>
                <li><Check size={17} aria-hidden="true" /> No account needed</li>
              </ul>
            </div>

            <figure className={styles.heroVisual}>
              <Image
                src="/ongero/someone-to-talk-to.webp"
                alt="A woman taking a quiet moment to talk on her phone by a window."
                fill
                loading="eager"
                fetchPriority="high"
                sizes="(max-width: 780px) 94vw, (max-width: 1230px) 49vw, 620px"
              />
              <figcaption className={styles.callCaption}>
                <span className={styles.callCaptionIcon}><Waveform /></span>
                <span className={styles.captionText}><small>Example Ongero call</small><strong>Take your time.</strong></span>
              </figcaption>
            </figure>
          </div>
        </div>
      </section>

      <section className={styles.starters} aria-labelledby="starters-title">
        <div className={styles.wrap}>
          <div className={styles.startersHeading}>
            <h2 id="starters-title">You can start with one sentence.</h2>
            <p>If you need a moment to find the next words, take it.</p>
          </div>
          <ul className={styles.quoteGrid}>
            {conversationStarters.map((starter) => (
              <li key={starter}><p>“{starter}”</p></li>
            ))}
          </ul>
        </div>
      </section>

      <section id="how-it-works" className={styles.walkthrough} aria-labelledby="how-title">
        <div className={styles.wrap}>
          <p className={styles.eyebrow}>How it works</p>
          <h2 id="how-title">A few minutes, on your terms.</h2>
          <CallWalkthrough />
        </div>
      </section>

      <section id="safety" className={styles.safety} aria-labelledby="safety-title">
        <div className={styles.wrap}>
          <div className={styles.safetyGrid}>
            <div>
              <p className={styles.eyebrow}>Privacy &amp; safety</p>
              <h2 id="safety-title">Some things can stay yours.</h2>
              <ul className={styles.privacyList}>
                {privacyDetails.map(({ icon: Icon, title, copy }) => (
                  <li key={title}>
                    <span className={styles.privacyIcon}><Icon size={19} aria-hidden="true" /></span>
                    <div><h3>{title}</h3><p>{copy}</p></div>
                  </li>
                ))}
              </ul>
              <Link href="/privacy" className={styles.textLink}>Read the privacy policy</Link>
            </div>
            <VoiceMaskingDemo />
          </div>
        </div>
      </section>

      <section id="listen" className={styles.listen} aria-labelledby="listen-title">
        <div className={styles.wrap}>
          <div className={styles.listenGrid}>
            <figure className={styles.listenPhoto}>
              <Image
                src="/ongero/room-to-listen.webp"
                alt="A man wearing an earbud listens quietly by a window."
                fill
                sizes="(max-width: 780px) 94vw, (max-width: 1230px) 46vw, 570px"
              />
            </figure>
            <div className={styles.listenCopy}>
              <p className={styles.eyebrow}>For listeners</p>
              <h2 id="listen-title">Have a few minutes to listen?</h2>
              <p>
                Sometimes the kindest thing you can say is, “Take your time. I’m listening.”
                Give the other person a moment to finish their thought.
              </p>
              <p>Choose the conversations you feel ready for, when you have time to give.</p>
              <Link className={styles.secondary} href="/volunteer">Learn about being a listener</Link>
            </div>
          </div>
        </div>
      </section>

      <section id="faq" className={styles.faq} aria-labelledby="faq-title">
        <div className={styles.wrap}>
          <div className={styles.faqGrid}>
            <div>
              <h2 id="faq-title">A few questions.</h2>
              <Link href="/faq" className={styles.textLink}>View all questions</Link>
            </div>
            <FAQList questions={questions.slice(0, 4)} />
          </div>
        </div>
      </section>

      <section className={styles.launch} id="download" aria-labelledby="download-title">
        <div className={styles.wrap}>
          <div className={styles.launchCard}>
            <span className={styles.launchIcon}><Smartphone size={27} aria-hidden="true" /></span>
            <div>
              <h2 id="download-title">Get Ongero for Android.</h2>
              <p>Coming soon to Google Play.</p>
            </div>
          </div>
          <div className={styles.supportStrip}>
            <Heart size={21} aria-hidden="true" />
            <div><strong>Need help right now?</strong><p>Find urgent support for where you live.</p></div>
            <Link href="/crisis" className={styles.textLink}>Find urgent support</Link>
          </div>
        </div>
      </section>
    </>
  );
}
