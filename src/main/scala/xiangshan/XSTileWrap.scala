/***************************************************************************************
* Copyright (c) 2024 Beijing Institute of Open Source Chip (BOSC)
* Copyright (c) 2024 Institute of Computing Technology, Chinese Academy of Sciences
*
* XiangShan is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*
* See the Mulan PSL v2 for more details.
***************************************************************************************/

package xiangshan

import aia._
import chisel3._
import chisel3.util._
import coupledL2.tl2chi.AsyncPortIO
import coupledL2.tl2chi.CHIAsyncBridgeSource
import coupledL2.tl2chi.PortIO
import device._
import freechips.rocketchip.devices.debug
import freechips.rocketchip.devices.debug.ClockedDMIIO
import freechips.rocketchip.devices.debug.ExportDebug
import freechips.rocketchip.devices.debug.TLDebugModule
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import org.chipsalliance.cde.config._
import system.HasSoCParameter
//import device.{IMSICAsync, MsiInfoBundle}
import coupledL2.tl2chi.{AsyncPortIO, CHIAsyncBridgeSource, PortIO}
import utility.sram.SramBroadcastBundle
import utility.{DFTResetSignals, IntBuffer, ResetGen}
import xiangshan.backend.trace.TraceCoreInterface

// This module is used for XSNoCTop for async time domain and divide different
// voltage domain. Everything in this module should be in the core clock domain
// and higher voltage domain.
class XSTileWrap()(implicit p: Parameters) extends LazyModule
    with HasXSParameter
    with HasSoCParameter {
  override def shouldBeInlined: Boolean = false

  val tile = LazyModule(new XSTile())

  // interrupts sync
  val clintIntNode = IntIdentityNode()
  val debugIntNode = IntIdentityNode()
  val plicIntNode  = IntIdentityNode()
  val beuIntNode   = IntIdentityNode()
  val nmiIntNode   = IntIdentityNode()
  tile.clint_int_node := IntBuffer(3, cdc = true) := clintIntNode
  tile.debug_int_node := IntBuffer(3, cdc = true) := debugIntNode
  tile.plic_int_node :*= IntBuffer(3, cdc = true) :*= plicIntNode
  tile.nmi_int_node := IntBuffer(3, cdc = true) := nmiIntNode
  beuIntNode := IntBuffer() := tile.beu_int_source

  // seperate DebugModule bus
  val EnableDMAsync = EnableDMAsyncBridge.isDefined
  println(s"SeperateDMBus = $SeperateDMBus")
  println(s"EnableDMAsync = $EnableDMAsync")
  // asynchronous bridge source node
  val dmAsyncSourceOpt = Option.when(SeperateDMBus && EnableDMAsync)(LazyModule(new TLAsyncCrossingSource()))
  dmAsyncSourceOpt.foreach(_.node := tile.sep_dm_opt.get)
  // synchronous source node
  val dmSyncSourceOpt = Option.when(SeperateDMBus && !EnableDMAsync)(TLTempNode())
  dmSyncSourceOpt.foreach(_ := tile.sep_dm_opt.get)

  class XSTileWrapImp(wrapper: LazyModule) extends LazyRawModuleImp(wrapper) {
    val clock = IO(Input(Clock()))
    val reset = IO(Input(AsyncReset()))
    val noc_reset = EnableCHIAsyncBridge.map(_ => IO(Input(AsyncReset())))
    val soc_reset = Option.when(!ClintAsyncFromCJ)(IO(Input(AsyncReset())))
    val i = Option.when(CHIAsyncFromCJ)(IO(new Bundle { // for customer J
      val dft = Input(new Bundle {
        val icg_scan_en = Bool()
        val scan_enable = Bool()
      })
    }))
    val io = IO(new Bundle {
      val hartId            = Input(UInt(hartIdLen.W))
      val msiinfo           = new MSITransBundle(aia.IMSICParams())
      val reset_vector      = Input(UInt(PAddrBits.W))
      val cpu_halt          = Output(Bool())
      val cpu_crtical_error = Output(Bool())
      // ==UseDMInTop start: 1- DebugModule is integrated in XSTOP, only customer J need it,other 0.==
      val hartResetReq  = Option.when(!UseDMInTop)(Input(Bool()))
      val hartIsInReset = Option.when(!UseDMInTop)(Output(Bool()))
      val dm = Option.when(UseDMInTop)(new Bundle {
        val dmi     = (!p(ExportDebug).apb).option(Flipped(new ClockedDMIIO()))
        val ndreset = Output(Bool()) // output of top,to request that soc can reset system logic exclude debug.
      })
      // ==UseDMInTop end ==
      val traceCoreInterface = new TraceCoreInterface
      val debugTopDown = new Bundle {
        val robHeadPaddr = Valid(UInt(PAddrBits.W))
        val l3MissMatch  = Input(Bool())
      }

      val l3Miss = Input(Bool())
      val chi = EnableCHIAsyncBridge match {
        case Some(param) => if (CHIAsyncFromCJ) new CHIAsyncIOCJ() else new AsyncPortIO(param)
        case None        => new PortIO
      }
      val nodeID = if (enableCHI) Some(Input(UInt(NodeIDWidth.W))) else None
      val clintTime = EnableClintAsyncBridge match {
        case Some(param) =>
          if (ClintAsyncFromCJ) Input(ValidIO(UInt(64.W))) else Flipped(new AsyncBundle(UInt(64.W), param))
        case None => Input(ValidIO(UInt(64.W)))
      }
      val dft = if(hasMbist) Some(Input(new SramBroadcastBundle)) else None
      val dft_reset = if(hasMbist) Some(Input(new DFTResetSignals())) else None
      val l2_flush_en = Option.when(EnablePowerDown) (Output(Bool()))
      val l2_flush_done = Option.when(EnablePowerDown) (Output(Bool()))
      val pwrdown_req_n = Option.when(EnablePowerDown) (Input (Bool()))
      val pwrdown_ack_n = Option.when(EnablePowerDown) (Output (Bool()))
      val iso_en = Option.when(EnablePowerDown) (Input (Bool()))
    })
//    val reset_sync_ip = withClockAndReset(clock, reset)(ResetGen())
//    val noc_reset_sync = EnableCHIAsyncBridge.map(_ => reset_sync_ip)
//    val soc_reset_sync = reset_sync_ip
    val hartResetReq = Wire(Bool()) // derive from io.hartResetReq or debugwrapper in top
    io.hartResetReq.foreach(iohartResetReq => hartResetReq := iohartResetReq)

    val reset_sync = withClockAndReset(clock, (reset.asBool || io.hartResetReq).asAsyncReset)(ResetGen(2, io.dft_reset))
    val noc_reset_sync = EnableCHIAsyncBridge.map(_ => withClockAndReset(clock, noc_reset.get)(ResetGen(2, io.dft_reset)))
    val soc_reset_sync = withClockAndReset(clock, soc_reset.get)(ResetGen(2, io.dft_reset))

    // override LazyRawModuleImp's clock and reset
    childClock := clock
    childReset := reset_sync

    tile.module.io.hartId := io.hartId
    // connect msi info io with xstile
    // start :TBD zhaohong ,wait tile update the msi interface,

    tile.module.io.msiInfo.valid := io.msiinfo.vld_req
    io.msiinfo.vld_ack           := io.msiinfo.vld_req // TODO
    tile.module.io.msiInfo.bits.info := io.msiinfo.data // 1.U // TODO for compile error since type donot match io.msiinfo.data
    // end :TBD zhaohong

    tile.module.io.reset_vector := io.reset_vector
    tile.module.io.dft.zip(io.dft).foreach({case(a, b) => a := b})
    tile.module.io.dft_reset.zip(io.dft_reset).foreach({case(a, b) => a := b})
    io.cpu_halt := tile.module.io.cpu_halt
    io.cpu_crtical_error := tile.module.io.cpu_crtical_error
    io.hartIsInReset.foreach(_ := tile.module.io.hartIsInReset)
    io.traceCoreInterface <> tile.module.io.traceCoreInterface
    io.debugTopDown <> tile.module.io.debugTopDown
    tile.module.io.l3Miss := io.l3Miss
    tile.module.io.nodeID.foreach(_ := io.nodeID.get)
    io.l2_flush_en.foreach { _ := tile.module.io.l2_flush_en.getOrElse(false.B) }
    io.l2_flush_done.foreach { _ := tile.module.io.l2_flush_done.getOrElse(false.B) }
    io.pwrdown_ack_n.foreach { _ := true.B }

    // instance :TL DebugModule
//    val debugModule = Option.when(UseDMInTop){
//      val l_debugModule = LazyModule(new DebugModule(numCores = NumCores, defDTM = false)(p))
////      l_debugModule.debug.node := TLBuffer() := TLFragmenter(8,32) := xbar // TLXbar())
//      l_debugModule.debug.node := TLXbar()
//      l_debugModule
//    }
    val l_debugModule = Option.when(UseDMInTop)(LazyModule(new DebugModule(numCores = NumCores, defDTM = false)(p)))
//    val debugModule = Option.when(UseDMInTop)(Module(l_debugModule.get.module))
    val debugModule = l_debugModule.map(l_debugModule => Module(l_debugModule.module))
    l_debugModule.foreach(_.debug.node := TLXbar()) // TBD connect with xstile.tl.out
    //    debugModule.foreach(_.module.debug.dmInner.dmInner.sb2tlOpt.foreach { sb2tl =>
    //      io.dm.mbus := sb2tl.node    //master node connect about debug
    //    })
    //    val io = IO(new DebugModuleIO)
    debugModule.foreach { debugModule =>
      debugModule.io.reset                   := reset
      debugModule.io.clock                   := clock
      debugModule.io.debugIO.clock           := clock
      debugModule.io.debugIO.reset           := reset
      debugModule.io.resetCtrl.hartIsInReset := tile.module.io.hartIsInReset
      hartResetReq                           := debugModule.io.resetCtrl.hartResetReq.get
      io.dm.foreach { iodm =>
        iodm.ndreset := debugModule.io.debugIO.ndreset
        iodm.dmi.map(_ <> debugModule.io.debugIO.clockeddmi.get)
      }
    }
    // CLINT Async Queue Sink
    (EnableClintAsyncBridge, ClintAsyncFromCJ) match {
      case (Some(param), false) =>
        val sink = withClockAndReset(clock, soc_reset_sync)(Module(new AsyncQueueSink(UInt(64.W), param)))
        sink.io.async <> io.clintTime
        sink.io.deq.ready := true.B
        tile.module.io.clintTime.valid := sink.io.deq.valid
        tile.module.io.clintTime.bits := sink.io.deq.bits
      case (Some(param), true) =>   //clint async proc ip is from customer J
        val sink = withClockAndReset(clock, soc_reset_sync)(Module(new ClintAsyncCJ()))
        sink.io.i_time <> io.clintTime
        tile.module.io.clintTime := sink.io.o_time
      case _ =>
        tile.module.io.clintTime := io.clintTime
    }
    // CHI Async Queue Source
    (EnableCHIAsyncBridge, CHIAsyncFromCJ) match {
      case (Some(param), true) => // chiasync bridge can be provided by customer J.
        val source = withClockAndReset(clock, noc_reset_sync.get)(Module(new CHIAsyncDEVCJ()))
        source.i.dft.icg_scan_en := i.get.dft.icg_scan_en
        source.i.dft.scan_enable := i.get.dft.scan_enable
        source.io.chi <> tile.module.io.chi.get
        io.chi <> source.io.cdb
      case (Some(param), false) =>
        val source = withClockAndReset(clock, noc_reset_sync.get)(Module(new CHIAsyncBridgeSource(param)))
        source.io.enq <> tile.module.io.chi.get
        io.chi <> source.io.async
      case _ =>
        require(enableCHI)
        io.chi <> tile.module.io.chi.get
    }

    // Seperate DebugModule TL Async Queue Source
    if (SeperateDMBus && EnableDMAsync) {
      dmAsyncSourceOpt.get.module.clock := clock
      dmAsyncSourceOpt.get.module.reset := soc_reset_sync
    }

    withClockAndReset(clock, reset_sync) {
      // Modules are reset one by one
      // reset ----> SYNC --> XSTile
      val resetChain = Seq(Seq(tile.module))
      ResetGen(resetChain, reset_sync, !debugOpts.FPGAPlatform, io.dft_reset)
    }
    dontTouch(io.hartId)
//    dontTouch(io.msiInfo)
  }
  lazy val module = new XSTileWrapImp(this)
}
