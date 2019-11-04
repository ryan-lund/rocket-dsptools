package rocketdsptools

import dsptools.DspTester

/**
 * Case class holding information needed to run an individual test
 */
case class X(
  // input x, y and z
  xin: Double,
  // optional outputs
  // if None, then don't check the result
  // if Some(...), check that the result matches
  xout: Option[Double] = None
)

/**
 * DspTester for FixedIterativeCordic
 *
 * Run each trial in @trials
 */
class genericFIRTester[T <: chisel3.Data](g: IterativeGenericFIR[T], trials: Seq[X], tolLSBs: Int = 0) extends DspTester(c) {
  //val maxCyclesWait = 50

  //poke(c.io.out.ready, 1)
  //poke(c.io.in.valid, 1)

  for (trial <- trials) {
    poke(g.io.in.bits.x, trial.xin)

    // wait until input is accepted
    /*var cyclesWaiting = 0
    while (!peek(c.io.in.ready) && cyclesWaiting < maxCyclesWait) {
      cyclesWaiting += 1
      if (cyclesWaiting >= maxCyclesWait) {
        expect(false, "waited for input too long")
      }
      step(1)
    }
    // wait until output is valid
    cyclesWaiting = 0
    while (!peek(c.io.out.valid) && cyclesWaiting < maxCyclesWait) {
      cyclesWaiting += 1
      if (cyclesWaiting >= maxCyclesWait) {
        expect(false, "waited for output too long")
      }
      step(1)
    }
    */
    // set desired tolerance
    // in this case, it's pretty loose (2 bits)
    // can you get tolerance of 1 bit? 0? what makes the most sense?
    fixTolLSBs.withValue(tolLSBs) {
      // check every output where we have an expected value
      trial.xout.foreach { x => expect(g.io.outputVal.bits.x, x) }
    }
  }
}

/**
 * Convenience function for running tests
 */
 // TODO: Fix Params 
object FixedGenericFIRTester {
  def apply(params: FixedCordicParams, trials: Seq[X]): Boolean = {
    chisel3.iotesters.Driver.execute(Array("-tbn", "firrtl", "-fiwv"), () => new genericFIR(params)) {
      g => new GenericFIRTester(g, trials)
    }
  }
}

object RealGenericFIRTester {
  def apply(params: CordicParams[dsptools.numbers.DspReal], trials: Seq[X]): Boolean = {
    chisel3.iotesters.Driver.execute(Array("-tbn", "verilator", "-fiwv"), () => new genericFIR(params)) {
      g => new GenericFIRTester(g, trials)
    }
  }
}