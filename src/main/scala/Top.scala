package rocketdsptools

import chisel3._

import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util.DontTouch

import testchipip._

import utilities.{System, SystemModule}

import sifive.blocks.devices.gpio._

// ------------------------------------
// BOOM and/or Rocket Top Level Systems
// ------------------------------------

class Top(implicit p: Parameters) extends System
  with HasNoDebug
  with HasPeripherySerial {
  override lazy val module = new TopModule(this)
}

class TopModule[+L <: Top](l: L) extends SystemModule(l)
  with HasNoDebugModuleImp
  with HasPeripherySerialModuleImp
  with DontTouch

//---------------------------------------------------------------------------------------------------------

class TopWithUIntPassthrough(implicit p: Parameters) extends Top
  with HasPeripheryUIntPassthrough {
  override lazy val module = new TopWithPassthroughThingModule(this)
}

class TopWithUIntPassthroughModule(l: TopWithUIntPassthrough) extends TopModule(l)
  with HasPeripheryUIntPassthroughModuleImp

//---------------------------------------------------------------------------------------------------------

class TopWithDTM(implicit p: Parameters) extends System
{
  override lazy val module = new TopWithDTMModule(this)
}

class TopWithDTMModule[+L <: TopWithDTM](l: L) extends SystemModule(l)