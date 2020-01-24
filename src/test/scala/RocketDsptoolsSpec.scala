package rocketdsptools

import chisel3._
import org.scalatest.{FlatSpec, Matchers}

class RocketDsptoolsSpec extends FlatSpec with Matchers {
	behavior of "Generic FIR"

	for (n <- 1 to 30) {
		it should s"work for adding 3 UInt of width $n" in {
			UIntFIRTester(n) should be (true)
		}
	}
}
