import type { Metadata, Viewport } from "next";
import { Space_Grotesk } from "next/font/google";
import { AppProviders } from "@/components/providers/app-providers";
import { PwaRegistration } from "@/components/pwa/pwa-registration";
import "./globals.css";

const displayFont = Space_Grotesk({
  subsets: ["latin"],
  variable: "--sg-font-display",
  display: "swap",
});

export const metadata: Metadata = {
  title: "SpecGraph Foundry",
  description: "Turn source documents into verified, execution-ready plans.",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  colorScheme: "dark light",
  themeColor: "#07100d",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" data-theme="dark" className={displayFont.variable}>
      <body>
        <AppProviders>
          {children}
          <PwaRegistration />
        </AppProviders>
      </body>
    </html>
  );
}
