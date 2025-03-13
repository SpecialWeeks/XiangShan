package utils

import chisel3._
import chisel3.util.experimental.BoringUtils

import scala.collection.mutable.ListBuffer

class PerfEventBundle extends Bundle {
  val value: UInt = UInt(64.W)
}

object HardenXSPerfAccumulate {
  private val enabled = true
  private val instances = ListBuffer.empty[(Data, String)]

  def apply[T <: Data](
                        name: String,
                        perfCnt: T,
                        wire: Boolean = false // perfCnt direct connection
                      ): Unit = {
    if (enabled) {
      val id = register(perfCnt, name)
      println(s"# Hardened Counter $id: $name")

      val helper = Module(new LogPerfHelper)
      val perfClean = helper.io.clean

      val counter = RegInit(0.U(64.W)).suggestName(name + "Counter")
      val next_counter = WireInit(0.U(64.W)).suggestName(name + "Next")
      next_counter := counter + perfCnt.asTypeOf(UInt(64.W))
      counter := Mux(perfClean, 0.U, next_counter)

      val probe = WireInit(0.U(64.W))
      if (wire)
        probe := perfCnt.asTypeOf(UInt(64.W))
      else
        probe := counter
      BoringUtils.addSource(probe, name)
    }
  }

  def reclaim(): Vec[PerfEventBundle] = {
    lazy val io_perf: Vec[PerfEventBundle] = IO(Output(Vec(instances.length, new PerfEventBundle)))
    io_perf.zip(instances).foreach{
      case (perf, (_, name)) =>
        val portal = WireInit(0.U(64.W))
        BoringUtils.addSink(portal, name)
        perf.value := portal
    }
    FileRegisters.add("HardenPerf.cpp", generateCppParser(true))
    FileRegisters.add("DSEMacro.v", generateVerilog())
    io_perf
  }

  def register[T <: Data](gen: T, name: String): Int = {
    val id = instances.length
    val element = (gen, name)
    instances += element
    id
  }

  def generateCppParser(pldm: Boolean = false): String = {
    val parserCpp = ListBuffer.empty[String]
    parserCpp +=
      s"""
         |#include <cstdint>
         |#include <vector>
         |#include <string>
         |""".stripMargin

    if (pldm) {
      parserCpp += "#include \"perfprocess.h\""
    } else {
      parserCpp +=
        s"""
          |#include "verilated.h"
          |#include "VSimTop.h"
          |""".stripMargin
    }

    if (pldm) {}
    else {
      parserCpp +=
        s"""
          |std::vector<uint64_t> getIOPerfCnts(VSimTop *dut_ptr) {
          |    std::vector<uint64_t> perfCnts;
          |""".stripMargin
      for (i <- instances.indices) {
        parserCpp += s"    perfCnts.push_back(dut_ptr->io_perf_${i}_value);"
      }
      parserCpp +=
        s"""
          |    return perfCnts;
          |}
          |""".stripMargin

      parserCpp +=
        s"""
          |std::vector<std::string> getIOPerfNames() {
          |    std::vector<std::string> perfNames;
          |""".stripMargin
      instances.foreach {
        case (_, s) =>
          parserCpp += s"    perfNames.push_back(\"$s\");"
      }
      parserCpp +=
        s"""
          |    return perfNames;
          |}
          |""".stripMargin
    }
  
    if (pldm) {
      parserCpp += "int get_perfCnt_id(std::string perfName) {"
      
      for (i <- instances.indices) {
        parserCpp += s"  if (perfName == \"${instances(i)._2}\") { return $i; }"
      }
      parserCpp += "}"
    
      parserCpp += "extern Perfprocess* perfprocess;"
      parserCpp += "extern \"C\" void pushIOPerfCnts("
      for (i <- instances.indices) {
        parserCpp += s"  uint64_t io_perf_${i}_value, "
      }
      parserCpp += "  char dse_reset_valid) {"
      parserCpp += "  perfprocess->perfCnts.clear();"
      for (i <- instances.indices) {
        parserCpp += s"  perfprocess->perfCnts.push_back(io_perf_${i}_value);"
      }
      parserCpp += "}"
    }
  
    parserCpp.mkString("\n")
  }

  def generateVerilog(): String = {
    val parserVerilog = ListBuffer.empty[String]
    
    parserVerilog += "`define DEFINE_HARDEN_PERFCNT \\"
    for (i <- instances.indices) {
      parserVerilog += s"  wire [63:0] io_perf_${i}_value;\\"
    }
    parserVerilog += ""

    parserVerilog += "`define INPUT_HARDEN_PERFCNT \\"
    for (i <- instances.indices) {
      parserVerilog += s"  input wire [63:0] io_perf_${i}_value,\\"
    }
    parserVerilog += ""

    parserVerilog += "`define PERFCNT_CONNECTIONS \\"
    for (i <- instances.indices) {
      parserVerilog += s"  .io_perf_${i}_value   (io_perf_${i}_value), \\"
    }
    parserVerilog += ""

    parserVerilog += "`define DECLEAR_PUSH_HARDEN_PERFCNT \\"
    parserVerilog += s"import \"DPI-C\" function void pushIOPerfCnts( \\"
    for (i <- instances.indices) {
      parserVerilog += s"  longint io_perf_${i}_value, \\"
    }
    parserVerilog += "  byte dse_reset_valid);"
    parserVerilog += ""

    parserVerilog += "`define PUSH_HARDEN_PERFCNT \\"
    parserVerilog += "  pushIOPerfCnts( \\"
    for (i <- instances.indices) {
      parserVerilog += s"    io_perf_${i}_value, \\"
    }
    parserVerilog += "    dse_reset_valid \\\n);"
    
    parserVerilog.mkString("\n")
  }

}