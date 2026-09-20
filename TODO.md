# Greenwood School + Implementation Tasks

- [ ] 1. **Fix crash after scrolling in Documents section**
  - Remove `captureListe` / `layerBackdrop` from `LazyColumn` in `DocumentsScreen.kt`.
  - Let floating search and filters sample `LocalGlassBackdrop` cleanly without unattached coordinates crash during lazy list item recycling.
  - Review `ConversationScreen.kt` to ensure its `LazyColumn` doesn't suffer from the same crash issue.

- [ ] 2. **Fix navigation state & bottom bar desync when switching tabs during a Quiz or Message Thread**
  - In `AppNav.kt`, associate detail routes (`quiz/{quizId}` -> `"documents"`, `conversation/{conversationId}` / `nouveau-message` -> `"messages"`, `demandes` / `post/{postId}` -> `"registre"`) with their respective parent tab.
  - Fix `GlassBottomBar` tab selection so `sélectionné` is true for the parent tab when inside a detail screen.
  - Fix tab switching logic (`allerÀLOnglet`) so tapping a tab when on a detail screen or switching back and forth properly updates the navbar and navigates smoothly.

- [ ] 3. **Quiz back button: use liquid glass and leave it floating alone**
  - In `QuizScreen.kt`, replace the standard `IconButton` with a floating `LiquidButton`.
  - Ensure it floats freely without any englobing header card or background container ("leave it floating with no main thing it is englobed in").

- [ ] 4. **Make all buttons use liquid glass across the app**
  - Audit all screens for standard Material buttons (`Button`, `IconButton`, `TextButton`).
  - Convert action buttons, back buttons, submit/retry buttons, etc., to use `LiquidButton` / liquid glass styling.

- [ ] 5. **Ensure search box and nav bar properly use liquid glass**
  - Inspect `ChampRecherche` and `GlassBottomBar` in `Glass.kt`.
  - Verify backdrop sampling, fallbacks, and parameters so true liquid glass (blur, refraction lens, subtle givre) is visibly active.

- [ ] 6. **Verification & Testing**
  - Run `./gradlew testDebugUnitTest` and `./gradlew assembleDebug`.
  - Verify all builds succeed with zero errors.
