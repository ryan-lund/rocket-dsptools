//// See LICENSE for license details.
//
package rocketdsptools

import chisel3._
import chisel3.core._
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

// Simple passthrough to use as testbed sanity check

class Passthrough[T<:Data:Ring](gen: T) extends Module {
    val io = IO(new Bundle {
       val in =  Input(gen)
       val out = Output(gen)
    })
    io.in := io.out
}

/**
  * Make DspBlock wrapper for Passthrough
  * @param cordicParams parameters for cordic
  * @param ev$1
  * @param ev$2
  * @param ev$3
  * @param p
  * @tparam D
  * @tparam U
  * @tparam EO
  * @tparam EI
  * @tparam B
  * @tparam T Type parameter for passthrough, i.e. FixedPoint or DspReal
  */
abstract class PassthroughBlock[D, U, EO, EI, B<:Data, T<:Data:Ring]
(
  proto: T
)(implicit p: Parameters) extends DspBlock[D, U, EO, EI, B] {
  val streamNode = AXI4StreamIdentityNode()
  val mem = None

  lazy val module = new LazyModuleImp(this) {
    require(streamNode.in.length == 1)
    require(streamNode.out.length == 1)

    val in = streamNode.in.head._1
    val out = streamNode.out.head._1

    // instantiate passthrough
    val passthrough = Module(new Passthrough(proto))

    // Pass ready and valid from read queue to write queue
    // TODO: verify this assignment is valid and works
    
    in.ready := out.ready
    out.valid := in.valid

    // cast UInt to T
    passthrough.io.in := in.bits.data.asTypeOf(proto)

    // cast T to UInt
    out.bits.data := passthrough.io.out.asUInt
  }
}

/**
  * TLDspBlock specialization of Passthrough
  * @param cordicParams parameters for passthrough
  * @param ev$1
  * @param ev$2
  * @param ev$3
  * @param p
  * @tparam T Type parameter for passthrough data type
  */
class TLPassthroughBlock[T<:Data:Ring]
(
  val proto: T
)(implicit p: Parameters) extends
PassthroughBlock[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle, T](proto) with TLDspBlock

/**
  * This doesn't work right now, TLChain seems to be broken. This is the "right way" to connect several DspBlocks and
  * add interconnect
  * @param depth
  * @param ev$1
  * @param ev$2
  * @param ev$3
  * @param p
  * @tparam T
  */
/*
class PassthroughChain[T<:Data:Ring]
(
  val depth: Int = 8,
)(implicit p: Parameters) extends TLChain(
  Seq(
    {implicit p: Parameters => { val writeQueue = LazyModule(new TLWriteQueue(depth)); writeQueue}},
    {implicit p: Parameters => { val passthrough = LazyModule(new TLPassthroughBlock()); passthrough}},
    {implicit p: Parameters => { val readQueue = LazyModule(new TLReadQueue(depth)); readQueue}},
  )
)
*/

/**
  * PassthroughChain is the "right way" to do this, but the dspblocks library seems to be broken.
  * In the interim, this should work.
  * @param depth depth of queues
  * @param ev$1
  * @param ev$2
  * @param ev$3
  * @param p
  * @tparam T Type parameter for passthrough, i.e. FixedPoint or DspReal
  */
class TLPassthroughThing[T<:Data:Ring]
(
  val proto: T,
  val depth: Int = 8,
)(implicit p: Parameters) extends LazyModule {
  // instantiate lazy modules
  val writeQueue = LazyModule(new TLWriteQueue(depth))
  val passthrough = LazyModule(new TLPassthroughBlock(proto))
  val readQueue = LazyModule(new TLReadQueue(depth))

  // connect streamNodes of queues and cordic
  readQueue.streamNode := passthrough.streamNode := writeQueue.streamNode

  lazy val module = new LazyModuleImp(this)
}


trait HasPeripheryTLUIntPassthrough { this: BaseSubsystem =>
  implicit val p: Parameters

  private val portName = "passthrough"

  val passthrough = LazyModule(new TLPassthroughThing(UInt(32.W), 8)(p))

  pbus.toVariableWidthSlave(Some(portName)) { passthrough.node }
}

trait HasPeripheryTLUIntPassthroughModuleImp extends LazyModuleImp {
  implicit val p: Parameters
  val outer: HasPeripheryTLUIntPassthrough

  val passthroughout = IO(Output(Bool()))

  passthroughout := outer.passthrough.module.io.out
}