//// See LICENSE for license details.
//
package rocketdsptools

import chisel3.core._
import chisel3.{Bundle, Module}
import dsptools._
import org.scalatest.{Matchers, FlatSpec}
import dsptools.numbers._

// A generic FIR filter
class genericFIR[T<:Data:Ring](genIn: => T, genOut: => T, numCoeffs: Int) extends Module {
  val io = IO(new Bundle {
  	val inputVal = Input(genIn)
  	val coeffs = Input(Vec(numCoeffs, genIn))
  	val outputVal = Output(genOut)
  })
  // Construct a vector of genericFIRDirectCells
  val DirectCells = Vec(numCoeffs, Seq.fill(numCoeffs){ Module(new genericFIRDirectCell(genIn, genOut)).io })

  // Define the carry wire
  // Construct the direct FIR chain
  for(i <- 0 until numCoeffs) {
  	DirectCells(i).coeff := io.coeffs(i) // wire coefficient from supplied vector
  	if (i != numCoeffs - 1) {
  		DirectCells(i+1).carryIn := DirectCells(i).carryOut // connect carryout to carryin chain
  		DirectCells(i+1).inputVal := DirectCells(i).outputVal // pass delayed signal
  	} else {
      io.outputVal := DirectCells(i).carryOut
    }
  }  
}

// A generic FIR direct cell used to construct a larger direct FIR chain
// 
//   inputVal -----[z^-1]-- outputVal
//			    		|
//	 coeff ----[*]
//	            |
//	 carryIn --[+]---------- carryOut
//
class genericFIRDirectCell[T<:Data:Ring](genIn: => T, genOut: => T) extends Module {
	val io = IO(new Bundle {
		val inputVal = Input(genIn) 	// value passed in
		val coeff = Input(genIn)		// coefficient of this transpose stage
		val carryIn = Input(genOut) 	// carryIn from previous stage
		val outputVal = Output(genOut)  // value delayed by one time step
		val carryOut = Output(genOut) 	// carryOut to next stage
	})

	val inputRegister = Reg(genIn)
	inputRegister := io.inputVal
	io.outputVal := inputRegister
	io.carryOut := io.inputVal * io.coeff + io.carryIn 
}