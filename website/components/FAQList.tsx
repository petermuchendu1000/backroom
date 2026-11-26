import styles from "@/app/landing.module.css";

export default function FAQList({ questions }: { questions: { question: string; answer: string }[] }) {
  return (
    <div className={styles.faqItems}>
      {questions.map(({ question, answer }) => (
        <details key={question}>
          <summary>{question}<span aria-hidden="true" /></summary>
          <p>{answer}</p>
        </details>
      ))}
    </div>
  );
}
