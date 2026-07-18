// Accessors for the embedded JetBrains Mono font (the bytes live in mono_data.c). Scala
// Native has no runtime resource loading — a native executable has no classpath — so the
// font is linked in as C data at compile time and read from memory. Exposing it through
// two small functions keeps the Scala extern (see MonoFont.scala) free of array-symbol
// linkage details. The font is loaded into a face with FreeType newMemoryFace (see Fonts);
// JetBrains Mono is under the SIL Open Font License (fonts/OFL-JetBrainsMono.txt).
extern unsigned char suit_mono_data[];
extern unsigned int  suit_mono_data_len;

const unsigned char *suit_mono_font_data(void) { return suit_mono_data; }
long suit_mono_font_size(void) { return (long)suit_mono_data_len; }
