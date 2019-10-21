package rocketdsptools

import chisel3._

import freechips.rocketchip.config.{Config}

class PassthroughThingRocketConfig extends Config(
  new WithPWMTop ++                                        // use top with tilelink-controlled PWM
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)