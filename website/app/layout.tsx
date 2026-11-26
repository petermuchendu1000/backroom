import type { Metadata, Viewport } from "next";
import "@fontsource/inter/latin-400.css";
import "@fontsource/inter/latin-500.css";
import "@fontsource/inter/latin-600.css";
import "@fontsource/inter/latin-700.css";
import "@fontsource/lora/latin-500.css";
import "@fontsource/lora/latin-500-italic.css";
import Navbar from "@/components/Navbar";
import Footer from "@/components/Footer";
import styles from "./landing.module.css";
import "./globals.css";

export const viewport: Viewport = { themeColor: "#f8f4fb" };

export const metadata: Metadata = {
  metadataBase: new URL("https://ongero.com"),
  title: { default: "Ongero | Someone to Talk To", template: "%s | Ongero" },
  description: "Talk for a few minutes with a volunteer listener. Share what’s on your mind, at your own pace.",
  openGraph: { type: "website", siteName: "Ongero", locale: "en_KE" },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body className={styles.landing}>
        <a className={styles.skipLink} href="#main-content">Skip to content</a>
        <Navbar />
        <main id="main-content" className={styles.siteMain} tabIndex={-1}>{children}</main>
        <Footer />
      </body>
    </html>
  );
}
