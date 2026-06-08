// Accessors for the embedded InterVariable font (the bytes live in inter_data.c). Scala
// Native has no runtime resource loading — a native executable has no classpath — so the
// font is linked in as C data at compile time and read from memory. Exposing it through
// two small functions keeps the Scala extern (see InterFont.scala) free of array-symbol
// linkage details. The font is loaded into a face with FreeType newMemoryFace (see Fonts);
// InterVariable is under the SIL Open Font License (fonts/OFL.txt).
extern unsigned char suit_inter_data[];
extern unsigned int  suit_inter_data_len;

const unsigned char *suit_inter_font_data(void) { return suit_inter_data; }
long suit_inter_font_size(void) { return (long)suit_inter_data_len; }
