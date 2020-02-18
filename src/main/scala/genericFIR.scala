//// See LICENSE for license details.
//
package rocketdsptools

import chisel3._
import chisel3.{Bundle, Module}
import chisel3.util._
import dspblocks._
import dsptools.numbers._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem._

class genericFIRCellBundle[T<:Data:Ring](genIn:T, genOut:T) extends Bundle {
  val data: T = genIn.cloneType
  val carry: T = genOut.cloneType

  override def cloneType: this.type = genericFIRCellBundle(genIn, genOut).asInstanceOf[this.type]
}
object genericFIRCellBundle {
    def apply[T<:Data:Ring](genIn:T, genOut:T): genericFIRCellBundle[T] = new genericFIRCellBundle(genIn, genOut)
}

class genericFIRCellIO[T<:Data:Ring](genIn:T, genOut:T) extends Bundle {
    val coeff = Input(genIn)
    val in = Flipped(Decoupled(genericFIRCellBundle(genIn, genOut)))
    val out = Decoupled(genericFIRCellBundle(genIn, genOut))
}
object genericFIRCellIO {
    def apply[T<:Data:Ring](genIn:T, genOut:T): genericFIRCellIO[T] = new genericFIRCellIO(genIn, genOut)
}

class genericFIRBundle[T<:Data:Ring](proto: T) extends Bundle {
  val data: T = proto.cloneType

  override def cloneType: this.type = genericFIRBundle(proto).asInstanceOf[this.type]
}
object genericFIRBundle {
  def apply[T<:Data:Ring](proto: T): genericFIRBundle[T] = new genericFIRBundle(proto)
}

class genericFIRIO[T<:Data:Ring](genIn:T, genOut:T) extends Bundle {
    val in = Flipped(Decoupled(genericFIRBundle(genIn)))
    val out = Decoupled(genericFIRBundle(genOut))
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
  DirectCells(0).in.bits.data := io.in.bits.data
  DirectCells(0).in.bits.carry := Ring[T].zero
  DirectCells(0).in.valid := io.in.valid
  io.in.ready := DirectCells(0).in.ready
  for(i <- 0 until coeffs.length) {
  	DirectCells(i).coeff := coeffs(i) // wire coefficient from supplied vector
  	if (i != coeffs.length - 1) {
  	    DirectCells(i+1).in.bits := DirectCells(i).out.bits  // connect out to in chain
	    DirectCells(i+1).in.valid := DirectCells(i).out.valid // connect valid chain
            DirectCells(i).out.ready := DirectCells(i+1).in.ready // connect ready chain
  	} else {
            io.out.bits.data := DirectCells(i).out.bits.carry
            DirectCells(i).out.ready := io.out.ready
	    io.out.valid := DirectCells(i).out.valid
    }
  }  
}

// A generic FIR direct cell used to construct a larger direct FIR chain
// 
//   in ----- [z^-1]-- out
//	        |
//   coeff ----[*]
//	        |
//   carryIn --[+]-- carryOut
//
class genericFIRDirectCell[T<:Data:Ring](genIn: T, genOut: T) extends Module {
	val io = IO(genericFIRCellIO(genIn, genOut))
	
    // Registers to delay the input and the valid to propagate with calculations
    val hasNewData = RegInit(0.U)
    val inputReg = Reg(genIn)

    // Passthrough ready
    io.in.ready := io.out.ready
	
    when (io.in.fire()) {
        hasNewData := 1.U
	inputReg := io.in.bits.data
    }
	  
    io.out.valid := hasNewData & io.in.fire()
    io.out.bits.data := inputReg

	// Compute carry
	io.out.bits.carry := inputReg * io.coeff + io.in.bits.carry 
}

abstract class GenericFIRBlock[D, U, EO, EI, B<:Data, T<:Data:Ring]
(
  genIn: T,
  genOut: T,
  coeffs: Seq[T]
)(implicit p: Parameters) extends DspBlock[D, U, EO, EI, B] {
  val streamNode = AXI4StreamIdentityNode()
  val mem = None

  lazy val module = new LazyModuleImp(this) {
    require(streamNode.in.length == 1)
    require(streamNode.out.length == 1)

    val in = streamNode.in.head._1
    val out = streamNode.out.head._1

    // instantiate passthrough
    val fir = Module(new genericFIR(genIn, genOut, coeffs))

    // Pass ready and valid from read queue to write queue
    in.ready := fir.io.in.ready
    fir.io.in.valid := in.valid

    // cast UInt to T
    fir.io.in.bits := in.bits.data.asTypeOf(genericFIRBundle(genIn))

    fir.io.out.ready := out.ready
    out.valid := fir.io.out.valid

    // cast T to UInt
    out.bits.data := fir.io.out.bits.asUInt
  }

}

class TLGenericFIRBlock[T<:Data:Ring]
(
  val genIn: T,
  val genOut: T,
  coeffs: Seq[T]
)(implicit p: Parameters) extends GenericFIRBlock[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle, T](genIn, genOut, coeffs) with TLDspBlock

class TLGenericFIRThing[T<:Data:Ring] (genIn: T, genOut: T, coeffs: Seq[T], depth: Int)(implicit p: Parameters)
  extends LazyModule {
  val writeQueue = LazyModule(new TLWriteQueue(depth))
  val fir = LazyModule(new TLGenericFIRBlock(genIn, genOut, coeffs))
  val readQueue = LazyModule(new TLReadQueue(depth))

  // connect streamNodes of queues and FIR
  readQueue.streamNode := fir.streamNode := writeQueue.streamNode

  lazy val module = new LazyModuleImp(this)
}

trait HasPeripheryTLUIntTestFIR extends BaseSubsystem {
  val fir = LazyModule(new TLGenericFIRThing(UInt(8.W), UInt(12.W), Seq(1.U, 2.U, 3.U), 8))

  pbus.toVariableWidthSlave(Some("firWrite")) { fir.writeQueue.mem.get }
  pbus.toVariableWidthSlave(Some("firRead")) { fir.readQueue.mem.get }
}
