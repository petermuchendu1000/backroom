import type { Metadata } from "next";
import Link from "next/link";
import PolicyDocument from "@/components/PolicyDocument";

export const metadata: Metadata = {
  title: "Terms of Service",
  description: "What to expect from Ongero and how to treat each other on a call.",
  alternates: { canonical: "/terms" },
};

export default function TermsPage() {
  return (
    <PolicyDocument
      title="Terms of Service"
      summary="What to expect from Ongero and how to treat each other on a call."
      artwork="quiet-landing"
      updated="2026-04-18"
      sections={[
        {
          id: "about",
          title: "What Ongero Is",
          content: (
            <>
              <p>
                Ongero is a peer-to-peer anonymous voice support application. It
                connects people who want to talk with people who want to listen, using
                real-time voice anonymization technology.
              </p>
            </>
          ),
        },
        {
          id: "scope",
          title: "What Ongero Is NOT",
          content: (
            <>
              <ul>
                <li>A crisis intervention service</li>
                <li>A therapy or counseling platform</li>
                <li>A medical service of any kind</li>
                <li>A substitute for professional mental health treatment</li>
              </ul>
            </>
          ),
        },
        {
          id: "eligibility",
          title: "Eligibility",
          content: (
            <>
              <p>You must be at least 13 years old to use Ongero.</p>
            </>
          ),
        },
        {
          id: "acceptable-use",
          title: "Acceptable Use",
          content: (
            <>
              <p>You agree NOT to:</p>
              <ul>
                <li>Attempt to identify, dox, or track other users</li>
                <li>Record, screenshot, or share any conversation</li>
                <li>Harass, threaten, or bully other users</li>
                <li>Share sexually explicit content</li>
                <li>Promote illegal activities</li>
                <li>Attempt to bypass voice anonymization</li>
              </ul>
            </>
          ),
        },
        {
          id: "moderation",
          title: "Safety & Moderation",
          content: (
            <>
              <p>
                Users can block and report other users with one tap. Reports are
                reviewed and may result in temporary or permanent bans. We reserve the
                right to terminate access for any violation.
              </p>
            </>
          ),
        },
        {
          id: "liability",
          title: "Disclaimer of Liability",
          content: (
            <>
              <p>
                Ongero is provided &quot;as is.&quot; We make no guarantees about the
                quality, accuracy, or safety of conversations. Listeners are
                volunteers, not trained professionals. Use at your own discretion.
              </p>
            </>
          ),
        },
        {
          id: "privacy",
          title: "Privacy",
          content: (
            <>
              <p>
                See our <Link href="/privacy">Privacy Policy</Link> for
                details on how we handle (or rather, don&apos;t handle) your data.
              </p>
            </>
          ),
        },
        {
          id: "contact",
          title: "Contact",
          content: (
            <>
              <p>
                Questions?{" "}
                <a href="mailto:legal@ongero.com">
                  legal@ongero.com
                </a>
              </p>
            </>
          ),
        },
      ]}
    />
  );
}
