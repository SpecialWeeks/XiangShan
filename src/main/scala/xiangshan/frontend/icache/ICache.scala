/***************************************************************************************
* Copyright (c) 2024 Beijing Institute of Open Source Chip (BOSC)
* Copyright (c) 2020-2024 Institute of Computing Technology, Chinese Academy of Sciences
* Copyright (c) 2020-2021 Peng Cheng Laboratory
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
*
*
* Acknowledgement
*
* This implementation is inspired by several key papers:
* [1] Glenn Reinman, Brad Calder, and Todd Austin. "[Fetch directed instruction prefetching.]
* (https://doi.org/10.1109/MICRO.1999.809439)" 32nd Annual ACM/IEEE International Symposium on Microarchitecture
* (MICRO). 1999.
***************************************************************************************/

package xiangshan.frontend.icache

import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy.AddressSet
import freechips.rocketchip.diplomacy.IdRange
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.diplomacy.LazyModuleImp
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.BundleFieldBase
import huancun.AliasField
import huancun.PrefetchField
import org.chipsalliance.cde.config.Parameters
import utility._
import utility.mbist.MbistPipeline
import utility.sram.SplittedSRAMTemplate
import utility.sram.SRAMReadBus
import utility.sram.SRAMTemplate
import utility.sram.SRAMWriteBus
import utils._
import xiangshan._
import xiangshan.cache._
import xiangshan.cache.mmu.TlbRequestIO
import xiangshan.frontend._

class ICacheMetadata(implicit p: Parameters) extends ICacheBundle {
  val tag: UInt = UInt(tagBits.W)
}

object ICacheMetadata {
  def apply(tag: Bits)(implicit p: Parameters): ICacheMetadata = {
    val meta = Wire(new ICacheMetadata)
    meta.tag := tag
    meta
  }
}

class ICacheMetaArrayIO(implicit p: Parameters) extends ICacheBundle {
  val write:    DecoupledIO[ICacheMetaWriteBundle] = Flipped(DecoupledIO(new ICacheMetaWriteBundle))
  val read:     DecoupledIO[ICacheReadBundle]      = Flipped(DecoupledIO(new ICacheReadBundle))
  val readResp: ICacheMetaRespBundle               = Output(new ICacheMetaRespBundle)
  val flush:    Vec[Valid[ICacheMetaFlushBundle]]  = Vec(PortNumber, Flipped(ValidIO(new ICacheMetaFlushBundle)))
  val flushAll: Bool                               = Input(Bool())
}

class ICacheMetaArray(implicit p: Parameters) extends ICacheModule with ICacheECCHelper {
  class ICacheMetaEntry(implicit p: Parameters) extends ICacheBundle {
    val meta: ICacheMetadata = new ICacheMetadata
    val code: UInt           = UInt(ICacheMetaCodeBits.W)
  }

  private object ICacheMetaEntry {
    def apply(meta: ICacheMetadata, poison: Bool)(implicit p: Parameters): ICacheMetaEntry = {
      val entry = Wire(new ICacheMetaEntry)
      entry.meta := meta
      entry.code := encodeMetaECC(meta.asUInt, poison)
      entry
    }
  }

  // sanity check
  require(ICacheMetaEntryBits == (new ICacheMetaEntry).getWidth)

  val io: ICacheMetaArrayIO = IO(new ICacheMetaArrayIO)

  private val port_0_read_0 = io.read.valid && !io.read.bits.vSetIdx(0)(0)
  private val port_0_read_1 = io.read.valid && io.read.bits.vSetIdx(0)(0)
  private val port_1_read_1 = io.read.valid && io.read.bits.vSetIdx(1)(0) && io.read.bits.isDoubleLine
  private val port_1_read_0 = io.read.valid && !io.read.bits.vSetIdx(1)(0) && io.read.bits.isDoubleLine

  private val port_0_read_0_reg = RegEnable(port_0_read_0, 0.U.asTypeOf(port_0_read_0), io.read.fire)
  private val port_0_read_1_reg = RegEnable(port_0_read_1, 0.U.asTypeOf(port_0_read_1), io.read.fire)
  private val port_1_read_1_reg = RegEnable(port_1_read_1, 0.U.asTypeOf(port_1_read_1), io.read.fire)
  private val port_1_read_0_reg = RegEnable(port_1_read_0, 0.U.asTypeOf(port_1_read_0), io.read.fire)

  private val bank_0_idx = Mux(port_0_read_0, io.read.bits.vSetIdx(0), io.read.bits.vSetIdx(1))
  private val bank_1_idx = Mux(port_0_read_1, io.read.bits.vSetIdx(0), io.read.bits.vSetIdx(1))

  private val write_bank_0 = io.write.valid && !io.write.bits.bankIdx
  private val write_bank_1 = io.write.valid && io.write.bits.bankIdx

  private val write_meta_bits = ICacheMetaEntry(
    meta = ICacheMetadata(
      tag = io.write.bits.phyTag
    ),
    poison = io.write.bits.poison
  )

