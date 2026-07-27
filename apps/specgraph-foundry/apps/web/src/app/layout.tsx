import type { Metadata, Viewport } from "next";
import { AppProviders } from "@/components/providers/app-providers";
import { PwaRegistration } from "@/components/pwa/pwa-registration";
import "./globals.css";

export const metadata: Metadata = {
  title: "SpecGraph Foundry",
  description: "Production web application foundation for SpecGraph Foundry.",
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
    <html lang="en" data-theme="dark">
      <body>
        <AppProviders>
          {children}
          <PwaRegistration />
        </AppProviders>
      </body>
    </html>
  );
}
