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

class TopWithPassthroughThing(implicit p: Parameters) extends Top
  with HasPeripheryPassthroughThing {
  override lazy val module = new TopWithPassthroughThingModule(this)
}

class TopWithPassthroughThingModule(l: TopWithPassthroughThing) extends TopModule(l)
  with HasPeripheryPassthroughThingModuleImp

//---------------------------------------------------------------------------------------------------------
