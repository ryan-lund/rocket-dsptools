package rocketdsptools

import chisel3._
import chisel3.iotesters.PeekPokeTester
import scala.util.Random

class UIntFIRTester(dut: GenericFIR[UInt], n: Int = 3, maxTests: Int = 500) extends PeekPokeTester(dut) {
  // stores of initial values
  var randMax = scala.math.pow(2, n).toInt - 1
  var x : Long = 0
  var y : Long = 0
  var z : Long = 0

  // initialize FIR
  for (_ <- 0 until 3) {
    val d = Random.nextInt(randMax) //constrain to be UInt(3.W)

    poke(dut.io.in.valid, 1.U)
    poke(dut.io.out.ready, 1.U)
    expect(dut.io.out.valid, 0.U)
    expect(dut.io.in.ready, 1.U)
    poke(dut.io.in.bits.data, d)
    step(1)
    z = y
    y = x
    x = d
  }

  for (_ <- 0 until maxTests) {
    val d = Random.nextInt(randMax)
    val expected = x + 2*y + 3*z

    poke(dut.io.in.valid, 0.U)
    poke(dut.io.out.ready, 1.U)
    expect(dut.io.in.ready, 1.U)
    poke(dut.io.in.bits.data, 0)
    expect(dut.io.out.valid, 0.U)

    expect(dut.io.out.bits.data, expected)

    step(1)


    poke(dut.io.in.valid, 1.U)
    poke(dut.io.out.ready, 1.U)
    expect(dut.io.in.ready, 1.U)
    poke(dut.io.in.bits.data, d)

    expect(dut.io.out.valid, 1.U)
    expect(dut.io.out.bits.data, expected)
    
    step(1)

    z = y
    y = x
    x = d
  }
}

object UIntFIRTester {
  def apply(n: Int, maxTests: Int = 500): Boolean = {
    chisel3.iotesters.Driver.execute(Array[String](), () => new GenericFIR[UInt](UInt(n.W), UInt((3+n).W), Seq(1.U, 2.U, 3.U))) { c => 
      new UIntFIRTester(c, n = n, maxTests = maxTests)
    }
  }
}
