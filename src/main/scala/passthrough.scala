//// See LICENSE for license details.
//
package rocketdsptools

import chisel3.core._
import chisel3.{Bundle, Module}
import dsptools.numbers._

// Simple passthrough to use as testbed sanity check

class passthrough[T<:Data:Ring](gen: T) extends Module {
    val io = IO(new Bundle {
       val in =  Input(gen)
       val out = Output(gen)
    })
    io.in := io.out
}