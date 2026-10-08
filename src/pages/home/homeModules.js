import { navItems } from '../../config/navigation.js';

/**
 * HOME'S MODULES, AND HOW A PERSON CHOOSES TO SEE THEM -- the 5 Oct Show & Tell (handover §A): Home keeps only the
 * modules (the month's figures went to Month end), and offers them as cards, a list or a table. Natalie liked the
 * cards, so the cards are what a browser that has chosen nothing sees.
 */

/** The three views, in the order the switch offers them. */
export const HOME_VIEWS = /* @__PURE__ */ Object.freeze(['cards', 'list', 'table']);
export const DEFAULT_HOME_VIEW = 'cards';

/**
 * Where this browser remembers the choice. 🔑 THIS EXACT NAME, AND NO OTHER KEY: the presentation engine photographs
 * every screen in one browser and resets the storage it knows by name -- `darkMode`, and this one, which the build
 * session gave it. Rename it and the decks' Home is whatever view the last screen left behind.
 */
export const HOME_VIEW_KEY = 'sasbo.homeView';

/**
 * The view this browser chose, or the cards.
 *
 * 🔴 THE READ IS WRAPPED, as `ThemeContext`'s is (R-ROB-D): a browser that blocks site data THROWS on `localStorage`
 * rather than answering null, and this runs during a render -- uncaught, that is a white page, not a forgotten
 * preference. A value this build does not know (an older build's, or one typed by hand) is the cards too.
 */
export const readHomeView = () => {
  try {
    const stored = localStorage.getItem(HOME_VIEW_KEY);
    return HOME_VIEWS.includes(stored) ? stored : DEFAULT_HOME_VIEW;
  } catch {
    return DEFAULT_HOME_VIEW;
  }
};

/** Remember the choice. A browser that cannot keep it still shows it, and forgets it on the next visit. */
export const storeHomeView = (view) => {
  try {
    localStorage.setItem(HOME_VIEW_KEY, view);
  } catch {
    // No storage: the view still changes for this visit.
  }
};

/**
 * What each module is for. The rail has no descriptions and a menu row with nothing under its title is a poorer row;
 * keyed by href, so a renamed title cannot silently detach one from its module.
 */
const DESCRIPTIONS = {
  '/admin': 'Daily work, month end and its figures, and maintenance',
  '/admin/acb': 'The monthly debit order run, re-debits, unpaids and letters',
  '/banks': 'Every bank and branch Sasbo members work from',
  '/archives': 'Agreements, policies and research, by institution',
  '/members': 'The membership register across every bank',
  '/recruiters': 'Who signs members up, and how many they bring',
  '/reports': 'Organiser reports, locations, forms and listings',
};

/**
 * A module's whole name, where the rail prints a short one: "Debit Order Deductions" is SASBO's name for the module
 * once called A.C.B. (the 5 Oct Show & Tell), and "Debit Orders" is what a 256px rail row has room for.
 */
const NAMES = { '/admin/acb': 'Debit Order Deductions' };

/**
 * Every path through the menu to `href`, as the rail prints each step -- the shortest first. Members is two: on its
 * own, and under Admin Menu › Daily Menu.
 */
export const menuPaths = (href, items = navItems, path = []) => items
  .flatMap((item) => {
    const here = [...path, item.title];
    return [
      ...(item.href === href ? [here] : []),
      ...(item.subItems ? menuPaths(href, item.subItems, here) : []),
    ];
  })
  .sort((a, b) => a.length - b.length);

/**
 * 🔑 THE MODULES ARE THE RAIL, READ ACROSS -- not a list of their own. `navItems` is the one place the application's
 * areas are declared, and Home drops only its own entry: a module added to the menu appears here on the same commit,
 * one removed (Subscriptions, 5 Oct) disappears from both. A module is shown by its whole name where the rail prints a
 * short one (`NAMES`). The table view adds where each sits in the menu and what is under it there, read from the same
 * tree.
 */
export const HOME_MODULES = /* @__PURE__ */ Object.freeze(navItems
  .filter((item) => item.href && item.href !== '/home')
  .map((item) => ({
    title: NAMES[item.href] ?? item.title.trim(),
    icon: item.icon,
    href: item.href,
    desc: DESCRIPTIONS[item.href],
    places: menuPaths(item.href).map((path) => path.join(' › ')),
    under: (item.subItems ?? []).map((sub) => sub.title),
  })));
