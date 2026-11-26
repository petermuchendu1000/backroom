import type { Metadata } from "next";
import { Heart } from "lucide-react";
import { RegionResources } from "@/components/SupportingInteractions";
import site from "../landing.module.css";
import styles from "../supporting.module.css";

export const metadata: Metadata = {
  title: "Urgent Support",
  description: "Find urgent support and crisis resources for your country or region.",
  alternates: { canonical: "/crisis" },
};

const resources = [
  {
    region: "United States",
    lines: [
      { name: "988 Suicide & Crisis Lifeline", contact: "Call or text 988", url: "https://988lifeline.org" },
      { name: "Crisis Text Line", contact: "Text HOME to 741741", url: "https://www.crisistextline.org" },
      { name: "SAMHSA National Helpline", contact: "1-800-662-4357", url: "https://www.samhsa.gov/find-help/national-helpline" },
      { name: "Trevor Project (LGBTQ+)", contact: "1-866-488-7386", url: "https://www.thetrevorproject.org" },
    ],
  },
  {
    region: "Kenya",
    lines: [
      { name: "Befrienders Kenya", contact: "+254 722 178 177", url: "https://www.befrienderskenya.org" },
      { name: "Kenya Red Cross", contact: "1199", url: "https://www.redcross.or.ke" },
    ],
  },
  {
    region: "United Kingdom",
    lines: [
      { name: "Samaritans", contact: "116 123 (free, 24/7)", url: "https://www.samaritans.org" },
      { name: "SHOUT", contact: "Text SHOUT to 85258", url: "https://giveusashout.org" },
    ],
  },
  {
    region: "International",
    lines: [
      { name: "International Association for Suicide Prevention", contact: "Find your country", url: "https://www.iasp.info/resources/Crisis_Centres/" },
      { name: "Befrienders Worldwide", contact: "Find your country", url: "https://www.befrienders.org" },
    ],
  },
];

export default function CrisisPage() {
  return (
    <div className={styles.crisisPage}>
      <div className={site.wrap}>
        <div className={styles.crisisIntro}>
          <span className={styles.crisisIcon}><Heart size={25} aria-hidden="true" /></span>
          <h1 className={styles.pageTitle}>Need help right now?</h1>
          <p className={styles.emergency}>If you’re in immediate danger, call your local emergency services.</p>
          <p className={styles.lead}>If you need urgent emotional support, choose your country or region below to find help.</p>
        </div>
        <RegionResources regions={resources} />
      </div>
    </div>
  );
}