  private val tagArrays = (0 until PortNumber) map { bank =>
    val tagArray = Module(new SplittedSRAMTemplate(
      new ICacheMetaEntry(),
      set = nSets / PortNumber,
      way = nWays,
      waySplit = 2,
      dataSplit = 1,
      shouldReset = true,
      holdRead = true,
      singlePort = true,
      withClockGate = true,
      hasMbist = hasMbist
    ))

    // meta connection
    if (bank == 0) {
      tagArray.io.r.req.valid := port_0_read_0 || port_1_read_0
      tagArray.io.r.req.bits.apply(setIdx = bank_0_idx(highestIdxBit, 1))
      tagArray.io.w.req.valid := write_bank_0
      tagArray.io.w.req.bits.apply(
        data = write_meta_bits,
        setIdx = io.write.bits.virIdx(highestIdxBit, 1),
        waymask = io.write.bits.waymask
      )
    } else {
      tagArray.io.r.req.valid := port_0_read_1 || port_1_read_1
      tagArray.io.r.req.bits.apply(setIdx = bank_1_idx(highestIdxBit, 1))
      tagArray.io.w.req.valid := write_bank_1
      tagArray.io.w.req.bits.apply(
        data = write_meta_bits,
        setIdx = io.write.bits.virIdx(highestIdxBit, 1),
        waymask = io.write.bits.waymask
      )
    }

    tagArray
  }
  private val mbistPl = MbistPipeline.PlaceMbistPipeline(1, "MbistPipeIcacheTag", hasMbist)

  private val read_set_idx_next = RegEnable(io.read.bits.vSetIdx, 0.U.asTypeOf(io.read.bits.vSetIdx), io.read.fire)
  private val valid_array       = RegInit(VecInit(Seq.fill(nWays)(0.U(nSets.W))))
  private val valid_metas       = Wire(Vec(PortNumber, Vec(nWays, Bool())))
  // valid read
  (0 until PortNumber).foreach(i =>
    (0 until nWays).foreach(way =>
      valid_metas(i)(way) := valid_array(way)(read_set_idx_next(i))
    )
  )
  io.readResp.entryValid := valid_metas

  io.read.ready := !io.write.valid && !io.flush.map(_.valid).reduce(_ || _) && !io.flushAll &&
    tagArrays.map(_.io.r.req.ready).reduce(_ && _)

  // valid write
  private val way_num = OHToUInt(io.write.bits.waymask)
  when(io.write.valid) {
    valid_array(way_num) := valid_array(way_num).bitSet(io.write.bits.virIdx, true.B)
  }

  XSPerfAccumulate("meta_refill_num", io.write.valid)

  io.readResp.metas <> DontCare
  io.readResp.codes <> DontCare
  private val readMetaEntries = tagArrays.map(port => port.io.r.resp.asTypeOf(Vec(nWays, new ICacheMetaEntry())))
  private val readMetas       = readMetaEntries.map(_.map(_.meta))
  private val readCodes       = readMetaEntries.map(_.map(_.code))

  // TEST: force ECC to fail by setting readCodes to 0
  if (ICacheForceMetaECCError) {
    readCodes.foreach(_.foreach(_ := 0.U))
  }

  when(port_0_read_0_reg) {
    io.readResp.metas(0) := readMetas(0)
    io.readResp.codes(0) := readCodes(0)
  }.elsewhen(port_0_read_1_reg) {
    io.readResp.metas(0) := readMetas(1)
    io.readResp.codes(0) := readCodes(1)
  }

  when(port_1_read_0_reg) {
    io.readResp.metas(1) := readMetas(0)
    io.readResp.codes(1) := readCodes(0)
  }.elsewhen(port_1_read_1_reg) {
    io.readResp.metas(1) := readMetas(1)
    io.readResp.codes(1) := readCodes(1)
  }

  io.write.ready := true.B // TODO : has bug ? should be !io.cacheOp.req.valid

  /*
   * flush logic
   */
  // flush standalone set (e.g. flushed by mainPipe before doing re-fetch)
  when(io.flush.map(_.valid).reduce(_ || _)) {
    (0 until nWays).foreach { w =>
      valid_array(w) := (0 until PortNumber).map { i =>
        Mux(
          // check if set `virIdx` in way `w` is requested to be flushed by port `i`
          io.flush(i).valid && io.flush(i).bits.waymask(w),
          valid_array(w).bitSet(io.flush(i).bits.virIdx, false.B),
          valid_array(w)
        )
      }.reduce(_ & _)
    }
  }

  // flush all (e.g. fence.i)
  when(io.flushAll) {
    (0 until nWays).foreach(w => valid_array(w) := 0.U)
  }

  // PERF: flush counter
  XSPerfAccumulate("flush", io.flush.map(_.valid).reduce(_ || _))
  XSPerfAccumulate("flush_all", io.flushAll)
}

class ICacheDataArrayIO(implicit p: Parameters) extends ICacheBundle {
  val write:    DecoupledIO[ICacheDataWriteBundle] = Flipped(DecoupledIO(new ICacheDataWriteBundle))
  val read:     Vec[DecoupledIO[ICacheReadBundle]] = Flipped(Vec(partWayNum, DecoupledIO(new ICacheReadBundle)))
  val readResp: ICacheDataRespBundle               = Output(new ICacheDataRespBundle)
}

