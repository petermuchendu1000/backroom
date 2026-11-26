import Link from "next/link";
import Navbar from "@/components/Navbar";
import Footer from "@/components/Footer";

/**
 * The landing page.
 *
 * The copy is the brief's, close to word for word. Two things shape the layout:
 *
 * Someone arriving here may be having a bad day and skimming. So the page is one
 * column, the headings are plain questions, and nothing moves on its own — the
 * previous version had a cursor spotlight, animated waveforms and a pulsing pill,
 * which is a lot of motion to put in front of someone who is upset.
 *
 * And it is a page about not having to perform. The old copy promised "real human
 * connection" and "a moment of connection"; this one says you can open with "I'm
 * having a hard day". The design follows that: no gradients, no glow, plenty of
 * space, and one accent colour.
 */

const STEPS = [
  {
    body: (
      <>
        Tap <strong className="font-medium text-ink">Share</strong> to talk or{" "}
        <strong className="font-medium text-ink">Listen</strong>{" "}
        {/* Explicit: JSX dropped the space between </strong> and the following text
            when the element began the line, and the page shipped "Listento hear". */}
        to hear someone out. Choose a topic, how heavy it feels, and whether
        you&rsquo;d like 5, 10, or 15 minutes.
      </>
    ),
  },
  {
    body: (
      <>
        Before you connect, you&rsquo;ll hear a few seconds of background sound from the
        other end. We call it{" "}
        <strong className="font-medium text-ink">Soft Preview</strong>. Then you decide
        if you want to join.
      </>
    ),
  },
  {
    body: (
      <>
        Once you&rsquo;re on the call, go at your own pace. If all you want is for
        someone to listen, you can say so.
      </>
    ),
  },
];

const FAQ = [
  {
    q: "Who’s on the other end?",
    a: "A volunteer listener. Ongero offers peer support. If you’re looking for therapy or treatment, a qualified mental health professional can help.",
  },
  {
    q: "Who can use Ongero?",
    a: "Anyone aged 13 or older. It’s free to use.",
  },
  {
    q: "Where can I get it?",
    a: "Ongero for Android is coming soon to Google Play.",
  },
];

/** A section heading, in the serif the app uses for the words that matter. */
function Heading({ children, id }: { children: React.ReactNode; id?: string }) {
  return (
    <h2
      id={id}
      className="font-serif text-3xl sm:text-4xl text-ink leading-tight tracking-tight"
    >
      {children}
    </h2>
  );
}

function Section({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <section className={`border-t border-line ${className}`}>
      <div className="max-w-5xl mx-auto px-5 sm:px-8 py-16 sm:py-20">{children}</div>
    </section>
  );
}

