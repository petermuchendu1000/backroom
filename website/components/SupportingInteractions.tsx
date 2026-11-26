"use client";

import { useState } from "react";
import { Search } from "lucide-react";
import FAQList from "./FAQList";
import { questions } from "@/app/faq-content";
import styles from "@/app/supporting.module.css";

const listeningExamples = [
  {
    label: "When words are hard to find",
    caller: "I don’t know where to start.",
    reply: "Take your time. You can start anywhere.",
  },
  {
    label: "When someone wants to talk it through",
    caller: "I keep going over what happened.",
    reply: "Would you like me to just listen, or would it help if I asked a few questions?",
  },
  {
    label: "When they’re ready to go",
    caller: "I think that’s enough for now.",
    reply: "Of course. Thanks for telling me what you felt ready to share.",
  },
];

export function ListenerPractice() {
  const [selected, setSelected] = useState(0);
  const example = listeningExamples[selected];

  return (
    <div className={styles.practiceGrid}>
      <ul className={styles.practiceChoices} aria-label="Listening situations">
        {listeningExamples.map(({ label }, index) => (
          <li key={label}>
            <button
              type="button"
              aria-pressed={selected === index}
              aria-controls="listener-example"
              onClick={() => setSelected(index)}
            >
              {label}
            </button>
          </li>
        ))}
      </ul>
      <div className={styles.practicePanel} id="listener-example" aria-live="polite">
        <div className={styles.practiceContent} key={example.label}>
          <p>They might say</p>
          <blockquote>“{example.caller}”</blockquote>
          <p>You could say</p>
          <blockquote>“{example.reply}”</blockquote>
        </div>
      </div>
    </div>
  );
}

export function SearchableFAQ() {
  const [query, setQuery] = useState("");
  const filter = query.trim().toLowerCase();
  const matching = questions.filter(({ question, answer }) =>
    (question + " " + answer).toLowerCase().includes(filter),
  );

  return (
    <div className={styles.questionSearch}>
      <label htmlFor="faq-search">Search questions</label>
      <div className={styles.searchControl}>
        <Search size={19} aria-hidden="true" />
        <input
          id="faq-search"
          type="search"
          placeholder="Try “privacy” or “listener”"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
      </div>
      <p className={styles.resultCount} role="status">
        {matching.length === 0 ? "No questions found. Try a different word." :
          matching.length + (matching.length === 1 ? " question" : " questions")}
      </p>
      {matching.length > 0 && <FAQList questions={matching} />}
    </div>
  );
}

type SupportRegion = {
  region: string;
  lines: { name: string; contact: string; url: string }[];
};

export function RegionResources({ regions }: { regions: SupportRegion[] }) {
  const [selected, setSelected] = useState("");
  const visible = selected ? regions.filter(({ region }) => region === selected) : regions;

  return (
    <div className={styles.resourceDirectory}>
      <div className={styles.regionFilter}>
        <label htmlFor="support-region">Choose a country or region</label>
        <select id="support-region" value={selected} onChange={(event) => setSelected(event.target.value)}>
          <option value="">All regions</option>
          {regions.map(({ region }) => <option key={region}>{region}</option>)}
        </select>
      </div>
      <div className={styles.resourceGrid}>
        {visible.map(({ region, lines }) => (
          <section key={region} className={styles.resourceGroup} aria-label={region}>
            <h2>{region}</h2>
            <ul>
              {lines.map(({ name, contact, url }) => (
                <li key={name}>
                  <a href={url} target="_blank" rel="noopener noreferrer">
                    <span><strong>{name}</strong><span>{contact}</span></span>
                    <span className={styles.resourceAction}>Visit website</span>
                  </a>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </div>
    </div>
  );
}
