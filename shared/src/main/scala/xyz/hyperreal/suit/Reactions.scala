package xyz.hyperreal.suit

abstract class Reactions extends Reaction {

  def +=(r: Reaction): Reactions

  def -=(r: Reaction): Reactions

}
