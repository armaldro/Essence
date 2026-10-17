# Essence roadmap

Essence is a minimal Android home-screen launcher with an e-ink look. It has AI agents built in. The layout borrows from
[Niagara Launcher](https://play.google.com/store/apps/details?id=bitpit.launcher): a short list of favorites on one
home page, an A–Z list of every app with a letter rail on the edge, no icon grid, and no animations.

## Requirements

1. **E-ink look.** Information is shown like an e-paper display.
2. **AI agent widgets.** Home-screen widgets work with more than one AI agent.
3. **Memory and screen help.** The launcher remembers things about the user and helps inside other apps, for example by
   reading the screen and drafting a reply in Chrome or a messaging app.

## Phase 1: launcher shell (done)

- `MainActivity` registers as a HOME activity. Pressing Home returns to the home page.
- Home page: a glance block with the time, the date and the battery level. It updates only on the system minute tick,
  the way e-ink refreshes. Below it are up to 8 favorites in large serif type.
- Swipe up, or tap "ALL APPS", to open the A–Z list with search and a letter rail. Swipe down to pull the notification
  shade.
- Long-press any app to pin it to home, unpin it, open app info or uninstall it.
- Theme: warm paper and near-black ink, with an inverted version at night. Serif text with monospace labels, hairline
  rules, no ripples, no transitions, and no dynamic color.

## Phase 2: agent widgets

The words "agent widgets" can mean two different things, and both are planned:

| Track | What it is | How |
| --- | --- | --- |
| **2a. Native agent cards** | Essence's own home-page cards. Each card is tied to an agent (Claude, ChatGPT, Gemini or a local model). Examples: a daily brief, "ask anything", a summary of the notification inbox. | An `AgentProvider` interface with one implementation per vendor. API keys stay on the device in an encrypted store. Cards render in the same e-ink style. |
| **2b. Hosting third-party widgets** | Place the official Android widgets of installed AI apps (Gemini, ChatGPT, Claude, Perplexity and others) on the home page. | `AppWidgetHost` with `AppWidgetManager.bindAppWidgetIdIfAllowed` and a widget picker. These widgets draw their own colors, so an optional grayscale filter keeps them on theme. |

Building blocks:

- `agents/AgentProvider.kt`: `suspend fun complete(messages, tools): Flow<Chunk>`, implemented per provider.
- Settings screen: add an agent, choose its provider and model, paste an API key, and set a default agent per card.
- Cards refresh on demand or on a schedule through WorkManager. Nothing polls in the background.

## Phase 3: memory

- A Room database on the device stores **memories** (short facts about the user, such as preferences, people and
  projects) and **notes**.
- Before a call, the agent receives the memories relevant to the request. It starts with keyword ranking; embeddings can
  come later.
- A "Remember this" action is available from any agent card or from the screen-assist overlay. A memory page lets the
  user see, edit and delete every memory.
- Encrypted backup and export, so memories survive a phone change.

## Phase 4: screen assist (help inside other apps)

- An `AccessibilityService` reads the text tree of the app in the foreground (`getRootInActiveWindow`). It can capture
  the screen on Android 11 and later (`takeScreenshot`) for apps whose text isn't exposed.
- A small floating "✦" button appears over other apps (an accessibility overlay). Tapping it sends the visible
  conversation and the relevant memories to the chosen agent, which returns 2–3 reply drafts.
- The user picks a draft. Essence fills it into the app's text field with `ACTION_SET_TEXT`, or copies it to the
  clipboard as a fallback. **Essence never presses Send.** The user always sends the message.
- Per-app allow list: the service does nothing in apps the user has not turned on. Password fields and banking apps are
  always skipped.

### Caveats

- **Privacy:** screen text goes to whichever AI provider the user picks. A local model avoids this, at the cost of quality.
- **Google Play policy:** Play restricts accessibility services that aren't for accessibility, and it would likely reject
  this app. Plan to sideload it, or distribute it through F-Droid or GitHub Releases.
- **Android 13+ restricted settings:** a sideloaded app must be allowed under *App info → ⋮ → Allow restricted settings*
  before its accessibility service can be turned on.
- Some apps block screen capture (`FLAG_SECURE`). Assist still works from the text tree when an app exposes it.

## Later

- Notification inbox in the e-ink style (`NotificationListenerService`), with the option for an agent to summarize it.
- A grayscale/e-ink mode for the entire phone through Digital Wellbeing's bedtime-mode shortcut.
- Hidden apps, icon-free folders, and a focus timer.
