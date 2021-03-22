package xyz.hyperreal.suit

import java.io.FileInputStream
import java.awt.{Font => JFont}

object PlatformDependent {

  private val STYLE_MAP =
    Map[TextStyle, Int](
      TextStyle.PLAIN -> JFont.PLAIN,
      TextStyle.ITALIC -> JFont.ITALIC
    )

  def getFont(typeface: String, size: Double, style: TextStyle): Font = {
    val filename =
      typeface match {
        case "Nimbus Sans L" => "NimbusSans-Regular"
      }
    val ttf = new FileInputStream(s"jvm/$filename.ttf")
    val res = JFont.createFont(JFont.TRUETYPE_FONT, ttf).deriveFont(STYLE_MAP(style), size.toFloat)

    ttf.close()
    new JVMFont(res)
  }

}
