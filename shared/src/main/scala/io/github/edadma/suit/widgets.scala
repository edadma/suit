package io.github.edadma.suit

// The widget library — the salle-equivalent: small reusable controls composed from
// the DSL primitives and vdom hooks. A widget is an ordinary vdom component, so its
// interaction state (hover, pressed) lives in `useState` and survives re-renders, and
// it reconciles in place exactly like an application component. Each widget is purely
// declarative output over `box`/`text`/`row`/`stack`; the render tree, layout, and
// input routing underneath are what give it pixels and behaviour.
//
// Every widget paints from the [[Theme]] it reads out of context ([[useTheme]]) — colours,
// corner radius, spacing — so a [[ThemeProvider]] restyles the whole set at once without
// touching widget code. The controlled widgets (Checkbox, Slider, Switch, RadioGroup, Tabs,
// TextField) never hold their own value: they render what they are given and report changes
// through a callback, leaving the state to the parent.
//
// The widgets are grouped into mixin traits — controls, indicators, overlays, data — over a
// shared support trait; this facade gathers them all so every member is reached as
// `widgets.X` exactly as before.
object widgets
    extends WidgetsSupport,
      WidgetsControls,
      WidgetsIndicators,
      WidgetsOverlays,
      WidgetsData
