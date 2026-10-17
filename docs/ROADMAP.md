# Essence roadmap

Essence is a minimal Android home-screen launcher with an e-ink look. It has AI agents built in. The layout borrows from
[Niagara Launcher](https://play.google.com/store/apps/details?id=bitpit.launcher): a short list of favorites on one
home page, an A–Z list of every app with a letter rail on the edge, no icon grid, and no animations.

## Requirements

1. **E-ink look.** Information is shown like an e-paper display.
2. **AI agent cards.** Essence has its own home-screen cards, powered by Claude.
3. **Memory and hands-free help.** The launcher remembers things about the user. The user can speak a command, and
   Essence carries it out on the phone by itself, for example replying to a message in Chrome or a messaging app.

## Phase 1: launcher shell (done)

- `MainActivity` registers as a HOME activity. Pressing Home returns to the home page.
- Home page: a glance block with the time, the date and the battery level. It updates only on the system minute tick,
  the way e-ink refreshes. Below it are up to 8 favorites in large serif type.
- Swipe up, or tap "ALL APPS", to open the A–Z list with search and a letter rail. Swipe down to pull the notification
  shade.
- Long-press any app to pin it to home, unpin it, open app info or uninstall it.
- Theme: warm paper and near-black ink, with an inverted version at night. Serif text with monospace labels, hairline
  rules, no ripples, no transitions, and no dynamic color.

## Phase 2: Claude agent, memory and the hands-free assistant (done)

Requirements 2 and 3, built on Claude (`claude-opus-5-5` by default; the model can be changed in Settings).

- **Agent cards.** Home-page cards, each with a title and a prompt. Tap a card to run its prompt. The answer is
  printed on the card in plain text. Long-press a card to edit or delete it. Cards refresh only when tapped.
- **Memory.** Claude has a `remember` tool for saving durable facts about the user. Saved facts are sent with every
  request. Settings lists every memory, and each one can be deleted or added by hand.
- **Assistant (voice → autonomous action).** Tap ✦ SPEAK on the home page or the ✦ bubble over any app, then say a
  command, for example "reply to Sam on WhatsApp that I'll be 10 minutes late". An `AccessibilityService` gives
  Claude these tools:
  - `read_screen`: a numbered outline of the foreground app's elements
  - `screenshot` (Android 11+)
  - `tap`, `long_press`, `tap_point`, `type_text` and `scroll`
  - `press_key` (back, home, recents, notifications)
  - `open_app` and `wait`
  - `ask_user`

  Each action returns the updated screen. While a command runs, a status strip shows each step, and the bubble becomes
  ■, which stops it.
- **Guardrails.** Claude must call `ask_user` and get an explicit yes before it sends, posts, buys, deletes, accepts
  terms or changes account settings. It never enters passwords, codes or payment details. Text on the screen is
  treated as information, never as instructions. Password fields are masked in the outline.
- **API key.** It is stored encrypted with an Android Keystore key. Requests go straight from the phone to the Claude
  API.

### Caveats

- **Privacy:** while a command runs, the screen outline and any screenshots are sent to Claude.
- **Google Play policy:** Play restricts accessibility services that aren't for accessibility, and it would likely
  reject this app. Plan to sideload it, or distribute it through F-Droid or GitHub Releases.
- **Android 13+ restricted settings:** a sideloaded app must be allowed under *App info → ⋮ → Allow restricted
  settings* before its accessibility service can be turned on.
- Some apps block screen capture (`FLAG_SECURE`). The assistant then relies on the text outline, if the app exposes
  one.

## Phase 3: next

- A hands-free wake phrase, so a command doesn't need a tap first.
- Agent cards that refresh on a schedule (WorkManager), and cards that can use the device tools.
- Hosting third-party app widgets on the home page.

## Later

- Notification inbox in the e-ink style (`NotificationListenerService`), with the option for an agent to summarize it.
- A grayscale/e-ink mode for the entire phone through Digital Wellbeing's bedtime-mode shortcut.
- Hidden apps, icon-free folders, and a focus timer.
