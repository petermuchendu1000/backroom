import type { Metadata } from "next";
import PolicyDocument from "@/components/PolicyDocument";

export const metadata: Metadata = {
  title: "Privacy Policy",
  description: "How Ongero handles your voice and the information used to run the service.",
  alternates: { canonical: "/privacy" },
};

export default function PrivacyPage() {
  return (
    <PolicyDocument
      title="Privacy Policy"
      summary="How Ongero handles your voice and the information used to run the service."
      artwork="voice-masking"
      updated="2026-04-18"
      sections={[
        {
          id: "approach",
          title: "Our Core Principle",
          content: (
            <>
              <p>
                Ongero is built on the principle of <strong>zero knowledge</strong>.
                We cannot identify you because we never collect identifying information
                in the first place.
              </p>
            </>
          ),
        },
        {
          id: "data-we-dont-collect",
          title: "What We Do NOT Collect",
          content: (
            <>
              <ul>
                <li>Names, email addresses, or phone numbers</li>
                <li>Profile photos or biometric data</li>
                <li>IP addresses (not logged)</li>
                <li>Voice recordings (calls are never recorded or stored)</li>
                <li>Chat transcripts (none exist)</li>
                <li>Location data</li>
                <li>Device identifiers for tracking</li>
              </ul>
            </>
          ),
        },
        {
          id: "data-we-collect",
          title: "What We Do Collect",
          content: (
            <>
              <ul>
                <li>Anonymous session tokens (temporary, for connection matching)</li>
                <li>Aggregate, non-identifying usage statistics (e.g., total calls made, average duration)</li>
                <li>Crash reports (anonymized, no personal data)</li>
              </ul>
            </>
          ),
        },
        {
          id: "voice-masking",
          title: "Voice Anonymization",
          content: (
            <>
              <p>
                All voice data is processed <strong>on your device</strong> using
                real-time voice masking before any audio is transmitted to the other
                party. We never receive, process, or store your original voice.
              </p>
            </>
          ),
        },
        {
          id: "retention",
          title: "Data Retention",
          content: (
            <>
              <p>
                Session tokens expire when a call ends. There is no persistent user
                data to retain or delete.
              </p>
            </>
          ),
        },
        {
          id: "cookies",
          title: "Cookies",
          content: (
            <>
              <p>
                Our website uses only essential cookies required for the site to
                function. We do not use advertising or tracking cookies.
              </p>
            </>
          ),
        },
        {
          id: "third-party-services",
          title: "Third-Party Services",
          content: (
            <>
              <p>
                We use Firebase Cloud Messaging for optional push notifications. No
                personal data is shared with Firebase beyond anonymous device tokens.
              </p>
            </>
          ),
        },
        {
          id: "eligibility",
          title: "Children",
          content: (
            <>
              <p>
                Ongero is not intended for users under 13. We do not knowingly
                collect data from children.
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
                Questions about privacy? Email{" "}
                <a href="mailto:privacy@ongero.com">
                  privacy@ongero.com
                </a>
              </p>
            </>
          ),
        },
      ]}
    />
  );
}