class ICacheDataArray(implicit p: Parameters) extends ICacheModule with ICacheECCHelper with ICacheDataSelHelper {
  class ICacheDataEntry(implicit p: Parameters) extends ICacheBundle {
    val data: UInt = UInt(ICacheDataBits.W)
    val code: UInt = UInt(ICacheDataCodeBits.W)
  }

  private object ICacheDataEntry {
    def apply(data: UInt, poison: Bool)(implicit p: Parameters): ICacheDataEntry = {
      val entry = Wire(new ICacheDataEntry)
      entry.data := data
      entry.code := encodeDataECC(data, poison)
      entry
    }
  }

  val io: ICacheDataArrayIO = IO(new ICacheDataArrayIO)

  /**
    ******************************************************************************
    * data array
    ******************************************************************************
    */
  private val writeDatas   = io.write.bits.data.asTypeOf(Vec(ICacheDataBanks, UInt(ICacheDataBits.W)))
  private val writeEntries = writeDatas.map(ICacheDataEntry(_, io.write.bits.poison).asUInt)

  // io.read() are copies to control fan-out, we can simply use .head here
  private val bankSel  = getBankSel(io.read.head.bits.blkOffset, io.read.head.valid)
  private val lineSel  = getLineSel(io.read.head.bits.blkOffset)
  private val waymasks = io.read.head.bits.waymask
  private val masks    = Wire(Vec(nWays, Vec(ICacheDataBanks, Bool())))
  (0 until nWays).foreach { way =>
    (0 until ICacheDataBanks).foreach { bank =>
      masks(way)(bank) := Mux(
        lineSel(bank),
        waymasks(1)(way) && bankSel(1)(bank).asBool,
        waymasks(0)(way) && bankSel(0)(bank).asBool
      )
    }
  }

  private val dataArrays = (0 until nWays).map { way =>
    val banks = (0 until ICacheDataBanks).map { bank =>
      val sramBank = Module(new SRAMTemplateWithFixedWidth(
        UInt(ICacheDataEntryBits.W),
        set = nSets,
        width = ICacheDataSRAMWidth,
        shouldReset = true,
        holdRead = true,
        singlePort = true,
        withClockGate = false, // enable signal timing is bad, no gating here
        hasMbist = hasMbist
      ))

      // read
      sramBank.io.r.req.valid := io.read(bank % 4).valid && masks(way)(bank)
      sramBank.io.r.req.bits.apply(setIdx =
        Mux(lineSel(bank), io.read(bank % 4).bits.vSetIdx(1), io.read(bank % 4).bits.vSetIdx(0))
      )
      // write
      sramBank.io.w.req.valid := io.write.valid && io.write.bits.waymask(way).asBool
      sramBank.io.w.req.bits.apply(
        data = writeEntries(bank),
        setIdx = io.write.bits.virIdx,
        // waymask is invalid when way of SRAMTemplate <= 1
        waymask = 0.U
      )
      sramBank
    }
    MbistPipeline.PlaceMbistPipeline(1, s"MbistPipeIcacheDataWay${way}", hasMbist)
    banks
  }

  /**
    ******************************************************************************
    * read logic
    ******************************************************************************
    */
  private val masksReg = RegEnable(masks, 0.U.asTypeOf(masks), io.read(0).valid)
  private val readDataWithCode = (0 until ICacheDataBanks).map { bank =>
    Mux1H(VecInit(masksReg.map(_(bank))).asTypeOf(UInt(nWays.W)), dataArrays.map(_(bank).io.r.resp.asUInt))
  }
  private val readEntries = readDataWithCode.map(_.asTypeOf(new ICacheDataEntry()))
  private val readDatas   = VecInit(readEntries.map(_.data))
  private val readCodes   = VecInit(readEntries.map(_.code))

  // TEST: force ECC to fail by setting readCodes to 0
  if (ICacheForceDataECCError) {
    readCodes.foreach(_ := 0.U)
  }

  /**
    ******************************************************************************
    * IO
    ******************************************************************************
    */
  io.readResp.datas := readDatas
  io.readResp.codes := readCodes
  io.write.ready    := true.B
  io.read.foreach(_.ready := !io.write.valid)
}

class ICache()(implicit p: Parameters) extends LazyModule with HasICacheParameters {
  override def shouldBeInlined: Boolean = false

  val clientParameters: TLMasterPortParameters = TLMasterPortParameters.v1(
    Seq(TLMasterParameters.v1(
      name = "icache",
      sourceId = IdRange(0, cacheParams.nFetchMshr + cacheParams.nPrefetchMshr + 1)
    )),
    requestFields = cacheParams.reqFields,
    echoFields = cacheParams.echoFields
  )

  val clientNode: TLClientNode = TLClientNode(Seq(clientParameters))

  val ctrlUnitOpt: Option[ICacheCtrlUnit] = ctrlUnitParamsOpt.map(params => LazyModule(new ICacheCtrlUnit(params)))

  lazy val module: ICacheImp = new ICacheImp(this)
}
