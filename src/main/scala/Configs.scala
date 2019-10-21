package rocketdsptools

import chisel3._

import freechips.rocketchip.config.{Config}

class PassthroughThingRocketConfig extends Config(
  new WithPassthroughThingTop ++                                        // use top with tilelink-controlled passthrough
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)