import {
  Facebook,
  Instagram,
  Linkedin,
  Mail,
  MapPin,
  Phone,
  Printer,
  Youtube,
} from 'lucide-react';

import PageTitle from '../components/common/PageTitle';
import { Page } from '../components/common/Sheet.jsx';

/**
 * The union's public channels. Module scope, not inline in the render: it is a constant,
 * and rebuilding it on every render also rebuilds the `key`ed list React reconciles
 * against.
 */
const SOCIAL_CHANNELS = [
  { href: 'https://www.instagram.com/sasbo_the_finance_union/', icon: Instagram, name: 'Instagram' },
  { href: 'https://www.linkedin.com/company/sasbo/', icon: Linkedin, name: 'LinkedIn' },
  { href: 'https://www.youtube.com/channel/UCm2xVnyrAr8zXTeEOX17l3A', icon: Youtube, name: 'YouTube' },
  { href: 'https://www.facebook.com/SasboTheFinanceUnion', icon: Facebook, name: 'Facebook' },
];

/**
 * The union's own page — who SASBO is, what it stands for, and how to reach it.
 *
 * 🔴 THIS FILE WAS BROKEN THREE WAYS AND NOTHING COULD SEE IT, because nothing renders
 * it. `04.1` found all three by opening the file:
 *
 *   1. NO EXPORT AT ALL. `function About() {}` sat at module scope with no
 *      `export default`, so the module could not be imported even deliberately.
 *   2. FOUR UNDEFINED IDENTIFIERS. `Instagram`, `Linkedin`, `Youtube` and `Facebook`
 *      were rendered and never imported — a `ReferenceError` on first paint.
 *   3. AN ORPHANED HEADING. "Partners & Affiliates" announced a section with nothing
 *      under it; the content beneath belonged to "Head Office", which followed it
 *      immediately. A heading over nothing is a landmark that lies.
 *
 * 🔑 AND THAT IS THE FINDING, not the three defects. **`About` IS NOT ROUTED** — no
 * `<Route>` in `App.jsx`, no entry in `navigation.js`, no link anywhere in `src`, and it
 * has been that way since the initial commit. So the route smoke never mounted it, the
 * a11y pass never scanned it, and a page that throws on render has been sitting in the
 * repository for the life of the project looking perfectly healthy.
 *
 * ⚠️ WHETHER IT GETS A ROUTE IS NOT THIS WAVE'S CALL. Routing it means a navigation
 * entry, which is information architecture and the owner's. The file is now correct,
 * on-token and accessible so that routing it is a one-line decision rather than a
 * rebuild. Recorded in `findings.md` as F-P20.
 */
