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
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem._

class GenericFIRCellBundle[T<:Data:Ring](genIn:T, genOut:T) extends Bundle {
  val data: T = genIn.cloneType
  val carry: T = genOut.cloneType

  override def cloneType: this.type = GenericFIRCellBundle(genIn, genOut).asInstanceOf[this.type]
}
object GenericFIRCellBundle {
    def apply[T<:Data:Ring](genIn:T, genOut:T): GenericFIRCellBundle[T] = new GenericFIRCellBundle(genIn, genOut)
}

class GenericFIRCellIO[T<:Data:Ring](genIn:T, genOut:T) extends Bundle {
    val coeff = Input(genIn.cloneType)
    val in = Flipped(Decoupled(GenericFIRCellBundle(genIn, genOut)))
    val out = Decoupled(GenericFIRCellBundle(genIn, genOut))
}
object GenericFIRCellIO {
    def apply[T<:Data:Ring](genIn:T, genOut:T): GenericFIRCellIO[T] = new GenericFIRCellIO(genIn, genOut)
}

class GenericFIRBundle[T<:Data:Ring](proto: T) extends Bundle {
  val data: T = proto.cloneType

  override def cloneType: this.type = GenericFIRBundle(proto).asInstanceOf[this.type]
}
object GenericFIRBundle {
  def apply[T<:Data:Ring](proto: T): GenericFIRBundle[T] = new GenericFIRBundle(proto)
}

class GenericFIRIO[T<:Data:Ring](genIn:T, genOut:T) extends Bundle {
    val in = Flipped(Decoupled(GenericFIRBundle(genIn)))
    val out = Decoupled(GenericFIRBundle(genOut))
}
object GenericFIRIO {
    def apply[T<:Data:Ring](genIn:T, genOut:T): GenericFIRIO[T] = new GenericFIRIO(genIn, genOut)
}

// A generic FIR filter
class GenericFIR[T<:Data:Ring](genIn:T, genOut:T, coeffs: Seq[T]) extends Module {
  val io = IO(GenericFIRIO(genIn, genOut))

  // Construct a vector of GenericFIRDirectCells
  val directCells = Seq.fill(coeffs.length){ Module(new GenericFIRDirectCell(genIn, genOut)).io }

  // connect coefficients
  for ((cell, coeff) <- directCells.zip(coeffs)) {
    cell.coeff := coeff
  }

  // connect input to first cell.
  directCells(0).in.bits.data := io.in.bits
  directCells(0).in.bits.carry := Ring[T].zero // first cell has no carry
  directCells(0).in.valid := io.in.valid
  io.in.ready := directCells(0).in.ready

  // connect adjacent cells
  for ((prev, next) <- directCells.zip(directCells.tail)) {
    next.in.bits := prev.out.bits
    next.in.valid := prev.out.valid
    prev.out.ready := next.out.ready
  }

  // connect output to last cell
  io.out.bits := directCells.last.out.bits.carry
  io.out.valid := directCells.last.out.valid
  directCells.last.out.ready := io.out.ready
}

// A generic FIR direct cell used to construct a larger direct FIR chain
//
//   in ----- [z^-1]-- out
//              |
//   coeff ----[*]
//              |
//   carryIn --[+]-- carryOut
//
class GenericFIRDirectCell[T<:Data:Ring](genIn: T, genOut: T) extends Module {
  val io = IO(GenericFIRCellIO(genIn, genOut))

  // Passthrough ready
  io.in.ready := io.out.ready

  // TODO reason about ready/valid
  // Describe what's going on
  val validReg = RegNext(io.in.fire(), init = false.B)
  io.out.valid := validReg

  // Delay input by 1 cycle to output
  val inputReg = Reg(genIn.cloneType)
  when (io.out.ready) {
    inputReg := io.in.bits.data
  }
  io.out.bits.data := inputReg

  // Compute multiply + carry.
  // This uses the ring implementation for + and *, i.e.
  // (a * b) maps to (Ring[T].prod(a, b)) for whichever T you use
  io.out.bits.carry := io.in.bits.data * io.coeff + io.in.bits.carry
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
    val fir = Module(new GenericFIR(genIn, genOut, coeffs))

    // Pass ready and valid from read queue to write queue
    in.ready := fir.io.in.ready
    fir.io.in.valid := in.valid

    // cast UInt to T
    fir.io.in.bits := in.bits.data.asTypeOf(GenericFIRBundle(genIn))

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
)(implicit p: Parameters) extends
GenericFIRBlock[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle, T](
  genIn, genOut, coeffs
) with TLDspBlock

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
