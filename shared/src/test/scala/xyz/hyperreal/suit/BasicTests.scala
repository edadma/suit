package xyz.hyperreal.suit

import org.scalatest._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class BasicTests extends AnyFreeSpec with Matchers {

  "test 1" in {
    1 + 2 shouldBe 3
  }

}