export default function Home() {
  return (
    <>
      <Navbar />
      <main>
        {/* ── Hero ───────────────────────────────────────────────────────── */}
        <div className="max-w-5xl mx-auto px-5 sm:px-8 pt-16 pb-16 sm:pt-24 sm:pb-20">
          <div className="max-w-2xl">
            <h1 className="font-serif text-4xl sm:text-5xl lg:text-6xl text-ink leading-[1.1] tracking-tight">
              Want to talk for a bit?
            </h1>
            <p className="mt-7 text-lg sm:text-xl text-muted leading-relaxed">
              Some days, you just wish someone would ask how you&rsquo;re doing and give
              you time to answer.
            </p>
            <p className="mt-5 text-lg text-muted leading-relaxed">
              On Ongero, you can spend a few minutes talking with a volunteer listener.
              Tell them what&rsquo;s on your mind, in your own words. You don&rsquo;t
              have to give your name.
            </p>

            <div className="mt-10 flex flex-col sm:flex-row gap-3 sm:gap-4">
              <Link
                href="#how-it-works"
                className="inline-flex items-center justify-center h-14 px-8 rounded-full bg-violet text-white text-base font-medium hover:bg-ink transition-colors"
              >
                See how it works
              </Link>
              <Link
                href="#listen"
                className="inline-flex items-center justify-center h-14 px-8 rounded-full border border-line text-ink text-base hover:border-violet transition-colors"
              >
                I&rsquo;d like to listen
              </Link>
            </div>

            <p className="mt-6 text-sm text-calm">Free to use. No account needed.</p>
          </div>
        </div>

        {/* ── Start with whatever comes to mind ──────────────────────────── */}
        <Section>
          <div className="max-w-2xl">
            <Heading>Start with whatever comes to mind.</Heading>
            <p className="mt-6 text-lg text-muted leading-relaxed">
              Maybe you miss someone you&rsquo;re still angry with. Maybe something good
              happened and you wish you had someone to tell. Or maybe you can only say,
              &ldquo;I&rsquo;m having a hard day.&rdquo;
            </p>
            <p className="mt-5 text-lg text-muted leading-relaxed">
              You can start there. If you need a moment to find the next words, take it.
            </p>
          </div>
        </Section>

        {/* ── How a call works ───────────────────────────────────────────── */}
        <Section>
          <Heading id="how-it-works">How a call works</Heading>
          <ol className="mt-10 space-y-8 max-w-2xl">
            {STEPS.map((step, i) => (
              <li key={i} className="flex gap-5">
                <span
                  className="shrink-0 w-9 h-9 rounded-full bg-violet-soft text-violet grid place-items-center text-sm font-medium"
                  aria-hidden="true"
                >
                  {i + 1}
                </span>
                <p className="text-lg text-muted leading-relaxed pt-1">{step.body}</p>
              </li>
            ))}
          </ol>
        </Section>

        {/* ── You choose what to share ───────────────────────────────────── */}
        <Section>
          <div className="max-w-2xl">
            <Heading id="safety">You choose what to share.</Heading>
            <p className="mt-6 text-lg text-muted leading-relaxed">
              Ongero changes how both people sound before sending their voices over the
              call. There&rsquo;s no profile to fill out, and Ongero keeps no call
              recordings.
            </p>
            <p className="mt-5 text-lg text-muted leading-relaxed">
              Keep personal details like your name or phone number to yourself. If a
              conversation makes you uncomfortable, you can block or report the other
              person in one tap.
            </p>
            <Link
              href="/privacy"
              className="mt-7 inline-block text-violet hover:underline underline-offset-4"
            >
              Read our privacy policy
            </Link>
          </div>
        </Section>

        {/* ── Have a few minutes to listen? ──────────────────────────────── */}
        <Section>
          <div className="max-w-2xl">
            <Heading id="listen">Have a few minutes to listen?</Heading>
            <p className="mt-6 text-lg text-muted leading-relaxed">
              When someone stops halfway through a sentence, give them a moment. A
              simple &ldquo;Take your time&rdquo; can be a kind thing to hear.
            </p>
            <p className="mt-5 text-lg text-muted leading-relaxed">
              You can choose which calls to accept based on the topic, how heavy it
              feels, and how long the call will last.
            </p>
            <Link
              href="/volunteer"
              className="mt-7 inline-block text-violet hover:underline underline-offset-4"
            >
              Learn about being a listener
            </Link>
          </div>
        </Section>

        {/* ── A few questions ────────────────────────────────────────────── */}
        <Section>
          <Heading id="faq">A few questions</Heading>
          <dl className="mt-10 max-w-2xl divide-y divide-line border-t border-line">
            {FAQ.map((item) => (
              <div key={item.q} className="py-7">
                <dt className="text-lg text-ink">{item.q}</dt>
                <dd className="mt-3 text-lg text-muted leading-relaxed">{item.a}</dd>
              </div>
            ))}
          </dl>
        </Section>

        {/* ── Need help right now? ───────────────────────────────────────── */}
        <Section className="bg-violet-soft/40">
          <div className="max-w-2xl">
            <Heading>Need help right now?</Heading>
            <p className="mt-6 text-lg text-muted leading-relaxed">
              If you&rsquo;re in immediate danger, call your local emergency services.
            </p>
            <Link
              href="/crisis"
              className="mt-8 inline-flex items-center justify-center h-14 px-8 rounded-full border border-danger text-danger text-base font-medium hover:bg-danger hover:text-white transition-colors"
            >
              Find urgent support
            </Link>
          </div>
        </Section>
      </main>
      <Footer />
    </>
  );
}
