package io.github.edadma.suit

/** The pointer shape a widget asks for while the cursor is over it. It is a semantic set — what
  * the shape *means* — not a bitmap: the native runtime maps each to the platform's own system
  * cursor, so a link reads as the hand the OS uses, a text field as its I-beam. The mouse shape is
  * process-wide, so the runtime resolves one winner per frame (the nearest ancestor of whatever is
  * under the pointer that names a cursor) and sets it; a widget that names none leaves the shape to
  * its surroundings, and with nothing named anywhere the arrow shows.
  *
  * These are the shapes a desktop UI actually reaches for; the platform has a few more (help, cell,
  * zoom) that are not exposed here. */
enum Cursor:
  /** The ordinary arrow — the resting shape, and what a widget uses to *override* an inherited
    * cursor back to the arrow (distinct from naming no cursor at all, which inherits). */
  case Default

  /** The pointing hand, for anything clickable — a button, a link, a menu item. */
  case Pointer

  /** The I-beam, over selectable or editable text. */
  case Text

  /** A crosshair, for precise picking — a colour dropper, a selection marquee. */
  case Crosshair

  /** The four-way move arrow, over something the drag repositions wholesale. */
  case Move

  /** The "no drop" shape, over a target that will reject the current action. */
  case NotAllowed

  /** A busy spinner shown *while the UI stays interactive* (a background load). */
  case Progress

  /** The busy shape for a UI that is blocked and not accepting input. */
  case Wait

  /** A horizontal resize arrow (↔), for a left/right edge or a vertical splitter. */
  case ResizeEW

  /** A vertical resize arrow (↕), for a top/bottom edge or a horizontal splitter. */
  case ResizeNS

  /** A diagonal resize arrow (⤢) running bottom-left to top-right, for those corners. */
  case ResizeNESW

  /** A diagonal resize arrow (⤡) running top-left to bottom-right, for those corners. */
  case ResizeNWSE
