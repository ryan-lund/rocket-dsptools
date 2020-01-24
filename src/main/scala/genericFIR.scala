//// See LICENSE for license details.
//
package rocketdsptools

import chisel3.core._
import chisel3.{Bundle, Module}
import chisel3.util.Decoupled
import dsptools._
import dsptools.numbers._

class genericFIRIO[T<:Data:Ring](genIn:T, genOut:T) extends Bundle {
    val in = Decoupled(Input(genIn))
    val out = Decoupled(Output(genOut))

    override def cloneType: this.type = genericFIRIO(genIn, genOut).asInstanceOf[this.type]
}
object genericFIRIO {
    def apply[T<:Data:Ring](genIn:T, genOut:T): genericFIRIO[T] = new genericFIRIO(genIn, genOut)
}

// A generic FIR filter
class genericFIR[T<:Data:Ring](genIn:T, genOut:T, coeffs: Seq[T]) extends Module {
  val io = IO(genericFIRIO(genIn, genOut))
  ;
  // Construct a vector of genericFIRDirectCells
  val DirectCells = Seq.fill(coeffs.length){ Module(new genericFIRDirectCell(genIn, genOut)).io }

  // Define the carry wire
  // Construct the direct FIR chain
  DirectCells(0).in := io.in
  DirectCells(0).carryIn := Ring[T].zero
  for(i <- 0 until coeffs.length) {
  	DirectCells(i).coeff := coeffs(i) // wire coefficient from supplied vector
  	if (i != coeffs.length - 1) {
  		DirectCells(i+1).carryIn := DirectCells(i).carryOut // connect carryout to carryin chain
  		DirectCells(i+1).in := DirectCells(i).out // pass delayed signal
  	} else {
      io.out := DirectCells(i).carryOut
    }
  }  
}

// A generic FIR direct cell used to construct a larger direct FIR chain
// 
//   in ------------[z^-1]-- out
//			    		|
//	 coeff ----[*]
//	            |
//	 carryIn --[+]---------- carryOut
//
class genericFIRDirectCell[T<:Data:Ring](genIn: T, genOut: T) extends Module {
	val io = IO(new Bundle {
		val in = Input(genIn) 	// value passed in
		val coeff = Input(genIn)		// coefficient of this transpose stage
		val carryIn = Input(genOut) 	// carryIn from previous stage
		val out = Output(genOut)  // value delayed by one time step
		val carryOut = Output(genOut) 	// carryOut to next stage
	})

	val inputRegister = Reg(genIn)
	inputRegister := io.in
	io.out := inputRegister
	io.carryOut := io.in * io.coeff + io.carryIn 
}

