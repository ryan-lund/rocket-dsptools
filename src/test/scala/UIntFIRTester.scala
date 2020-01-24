package rocketdsptools

import chisel3._
import chisel3.iotesters.PeekPokeTester
import dsptools._
import dsptools.numbers._
import scala.util.Random

class UIntFIRTester(dut: genericFIR[UInt], n: Int = 3, maxTests: Int = 500) extends PeekPokeTester(dut) {
  // stores of initial values
  var randMax = scala.math.pow(2, n).toInt - 1
  var y : Long = 0
  var z : Long = 0

  // initialize FIR
  for (_ <- 0 until 2) {
    val x = Random.nextInt(randMax) //constrain to be UInt(3.W)
    poke(dut.io.in, x)
    step(1)
    z = y
    y = x
  }

  for (_ <- 0 until maxTests) {
    val x = Random.nextInt(randMax) 
    val expected = x + y + z
    poke(dut.io.in, x)
    expect(dut.io.out, expected)
    step(1)
    z = y
    y = x
  }
}

object UIntFIRTester {
  def apply(n: Int, maxTests: Int = 500): Boolean = {
    chisel3.iotesters.Driver.execute(Array[String](), () => new genericFIR[UInt](UInt(n.W), UInt((3+n).W), Seq(1.U, 1.U, 1.U))) { c => 
      new UIntFIRTester(c, n = n, maxTests = maxTests)
    }
  }
}
