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
Header actions sample their entry's gradient; the title/banner layout remains
the existing one, not a new cross-screen overlay.

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
buttons use 2 dp). Text itself is not frosted.

## Accessibility and compatibility

- API 26 remains the minimum. Below 31, normal content surfaces use 97% tint;
  API 31+ uses 80% tint and backdrop blur. Lensing is enabled only on API 33+.
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

Before merging/releasing, check a real handset with school content: every tab
and detail, cancellation/completion of predictive Back, returning from Devoirs,
quiz exit confirmation, login/logout, composing/attachments, dialogs, scroll
performance, dark/light themes, TalkBack, large fonts and 320 dp width. Automated
component checks are not a claim that these authenticated flows or real-device
performance have been verified. A beta must precede any later stable release;
this UI change itself does not authorize a stable publication.
The user's subsequent release request prepares `0.10.2-beta.1`, versionCode 126,
from this branch (stable 0.10.0 base, not the unmerged 0.10.1 beta stack). Signing
configuration is unchanged. Publication is blocked until the historical
`775498f7…` signing keystore is restored: the local key has a different
certificate and must not be used for a release.