export default function About() {
  return (
    <Page>
      <PageTitle title="About Us" subtitle="SASBO — The Finance Union" />

      {/* 📱 R-RESP-O: a readable measure. `max-w-4xl` at this type size runs to roughly
          120 characters a line on a laptop, which is about twice the span an eye tracks
          comfortably. The contact block below is not prose and keeps its own width. */}
      <div className="mx-auto max-w-3xl space-y-8">
        <section className="space-y-3">
          <h2 className="text-xl text-brand-ink">Who We Are</h2>
          <p className="text-ink-muted">
            Sasbo, the South African Society of Bank Officials as it was in 1916,
            started with members from the National Bank of South Africa Limited,
            the African Banking Corporation, the Netherlands Bank of South African
            Limited and the Standard Bank of South Africa Limited. Over time the
            union became a niche union operating almost exclusively in the
            Standard Bank of South Africa Limited and the then Barclays National
            Bank of South Africa Limited. Sasbo - The Finance Union, as it is now
            known, having diversified into the finance sector as a whole, has de
            facto recognition with approximately 20 financial institutions,
            ranging from banks to insurance companies to pension fund
            administrators and the like, and has members from nearly 200
            financial institutions throughout the country. The union still has a
            banking flavor though in that the bulk of the membership is from ABSA,
            First National Bank and the Standard Bank, with Nedbank running a close
            fourth.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-xl text-brand-ink">Our Core Values</h2>
          <p className="text-ink-muted">
            The Foundation of South Africa&apos;s Premier Finance Union
          </p>
          <ul className="list-disc space-y-2 pl-6 text-ink-muted">
            <li>
              <span className="text-ink">Passion:</span>{' '}
              We are passionate about everything we do. Our passion is driven by
              our desire to make a positive difference to the lives of our
              members. Our enthusiasm is infectious.
            </li>
            <li>
              <span className="text-ink">Risk Tolerance:</span>{' '}
              We believe in taking prudent, calculated risks. We are always
              ready to challenge the status quo and engage in constructive
              confrontation.
            </li>
            <li>
              <span className="text-ink">Excellence:</span>{' '}
              We strive to excel in every aspect of our business. We aim to
              deliver service excellence to each and every member.
            </li>
            <li>
              <span className="text-ink">Motivation:</span>{' '}
              We celebrate our success. We are all committed to hard work and
              performance. We celebrate our successes and recognise those who
              outperform.
            </li>
            <li>
              <span className="text-ink">Innovation:</span>{' '}
              We are continuously moving forward, innovating and improving. We
              are open to ideas that challenge conventional views and drive
              innovation.
            </li>
            <li>
              <span className="text-ink">Empowerment:</span>{' '}
              We believe in empowering our employees. We encourage our employees
              to share their knowledge and give respect and responsibility to
              those around them.
            </li>
            <li>
              <span className="text-ink">Respect:</span>{' '}
              We treat others as we would like to be treated ourselves. We value
              our differences and respect the ideas and opinions of others.
            </li>
          </ul>
        </section>

        <section className="space-y-3">
          <h2 className="text-xl text-brand-ink">Financial Institutions</h2>
          <p className="text-ink-muted">
            Sasbo extends a hearty welcome to all members in all Financial
            Institutions. We currently have Recognition Agreements with the
            following Finance Institutions:
          </p>
          <ul className="grid list-disc gap-1 pl-6 text-ink-muted sm:grid-cols-2">
            <li>Absa Bank</li>
            <li>African Bank</li>
            <li>Bank of Athens</li>
            <li>Bankserv</li>
            <li>First National Bank</li>
            <li>IPS</li>
            <li>LandBank</li>
            <li>NHFC</li>
            <li>Nedbank</li>
            <li>Old Mutual</li>
            <li>SA Reserve Bank</li>
            <li>Santam</li>
            <li>SBV</li>
            <li>Standard Bank</li>
            <li>UBank</li>
            <li>VBS</li>
            <li>Capitec Bank Limited</li>
          </ul>
        </section>

        <section className="space-y-3">
          <h2 className="text-xl text-brand-ink">Head Office</h2>

          {/* An <address> because that is what this is, and it is the element assistive
              technology exposes as contact information for the page. The icons carry no
              meaning a label does not already carry, so each is aria-hidden and each row
              says its own name in text. */}
          <address className="space-y-3 not-italic text-ink-muted">
            <div className="flex gap-3">
              <MapPin aria-hidden="true" className="mt-1 h-5 w-5 shrink-0 text-brand" strokeWidth={1.5} />
              <div>
                <p>Fourmall Office Park West</p>
                <p>1 Percy Street</p>
                <p>Fourways, 2191</p>
              </div>
            </div>
            <div className="flex gap-3">
              <Mail aria-hidden="true" className="mt-1 h-5 w-5 shrink-0 text-brand" strokeWidth={1.5} />
              <p>
                Email:{' '}
                <a
                  href="mailto:reception@sasbo.org.za"
                  className="rounded-sm text-brand-ink hover-hover:hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-ring"
                >
                  reception@sasbo.org.za
                </a>
              </p>
            </div>
            <div className="flex gap-3">
              <Phone aria-hidden="true" className="mt-1 h-5 w-5 shrink-0 text-brand" strokeWidth={1.5} />
              <p>
                Phone:{' '}
                <a
                  href="tel:+27114670192"
                  className="rounded-sm text-brand-ink hover-hover:hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-ring"
                >
                  +27 11 467 0192
                </a>
              </p>
            </div>
            <div className="flex gap-3">
              <Printer aria-hidden="true" className="mt-1 h-5 w-5 shrink-0 text-brand" strokeWidth={1.5} />
              <p>
                Fax: <span className="text-ink">+27 11 467 0188</span>
              </p>
            </div>
          </address>

          {/* 🔴 EVERY ONE OF THESE LEAVES THE APPLICATION, and none of them said so.
              `rel="noreferrer"` is the security half of `target="_blank"` — without it
              the opened tab gets a handle on this one — and the visually-hidden
              "(opens in a new tab)" is the half a screen-reader user needs, because a
              tab switching underneath you with no warning is disorienting rather than
              convenient. */}
          <ul className="flex flex-wrap gap-4 pt-2">
            {SOCIAL_CHANNELS.map(({ href, icon: Icon, name }) => (
              <li key={name}>
                <a
                  href={href}
                  target="_blank"
                  rel="noreferrer"
                  className="touch-target inline-flex items-center gap-2 rounded-sm text-brand-ink hover-hover:hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-ring"
                >
                  <Icon aria-hidden="true" className="h-4 w-4" strokeWidth={1.5} />
                  {name}
                  <span className="sr-only">(opens in a new tab)</span>
                </a>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </Page>
  );
}
