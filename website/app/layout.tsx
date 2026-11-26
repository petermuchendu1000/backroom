import type { Metadata } from "next";
import { Inter, Lora } from "next/font/google";
import "./globals.css";

const inter = Inter({
  subsets: ["latin"],
  variable: "--font-inter",
  display: "swap",
});

const lora = Lora({
  subsets: ["latin"],
  variable: "--font-lora",
  display: "swap",
});

export const metadata: Metadata = {
  metadataBase: new URL("https://ongero.com"),
  title: {
    default: "Ongero | Someone to Talk To",
    template: "%s | Ongero",
  },
  description:
    "Talk with a volunteer listener on a short, anonymous voice call. Share what's on your mind, at your own pace. Free to use, with no account needed.",
  // The previous list ran to 34 keywords, including "cant afford therapy" and
  // "midnight someone to call". Search engines have ignored this tag for years, and
  // aiming copy at those phrases shapes the page around desperation rather than around
  // what Ongero is. The page's own words do the work now.
  openGraph: {
    title: "Ongero | Someone to Talk To",
    description:
      "Talk with a volunteer listener on a short, anonymous voice call. Share what's on your mind, at your own pace. Free to use, with no account needed.",
    type: "website",
    url: "https://ongero.com",
    siteName: "Ongero",
    locale: "en_US",
  },
  twitter: {
    card: "summary_large_image",
    title: "Ongero | Someone to Talk To",
    description:
      "Talk with a volunteer listener on a short, anonymous voice call. Share what's on your mind, at your own pace.",
  },
  robots: { index: true, follow: true },
  alternates: { canonical: "https://ongero.com" },
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" className={`${inter.variable} ${lora.variable}`}>
      <head>
        <meta name="theme-color" content="#F7F4F0" />
      </head>
      <body className="antialiased bg-paper text-ink font-sans">{children}</body>
    </html>
  );
}
