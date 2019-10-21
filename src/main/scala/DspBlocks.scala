package rocketdsptools

import chisel3._
import chisel3.util._
import dspblocks._
import dsptools.numbers._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._


trait HasPeripheryPassthroughThing { this: BaseSubsystem =>
  implicit val p: Parameters

  private val portName = "passthrough"

  val passthrough = LazyModule(new PassthroughThing()(p))

  pbus.toVariableWidthSlave(Some(portName)) { passthrough.node }
}

trait HasPeripheryPassthroughThingModuleImp extends LazyModuleImp {
  implicit val p: Parameters
  val outer: HasPeripheryPassthroughThing

  val passthroughout = IO(Output(Bool()))

  passthroughout := outer.passthrough.module.io.out
}

/**
  * The memory interface writes entries into the queue.
  * They stream out the streaming interface
  * @param depth number of entries in the queue
  * @param streamParameters parameters for the stream node
  * @param p
  */
abstract class WriteQueue
(
  val depth: Int = 8,
  val streamParameters: AXI4StreamMasterParameters = AXI4StreamMasterParameters()
)(implicit p: Parameters) extends LazyModule with HasCSR {
  // stream node, output only
  val streamNode = AXI4StreamMasterNode(streamParameters)

  lazy val module = new LazyModuleImp(this) {
    require(streamNode.out.length == 1)

    // get the output bundle associated with the AXI4Stream node
    val out = streamNode.out(0)._1
    // width (in bits) of the output interface
    val width = out.params.n * 8
    // instantiate a queue
    val queue = Module(new Queue(UInt(out.params.dataBits.W), depth))
    // connect queue output to streaming output
    out.valid := queue.io.deq.valid
    out.bits.data := queue.io.deq.bits
    // don't use last
    out.bits.last := false.B
    queue.io.deq.ready := out.ready

    regmap(
      // each write adds an entry to the queue
      0x0 -> Seq(RegField.w(width, queue.io.enq)),
      // read the number of entries in the queue
      (width+7)/8 -> Seq(RegField.r(width, queue.io.count)),
    )
  }
}

/**
  * TLDspBlock specialization of WriteQueue
  * @param depth number of entries in the queue
  * @param csrAddress address range for peripheral
  * @param beatBytes beatBytes of TL interface
  * @param p
  */
class TLWriteQueue
(
  depth: Int = 8,
  csrAddress: AddressSet = AddressSet(0x2000, 0xff),
  beatBytes: Int = 8,
)(implicit p: Parameters) extends WriteQueue(depth) with TLHasCSR {
  val devname = "tlQueueIn"
  val devcompat = Seq("ucb-art", "dsptools")
  val device = new SimpleDevice(devname, devcompat) {
    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping)
    }
  }
  // make diplomatic TL node for regmap
  override val mem = Some(TLRegisterNode(address = Seq(csrAddress), device = device, beatBytes = beatBytes))
}

/**
  * The streaming interface adds elements into the queue.
  * The memory interface can read elements out of the queue.
  * @param depth number of entries in the queue
  * @param streamParameters parameters for the stream node
  * @param p
  */
abstract class ReadQueue
(
  val depth: Int = 8,
  val streamParameters: AXI4StreamSlaveParameters = AXI4StreamSlaveParameters()
)(implicit p: Parameters) extends LazyModule with HasCSR {
  val streamNode = AXI4StreamSlaveNode(streamParameters)

  lazy val module = new LazyModuleImp(this) {
    require(streamNode.in.length == 1)

    // get the input associated with the stream node
    val in = streamNode.in(0)._1
    // make a Decoupled[UInt] that RegReadFn can do something with
    val out = Wire(Decoupled(UInt()))
    // get width of streaming input interface
    val width = in.params.n * 8
    // instantiate a queue
    val queue = Module(new Queue(UInt(in.params.dataBits.W), depth))
    // connect input to the streaming interface
    queue.io.enq.valid := in.valid
    queue.io.enq.bits := in.bits.data
    in.ready := queue.io.enq.ready
    // connect output to wire
    out.valid := queue.io.deq.valid
    out.bits := queue.io.deq.bits
    queue.io.deq.ready := out.ready

    regmap(
      // map the output of the queue
      0x0         -> Seq(RegField.r(width, RegReadFn(out))),
      // read the number of elements in the queue
      (width+7)/8 -> Seq(RegField.r(width, queue.io.count)),
    )
  }
}

/**
  * TLDspBlock specialization of ReadQueue
  * @param depth number of entries in the queue
  * @param csrAddress address range
  * @param beatBytes beatBytes of TL interface
  * @param p
  */
class TLReadQueue
(
  depth: Int = 8,
  csrAddress: AddressSet = AddressSet(0x2100, 0xff),
  beatBytes: Int = 8
)(implicit p: Parameters) extends ReadQueue(depth) with TLHasCSR {
  val devname = "tlQueueOut"
  val devcompat = Seq("ucb-art", "dsptools")
  val device = new SimpleDevice(devname, devcompat) {
    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping)
    }
  }
  // make diplomatic TL node for regmap
  override val mem = Some(TLRegisterNode(address = Seq(csrAddress), device = device, beatBytes = beatBytes))

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

)(implicit p: Parameters) extends DspBlock[D, U, EO, EI, B] {
  val streamNode = AXI4StreamIdentityNode()
  val mem = None

  lazy val module = new LazyModuleImp(this) {
    require(streamNode.in.length == 1)
    require(streamNode.out.length == 1)

    val in = streamNode.in.head._1
    val out = streamNode.out.head._1

    // instantiate passthrough
    val passthrough = Module(new Passthrough(T))

    // Pass ready and valid from read queue to write queue
    // TODO: verify this assignment is valid and works
    
    in.ready := out.ready
    out.valid := in.valid

    // cast UInt to T
    passthrough.io.in := in.bits.data.asTypeOf(T)

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

)(implicit p: Parameters) extends
  PassthroughBlock[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle, T]()
  with TLDspBlock

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
class PassthroughThing[T<:Data:Ring]
(
  val depth: Int = 8,
)(implicit p: Parameters) extends LazyModule {
  // instantiate lazy modules
  val writeQueue = LazyModule(new TLWriteQueue(depth))
  val passthrough = LazyModule(new TLPassthroughBlock())
  val readQueue = LazyModule(new TLReadQueue(depth))

  // connect streamNodes of queues and cordic
  readQueue.streamNode := passthrough.streamNode := writeQueue.streamNode

  lazy val module = new LazyModuleImp(this)
}
