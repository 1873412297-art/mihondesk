# i18n

This module houses the string resources and translations.

Original English strings are managed in `src/commonMain/moko-resources/base/`. Translations are done externally via Weblate. See [our website](https://mihon.app/docs/contribute#translation) for more details. 

## Desktop app strings / 桌面端文案

The Windows desktop app (`desktop-app/`) does **not** use moko-resources. It has its own
trilingual string system in `desktop-app/src/main/kotlin/mihon/desktop/i18n/`:

- `DesktopStrings.kt` — a `DesktopStrings` interface with one `val`/`fun` per message,
  implemented by three singleton objects: `EnglishStrings`, `SimplifiedChineseStrings`
  (`zh-CN`, simplified characters) and `TraditionalChineseStrings` (`zh-TW`, traditional
  characters). `AppLanguage` + `DesktopStrings.resolve()` pick the implementation, and
  `LocalStrings` (a `staticCompositionLocalOf`) exposes the active one in composition.
- `UiText.kt` — an `enum class UiText(english, simplified, traditional)` for
  shorter/shared messages. Placeholders use `{0}`, `{1}`, … and are filled by
  `strings.text(UiText.SomeKey, arg0, arg1)`. `DesktopStrings.locale` maps the active
  strings object to a `java.util.Locale` for localized date/number formatting
  (`DateTimeFormatter.ofLocalizedDate/Time(...).withLocale(strings.locale)`).

### How to add a language / 如何新增语言

1. Add a value to `AppLanguage` (ISO code + display name) and map it in
   `DesktopStrings.resolve()`.
2. Create a new `object XxxStrings : DesktopStrings` implementing **every** member of the
   interface (the compiler enforces this), using the file's existing section order.
   For `UiText`, add the third tuple to every enum entry (the enum forces all three
   languages to stay in sync).
3. Call sites read `val strings = LocalStrings.current` (or receive it as a parameter) and
   use `strings.someKey` / `strings.someFun(...)` / `strings.text(UiText.SomeKey, ...)`.
   Never branch on language at the call site, and never inline user-facing literals —
   `recoveryText(en, zh, tw)` was removed for this reason.
4. Keep hot paths allocation-free: hoist `strings.text(...)` and locale-dependent
   formatters into `remember(...)` where they are used per item in lists.
