# Liquid Glass adaptation (issue #155)

This adapts the user-supplied `LIQUID_GLASS_TRANSFORMATION.md` guide to GWS+.
It is an Android approximation, not Apple's renderer. School data, network
commands, caches, authentication and navigation remain owned by their existing
layers. No earbud-specific code or protocol workarounds belong here.

## Rendering ownership

`ui/components/GlassScene.kt` records only a theme/accent gradient in a sibling
Backdrop layer. Cards and controls sample that background, never themselves or
their neighboring controls. Each navigation entry owns a scene and its own
accent, so predictive-back previews retain their correct destination color.

The shell separately records the NavHost for the bottom tabs to sample. The
bottom bar is outside that recorded subtree. Root lists scroll beneath the
glass, with trailing content padding (and system navigation-bar insets) so the
last action can always scroll clear of the tabs. Messages and Plus use the same
trailing-padding contract as the banner-based tabs.
Ordinary content controls sample their entry's gradient. Since beta.2,
`GlassFloatingScaffold` gives Boutique and post/homework/product details a
separate scrolling-content layer and a stationary, unpainted header. Its
search/categories/actions sample that sibling's real content. Header height is
measured, and initial/trailing padding lives inside the scroll container, not
outside its viewport. Thus products/posts can pass behind both header controls
and bottom navigation without leaving the last item inaccessible. This is
local to the entry, not a cross-screen overlay or a new Back handler.

The fullscreen viewer similarly records its pager in its own dialog window,
then draws the close button above it. A dark translucent tint preserves the
white cross on both dark letterboxing and bright images. The pager's zoom,
pan and swipe never move the close action.

`GlassScene` tracks its `LocalView` owner. `WithGlassScene` creates a fresh scene
when a control enters another window (dialog, modal sheet or popup), instead of
sampling incompatible window coordinates. `GlassAlertDialog` and
`GlassDropdownMenu` explicitly provide local scenes inside their windows.

## Shared components and coverage

`GlassControls.kt` supplies glass surfaces (including clickable cards), liquid
buttons, text/outlined actions, header/icon actions, switches, confirmation
dialogs, popup menus and the inline expanding settings picker. Existing imports
alias these components to keep the screen action wiring unchanged. Known page,
control and annotation geometries map to continuous Capsule shapes; special
shapes such as circles are preserved.

These shared components are used across login/onboarding, all six tabs, the
secondary Plus sections, detail screens, quiz, composer/AI panel, community,
shop, settings, previews and viewer actions. Editable text, checkboxes, audio,
image content and platform pickers retain their native behavior. GWS+ has no
sliders, so no unused slider implementation is copied.

The picker expands inline with spring-driven size and corner changes, bounded
finger-following stretch and at most 1.2 dp directional motion blur on Android
12+. This is a velocity-based approximation, not temporal accumulation; it
clears at rest. Tap the title again, choose an option, or press Back to collapse.
Only an expanded picker installs a Back handler; the NavHost predictive-pop
callbacks, saved tab stacks and quiz exit guard are unchanged.

Errors remain non-blocking and visible until resolved; there is intentionally no
Dismiss action that could conceal a failed/offline refresh. `BandeauErreur`
announces politely and uses an 8 dp background blur for its retry action (normal
buttons use 0.75 dp). Text itself is not frosted.

## Accessibility and compatibility

- API 26 remains the minimum. Below 31, normal content surfaces use 97% tint;
  API 31+ uses 80% tint and backdrop blur. Lensing is enabled only on API 33+.
- Beta.2 separates reading-card tint from chrome: floating controls and tabs
  use only 16% theme tint on API 31+, and retain 97% tint on older systems.
  Chrome blur is 0.75 dp (previously 4–8 dp), with stronger 48 dp refraction
  and chromatic edge dispersion. Foreground labels/icons are never blurred.
