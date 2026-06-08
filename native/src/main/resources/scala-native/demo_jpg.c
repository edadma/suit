// Accessors for the embedded demo JPEG (the bytes live in demo_jpg_data.c). Exposing it through
// two small functions keeps the Scala extern (see DemoAssets.scala) free of array-symbol linkage
// details, the same way the embedded font is reached.
extern unsigned char suit_demo_jpg[];
extern unsigned int  suit_demo_jpg_len;

const unsigned char *suit_demo_jpg_data(void) { return suit_demo_jpg; }
long suit_demo_jpg_size(void) { return (long)suit_demo_jpg_len; }