- Filled actions keep a stronger tint to preserve foreground contrast.
- Disabled liquid buttons reject input. Button callbacks come from the current
  composition; remembered drag callbacks use `rememberUpdatedState`.
- Settings already expose a toggleable row; the liquid switch is decorative
  and does not create a second touch or TalkBack action.
- Tabs expose `Role.Tab` and selected state. External selection changes must
  never issue navigation. Cancelled drags must never write settings or navigate.
- Native dialog outside-tap/Back dismissal and enabled confirmation actions are
  retained. Dialog text slots have finite constraints and do not add another
  scroll container around already-scrollable community forms.

Pinned source and modifications: [`third_party/kyant-backdrop/`](../../third_party/kyant-backdrop/).

## Verification

Run the ordinary debug assembly and JVM suite, `:app:lintDebug`, and
`:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=school.greenwood.plus.GlassControlsTest`.
`GlassPolicyTest` pins compatibility thresholds; `GlassControlsTest` covers
disabled actions, current callbacks, one-action switch semantics, picker Back,
external tab synchronization and dialog actions in another window.

Verification on 2026-10-19: debug assembly and all 320 JVM tests pass; all eight
glass component tests pass on an API 36 AOSP ATD emulator (including refused
tab selection and cancelled tab drag). Lint now completes analysis, but reports
four existing `NewApi` errors in untouched `CommunApi.kt` (`HexFormat`, two
calls) and `Fichiers.kt` (`MediaStore.Downloads`, two accesses). The complete
instrumented suite additionally exposes an existing `SessionStoreTest`
initialization failure: its `@Before` method `étatNeuf()` is not JVM-void.
These unrelated findings are not suppressed or repaired in this UI change.

Beta.2 verification adds one JVM policy test (321 total) and two component
checks (10 total): the floating header stays in place after scrolling, the
last action clears the bottom inset, and the viewer's close action stays fixed
while paging. These checks also pass on a regular API 36 AOSP emulator with
rendering enabled, not just AOSP ATD. Synthetic fixture captures can be enabled
with the instrumentation argument `captureGlassFixtures=true`; they contain
no family data. They do not replace a handset review of real school images.

Before merging or publishing a stable release, check a real handset with school content: every tab
and detail, cancellation/completion of predictive Back, returning from Devoirs,
quiz exit confirmation, login/logout, composing/attachments, dialogs, scroll
performance, dark/light themes, TalkBack, large fonts and 320 dp width. Automated
component checks are not a claim that these authenticated flows or real-device
performance have been verified. A beta must precede any later stable release;
this UI change itself does not authorize a stable publication.
The user's subsequent release request prepares `0.10.2-beta.1`, versionCode 126,
from this branch (stable 0.10.0 base, not the unmerged 0.10.1 beta stack). Signing
configuration is unchanged. The user explicitly authorized using the local
`4ff056a3…` signing certificate for this beta after being informed that it differs
from the historical `775498f7…` certificate. This beta cannot update versions
signed with another key: uninstalling the previous app deletes its local session
and caches. This exception does not authorize merging or a stable release.

Published beta: <https://github.com/iliasgws/gws-plus/releases/tag/v0.10.2-beta.1>,
built from `602a43b` and installed/cold-launched on the API 36 test emulator.
The release asset's SHA-256 is
`86176051aca2ff731bb130dc4be322f4f0bb7285361e92619b00f4ea42b6f263`.
Keep an encrypted off-machine backup of `~/.android/debug.keystore` to preserve
this beta's signing identity; never commit the key or its backup.

Beta.2: <https://github.com/iliasgws/gws-plus/releases/tag/v0.10.2-beta.2>, built
from `8177aa1`, versionCode 127. Signature matches beta.1, so no uninstall is
needed to update that version. Signed release installation/cold launch on the
regular API 36 emulator passes. APK SHA-256:
`541a4f07e0e6f3729686af2d022ae3add078ae4b46cfb50bbd63120c1e46d46a`.
