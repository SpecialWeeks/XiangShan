package device

import chisel3.{BlackBox, IO, _}
import chisel3.util.{HasBlackBoxResource, _}
import coupledL2.tl2chi.PortIO
import freechips.rocketchip.diplomacy.LazyModule
import org.chipsalliance.cde.config.Parameters
import system.HasSoCParameter
import coupledL2.tl2chi

//icn
class CHIAsyncIOCJ extends Bundle {
  val devtoicn_SACTIVE_async = Input(Bool())
  val devtoicn_dat_fifo_data_mcp = Input(UInt(3136.W)) //[3135:0]
  val devtoicn_dat_rptr_async = Input(UInt(8.W)) //[7   :0]
  val devtoicn_dat_wptr_async = Input(UInt(8.W)) //[7   :0]
  val devtoicn_ptr_reset_req_async = Input(Bool())
  val devtoicn_pwr_handshake_async = Input(Bool())
  val devtoicn_pwr_qreqn_async = Input(Bool())
  val devtoicn_req_fifo_data_mcp = Input(UInt(1016.W)) //[1015:0]
  val devtoicn_req_wptr_async = Input(UInt(8.W)) //[7   :0]
  val devtoicn_rsp_fifo_data_mcp = Input(UInt(440.W)) //[439 :0]
  val devtoicn_rsp_rptr_async = Input(UInt(8.W)) //[7   :0]
  val devtoicn_rsp_wptr_async = Input(UInt(8.W)) //[7   :0]
  val devtoicn_rxfifo_qactive_async = Input(Bool()) //
  val devtoicn_snp_rptr_async = Input(UInt(8.W)) //[7   :0]
  val devtoicn_syscoreq_async = Input(Bool())
  val devtoicn_txfifo_qactive_async = Input(Bool())
  val icntodev_SACTIVE_async = Output(Bool())
  val icntodev_dat_fifo_data_mcp = Output(UInt(3136.W)) //[3135:0]
  val icntodev_dat_rptr_async = Output(UInt(8.W)) //[7   :0]
  val icntodev_dat_wptr_async = Output(UInt(8.W)) //[7   :0]
  val icntodev_ptr_reset_ack_async = Output(Bool())
  val icntodev_pwr_qacceptn_async = Output(Bool())
  val icntodev_pwr_qdeny_async = Output(Bool())
  val icntodev_req_rptr_async = Output(UInt(8.W)) //[7   :0]
  val icntodev_rsp_fifo_data_mcp = Output(UInt(440.W)) //[439 :0]
  val icntodev_rsp_rptr_async = Output(UInt(8.W)) //[7   :0]
  val icntodev_rsp_wptr_async = Output(UInt(8.W)) //[7   :0]
  val icntodev_rxfifo_qactive_async = Output(Bool())
  val icntodev_snp_fifo_data_mcp = Output(UInt(704.W)) // [703 :0]
  val icntodev_snp_wptr_async = Output(UInt(8.W)) //[7   :0]
  val icntodev_syscoack_async = Output(Bool())
  val icntodev_txfifo_qactive_async = Output(Bool())
}

class CHIAsyncICNCJ()(implicit p: Parameters) extends Module {
  val i = IO(new Bundle {
    val dft = new Bundle {
      val icg_scan_en = Input(Bool())
      val scan_enable = Input(Bool())
    }
  })
  val io = IO(new Bundle {
    val cdb = new CHIAsyncIOCJ
    val chi = new PortIO
  })
  val cdbicn = Module(new xh_cdb_icn)

  //input
  cdbicn.DFTCGEN := i.dft.icg_scan_en
  cdbicn.DFTRSTDISABLE := i.dft.scan_enable
  cdbicn.RESETN := reset
  cdbicn.clk := clock
  cdbicn.RXDATFLIT := io.chi.rx.dat.flit //i_biu_rxdatflit[391:0]),
  cdbicn.RXDATFLITPEND := io.chi.rx.dat.flitpend
  cdbicn.RXDATFLITV := io.chi.rx.dat.flitv
  cdbicn.RXLINKACTIVEREQ := io.chi.rx.linkactivereq
  cdbicn.RXRSPFLIT := io.chi.rx.rsp.flit //[54:0]
  cdbicn.RXRSPFLITPEND := io.chi.rx.rsp.flitpend
  cdbicn.RXRSPFLITV := io.chi.rx.rsp.flitv
  cdbicn.RXSACTIVE_local := io.chi.rxsactive
  cdbicn.RXSNPFLIT := io.chi.rx.snp.flit //[87:0]
  cdbicn.RXSNPFLITPEND := io.chi.rx.snp.flitpend
  cdbicn.RXSNPFLITV := io.chi.rx.snp.flitv
  cdbicn.SYSCOACK := io.chi.syscoack
  cdbicn.TXDATLCRDV := io.chi.tx.dat.lcrdv
  cdbicn.TXLINKACTIVEACK := io.chi.tx.linkactiveack
  cdbicn.TXREQLCRDV := io.chi.tx.req.lcrdv
  cdbicn.TXRSPLCRDV := io.chi.tx.rsp.lcrdv
  //output
  io.chi.rx.dat.lcrdv := cdbicn.RXDATLCRDV
  io.chi.rx.linkactiveack := cdbicn.RXLINKACTIVEACK
  io.chi.rx.rsp.lcrdv := cdbicn.RXRSPLCRDV
  io.chi.rx.snp.lcrdv := cdbicn.RXSNPLCRDV
  io.chi.syscoreq := cdbicn.SYSCOREQ
  io.chi.tx.dat.flit := cdbicn.TXDATFLIT //[391:0]
  io.chi.tx.dat.flitpend := cdbicn.TXDATFLITPEND
  io.chi.tx.dat.flitv := cdbicn.TXDATFLITV
  io.chi.tx.linkactivereq := cdbicn.TXLINKACTIVEREQ
  io.chi.tx.req.flit := cdbicn.TXREQFLIT //[126:0]
  io.chi.tx.req.flitpend := cdbicn.TXREQFLITPEND
  io.chi.tx.req.flitv := cdbicn.TXREQFLITV
  io.chi.tx.rsp.flit := cdbicn.TXRSPFLIT //[54:0]
  io.chi.tx.rsp.flitpend := cdbicn.TXRSPFLITPEND
  io.chi.tx.rsp.flitv := cdbicn.TXRSPFLITV
  io.chi.txsactive := cdbicn.TXSACTIVE_local
  //cdb connect
  cdbicn.devtoicn_SACTIVE_async := io.cdb.devtoicn_SACTIVE_async
  cdbicn.devtoicn_dat_fifo_data_mcp := io.cdb.devtoicn_dat_fifo_data_mcp
  cdbicn.devtoicn_dat_rptr_async := io.cdb.devtoicn_dat_rptr_async
  cdbicn.devtoicn_dat_wptr_async := io.cdb.devtoicn_dat_wptr_async
  cdbicn.devtoicn_ptr_reset_req_async := io.cdb.devtoicn_ptr_reset_req_async
  cdbicn.devtoicn_pwr_handshake_async := io.cdb.devtoicn_pwr_handshake_async
  cdbicn.devtoicn_pwr_qreqn_async := io.cdb.devtoicn_pwr_qreqn_async
  cdbicn.devtoicn_req_fifo_data_mcp := io.cdb.devtoicn_req_fifo_data_mcp
  cdbicn.devtoicn_req_wptr_async := io.cdb.devtoicn_req_wptr_async
  cdbicn.devtoicn_rsp_fifo_data_mcp := io.cdb.devtoicn_rsp_fifo_data_mcp
  cdbicn.devtoicn_rsp_rptr_async := io.cdb.devtoicn_rsp_rptr_async
  cdbicn.devtoicn_rsp_wptr_async := io.cdb.devtoicn_rsp_wptr_async
  cdbicn.devtoicn_rxfifo_qactive_async := io.cdb.devtoicn_rxfifo_qactive_async
  cdbicn.devtoicn_snp_rptr_async := io.cdb.devtoicn_snp_rptr_async
  cdbicn.devtoicn_syscoreq_async := io.cdb.devtoicn_syscoreq_async
  cdbicn.devtoicn_txfifo_qactive_async := io.cdb.devtoicn_txfifo_qactive_async

  io.cdb.icntodev_SACTIVE_async := cdbicn.icntodev_SACTIVE_async
  io.cdb.icntodev_dat_fifo_data_mcp := cdbicn.icntodev_dat_fifo_data_mcp
  io.cdb.icntodev_dat_rptr_async := cdbicn.icntodev_dat_rptr_async
  io.cdb.icntodev_dat_wptr_async := cdbicn.icntodev_dat_wptr_async
  io.cdb.icntodev_ptr_reset_ack_async := cdbicn.icntodev_ptr_reset_ack_async
  io.cdb.icntodev_pwr_qacceptn_async := cdbicn.icntodev_pwr_qacceptn_async
  io.cdb.icntodev_pwr_qdeny_async := cdbicn.icntodev_pwr_qdeny_async
  io.cdb.icntodev_req_rptr_async := cdbicn.icntodev_req_rptr_async
  io.cdb.icntodev_rsp_fifo_data_mcp := cdbicn.icntodev_rsp_fifo_data_mcp
  io.cdb.icntodev_rsp_rptr_async := cdbicn.icntodev_rsp_rptr_async
  io.cdb.icntodev_rsp_wptr_async := cdbicn.icntodev_rsp_wptr_async
  io.cdb.icntodev_rxfifo_qactive_async := cdbicn.icntodev_rxfifo_qactive_async
  io.cdb.icntodev_snp_fifo_data_mcp := cdbicn.icntodev_snp_fifo_data_mcp
  io.cdb.icntodev_snp_wptr_async := cdbicn.icntodev_snp_wptr_async
  io.cdb.icntodev_syscoack_async := cdbicn.icntodev_syscoack_async
  io.cdb.icntodev_txfifo_qactive_async := cdbicn.icntodev_txfifo_qactive_async
}

class xh_cdb_icn extends BlackBox {
  //chi cdb async interface
  val devtoicn_SACTIVE_async = Input(Bool())
  val devtoicn_dat_fifo_data_mcp = Input(UInt(3136.W)) //[3135:0]
  val devtoicn_dat_rptr_async = Input(UInt(8.W)) //[7   :0]
  val devtoicn_dat_wptr_async = Input(UInt(8.W)) //[7   :0]
  val devtoicn_ptr_reset_req_async = Input(Bool())
  val devtoicn_pwr_handshake_async = Input(Bool())
  val devtoicn_pwr_qreqn_async = Input(Bool())
  val devtoicn_req_fifo_data_mcp = Input(UInt(1016.W)) //[1015:0]
  val devtoicn_req_wptr_async = Input(UInt(8.W)) //[7   :0]
  val devtoicn_rsp_fifo_data_mcp = Input(UInt(440.W)) //[439 :0]
  val devtoicn_rsp_rptr_async = Input(UInt(8.W)) //[7   :0]
  val devtoicn_rsp_wptr_async = Input(UInt(8.W)) //[7   :0]
  val devtoicn_rxfifo_qactive_async = Input(Bool()) //
  val devtoicn_snp_rptr_async = Input(UInt(8.W)) //[7   :0]
  val devtoicn_syscoreq_async = Input(Bool())
  val devtoicn_txfifo_qactive_async = Input(Bool())
  val icntodev_SACTIVE_async = Output(Bool())
  val icntodev_dat_fifo_data_mcp = Output(UInt(3136.W)) //[3135:0]
  val icntodev_dat_rptr_async = Output(UInt(8.W)) //[7   :0]
  val icntodev_dat_wptr_async = Output(UInt(8.W)) //[7   :0]
  val icntodev_ptr_reset_ack_async = Output(Bool())
  val icntodev_pwr_qacceptn_async = Output(Bool())
  val icntodev_pwr_qdeny_async = Output(Bool())
  val icntodev_req_rptr_async = Output(UInt(8.W)) //[7   :0]
  val icntodev_rsp_fifo_data_mcp = Output(UInt(440.W)) //[439 :0]
  val icntodev_rsp_rptr_async = Output(UInt(8.W)) //[7   :0]
  val icntodev_rsp_wptr_async = Output(UInt(8.W)) //[7   :0]
  val icntodev_rxfifo_qactive_async = Output(Bool())
  val icntodev_snp_fifo_data_mcp = Output(UInt(704.W)) // [703 :0]
  val icntodev_snp_wptr_async = Output(UInt(8.W)) //[7   :0]
  val icntodev_syscoack_async = Output(Bool())
  val icntodev_txfifo_qactive_async = Output(Bool())
  //clock reset
  val clk = Input(Clock())
  val RESETN = Input(Reset())
  // dft
  val DFTCGEN = Input(Bool()) //i_dft_icg_scan_en
  val DFTRSTDISABLE = Input(Bool()) //i_dft_scan_enable
  //chi interface
  val RXDATFLIT = Input(UInt(392.W)) //    [391 :0] rxdatflit
  val RXDATFLITPEND = Input(Bool()) //rxdatflitpend
  val RXDATFLITV = Input(Bool()) //rxdatflitv
  val RXLINKACTIVEREQ = Input(Bool()) //rxlinkactivereq
  val RXRSPFLIT = Input(UInt(55.W)) //   [54  :0]
  val RXRSPFLITPEND = Input(Bool()) //i_biu_rxrspflitpend
  val RXRSPFLITV = Input(Bool())
  val RXSACTIVE_local = Input(Bool())
  val RXSNPFLIT = Input(UInt(88.W)) //    [87  :0]
  val RXSNPFLITPEND = Input(Bool())
  val RXSNPFLITV = Input(Bool())
  val SYSCOACK = Input(Bool())
  val TXDATLCRDV = Input(Bool())
  val TXLINKACTIVEACK = Input(Bool())
  val TXREQLCRDV = Input(Bool())
  val TXRSPLCRDV = Input(Bool())
  val RXDATLCRDV = Output(Bool()) //rxdatlcrdv
  val RXLINKACTIVEACK = Output(Bool()) //rxlinkactiveack
  val RXRSPLCRDV = Output(Bool())
  val RXSNPLCRDV = Output(Bool())
  val SYSCOREQ = Output(Bool())
  val TXDATFLIT = Output(UInt(392.W)) //  [391 :0]
  val TXDATFLITPEND = Output(Bool())
  val TXDATFLITV = Output(Bool())
  val TXLINKACTIVEREQ = Output(Bool())
  val TXREQFLIT = Output(UInt(127.W)) //  [126 :0]
  val TXREQFLITPEND = Output(Bool())
  val TXREQFLITV = Output(Bool())
  val TXRSPFLIT = Output(UInt(55.W)) //  [54  :0]
  val TXRSPFLITPEND = Output(Bool())
  val TXRSPFLITV = Output(Bool())
  val TXSACTIVE_local = Output(Bool())
  val CLK_QACTIVE = Output(Bool()) //unused
}

class CHIAsyncDEVCJ()(implicit p: Parameters) extends Module {
  val i = IO(new Bundle {
    val dft = new Bundle {
      val icg_scan_en = Input(Bool())
      val scan_enable = Input(Bool())
    }
  })
  val io = IO(new Bundle {
    val cdb = Flipped(new CHIAsyncIOCJ)
    val chi = Flipped(new PortIO)
  })
  //---instance cdb bridge ---
  val cdbdev = Module(new xh_cdb_dev)
  //===dft connect====
  cdbdev.DFTCGEN := i.dft.icg_scan_en
  cdbdev.DFTRSTDISABLE := i.dft.scan_enable
  cdbdev.PWR_QREQN := true.B
  //===clock reset connect =====
  cdbdev.RESETN := reset
  cdbdev.clk := clock
  //=== chi connect===
  cdbdev.RXDATFLIT := io.chi.tx.dat.flit
  cdbdev.RXDATFLITPEND := io.chi.tx.dat.flitpend
  cdbdev.RXDATFLITV := io.chi.tx.dat.flitv
  cdbdev.RXLINKACTIVEREQ := io.chi.tx.linkactivereq
  cdbdev.RXREQFLIT := io.chi.tx.req.flit
  cdbdev.RXREQFLITPEND := io.chi.tx.req.flitpend
  cdbdev.RXREQFLITV := io.chi.tx.req.flitv
  cdbdev.RXRSPFLIT := io.chi.tx.rsp.flit
  cdbdev.RXRSPFLITPEND := io.chi.tx.rsp.flitpend
  cdbdev.RXRSPFLITV := io.chi.tx.rsp.flitv
  cdbdev.RXSACTIVE_local := io.chi.txsactive
  cdbdev.SYSCOREQ := io.chi.syscoreq
  cdbdev.TXDATLCRDV := io.chi.rx.dat.lcrdv
  cdbdev.TXLINKACTIVEACK := io.chi.rx.linkactiveack
  cdbdev.TXRSPLCRDV := io.chi.rx.rsp.lcrdv
  cdbdev.TXSNPLCRDV := io.chi.rx.snp.lcrdv
  //output
  io.chi.tx.dat.lcrdv := cdbdev.RXDATLCRDV
  io.chi.tx.linkactiveack := cdbdev.RXLINKACTIVEACK
  io.chi.tx.req.lcrdv := cdbdev.RXREQLCRDV
  io.chi.tx.rsp.lcrdv := cdbdev.RXRSPLCRDV
  io.chi.syscoack := cdbdev.SYSCOACK
  io.chi.rx.dat.flit := cdbdev.TXDATFLIT
  io.chi.rx.dat.flitpend := cdbdev.TXDATFLITPEND
  io.chi.rx.dat.flitv := cdbdev.TXDATFLITV
  io.chi.rx.linkactivereq := cdbdev.TXLINKACTIVEREQ
  io.chi.rx.rsp.flit := cdbdev.TXRSPFLIT
  io.chi.rx.rsp.flitpend := cdbdev.TXRSPFLITPEND
  io.chi.rx.rsp.flitv := cdbdev.TXRSPFLITV
  io.chi.rxsactive := cdbdev.TXSACTIVE_local
  io.chi.rx.snp.flit := cdbdev.TXSNPFLIT
  io.chi.rx.snp.flitpend := cdbdev.TXSNPFLITPEND
  io.chi.rx.snp.flitv := cdbdev.TXSNPFLITV
  //cdb connect
  io.cdb.devtoicn_SACTIVE_async := cdbdev.devtoicn_SACTIVE_async
  io.cdb.devtoicn_dat_fifo_data_mcp := cdbdev.devtoicn_dat_fifo_data_mcp
  io.cdb.devtoicn_dat_rptr_async := cdbdev.devtoicn_dat_rptr_async
  io.cdb.devtoicn_dat_wptr_async := cdbdev.devtoicn_dat_wptr_async
  io.cdb.devtoicn_ptr_reset_req_async := cdbdev.devtoicn_ptr_reset_req_async
  io.cdb.devtoicn_pwr_handshake_async := cdbdev.devtoicn_pwr_handshake_async
  io.cdb.devtoicn_pwr_qreqn_async := cdbdev.devtoicn_pwr_qreqn_async
  io.cdb.devtoicn_req_fifo_data_mcp := cdbdev.devtoicn_req_fifo_data_mcp
  io.cdb.devtoicn_req_wptr_async := cdbdev.devtoicn_req_wptr_async
  io.cdb.devtoicn_rsp_fifo_data_mcp := cdbdev.devtoicn_rsp_fifo_data_mcp
  io.cdb.devtoicn_rsp_rptr_async := cdbdev.devtoicn_rsp_rptr_async
  io.cdb.devtoicn_rsp_wptr_async := cdbdev.devtoicn_rsp_wptr_async
  io.cdb.devtoicn_rxfifo_qactive_async := cdbdev.devtoicn_rxfifo_qactive_async
  io.cdb.devtoicn_snp_rptr_async := cdbdev.devtoicn_snp_rptr_async
  io.cdb.devtoicn_syscoreq_async := cdbdev.devtoicn_syscoreq_async
  io.cdb.devtoicn_txfifo_qactive_async := cdbdev.devtoicn_txfifo_qactive_async
  //input
  cdbdev.icntodev_SACTIVE_async := io.cdb.icntodev_SACTIVE_async
  cdbdev.icntodev_dat_fifo_data_mcp := io.cdb.icntodev_dat_fifo_data_mcp
  cdbdev.icntodev_dat_rptr_async := io.cdb.icntodev_dat_rptr_async
  cdbdev.icntodev_dat_wptr_async := io.cdb.icntodev_dat_wptr_async
  cdbdev.icntodev_ptr_reset_ack_async := io.cdb.icntodev_ptr_reset_ack_async
  cdbdev.icntodev_pwr_qacceptn_async := io.cdb.icntodev_pwr_qacceptn_async
  cdbdev.icntodev_pwr_qdeny_async := io.cdb.icntodev_pwr_qdeny_async
  cdbdev.icntodev_req_rptr_async := io.cdb.icntodev_req_rptr_async
  cdbdev.icntodev_rsp_fifo_data_mcp := io.cdb.icntodev_rsp_fifo_data_mcp
  cdbdev.icntodev_rsp_rptr_async := io.cdb.icntodev_rsp_rptr_async
  cdbdev.icntodev_rsp_wptr_async := io.cdb.icntodev_rsp_wptr_async
  cdbdev.icntodev_rxfifo_qactive_async := io.cdb.icntodev_rxfifo_qactive_async
  cdbdev.icntodev_snp_fifo_data_mcp := io.cdb.icntodev_snp_fifo_data_mcp
  cdbdev.icntodev_snp_wptr_async := io.cdb.icntodev_snp_wptr_async
  cdbdev.icntodev_syscoack_async := io.cdb.icntodev_syscoack_async
  cdbdev.icntodev_txfifo_qactive_async := io.cdb.icntodev_txfifo_qactive_async
}

class xh_cdb_dev extends BlackBox {
  val DFTCGEN = IO(Input(Bool()))
  val DFTRSTDISABLE = IO(Input(Bool()))
  val PWR_QREQN = IO(Input(Bool()))
  val RESETN = IO(Input(Reset()))
  val RXDATFLIT = IO(Input(UInt(392.W)))
  val RXDATFLITPEND = IO(Input(Bool()))
  val RXDATFLITV = IO(Input(Bool()))
  val RXLINKACTIVEREQ = IO(Input(Bool()))
  val RXREQFLIT = IO(Input(UInt(127.W))) //   [126 :0]
  val RXREQFLITPEND = IO(Input(Bool()))
  val RXREQFLITV = IO(Input(Bool()))
  val RXRSPFLIT = IO(Input(UInt(55.W)))
  val RXRSPFLITPEND = IO(Input(Bool()))
  val RXRSPFLITV = IO(Input(Bool()))
  val RXSACTIVE_local = IO(Input(Bool()))
  val SYSCOREQ = IO(Input(Bool()))
  val TXDATLCRDV = IO(Input(Bool()))
  val TXLINKACTIVEACK = IO(Input(Bool()))
  val TXRSPLCRDV = IO(Input(Bool()))
  val TXSNPLCRDV = IO(Input(Bool()))
  val clk = IO(Input(Clock()))
  val icntodev_SACTIVE_async = IO(Input(Bool()))
  val icntodev_dat_fifo_data_mcp = IO(Input(UInt(3136.W))) //   [3135:0]
  val icntodev_dat_rptr_async = IO(Input(UInt(8.W))) //   [7   :0]
  val icntodev_dat_wptr_async = IO(Input(UInt(8.W))) //   [7   :0]
  val icntodev_ptr_reset_ack_async = IO(Input(Bool()))
  val icntodev_pwr_qacceptn_async = IO(Input(Bool()))
  val icntodev_pwr_qdeny_async = IO(Input(Bool()))
  val icntodev_req_rptr_async = IO(Input(UInt(8.W))) //   [7   :0]
  val icntodev_rsp_fifo_data_mcp = IO(Input(UInt(440.W))) //   [439 :0]
  val icntodev_rsp_rptr_async = IO(Input(UInt(8.W))) //   [7   :0]
  val icntodev_rsp_wptr_async = IO(Input(UInt(8.W))) //   [7   :0]
  val icntodev_rxfifo_qactive_async = IO(Input(Bool()))
  val icntodev_snp_fifo_data_mcp = IO(Input(UInt(704.W))) //   [703 :0]
  val icntodev_snp_wptr_async = IO(Input(UInt(8.W))) //   [7   :0]
  val icntodev_syscoack_async = IO(Input(Bool()))
  val icntodev_txfifo_qactive_async = IO(Input(Bool()))
  val CLK_QACTIVE = IO(Output(Bool()))
  val PWR_QACCEPTN = IO(Output(Bool()))
  val PWR_QACTIVE = IO(Output(Bool()))
  val PWR_QDENY = IO(Output(Bool()))
  val RXDATLCRDV = IO(Output(Bool()))
  val RXLINKACTIVEACK = IO(Output(Bool()))
  val RXREQLCRDV = IO(Output(Bool()))
  val RXRSPLCRDV = IO(Output(Bool()))
  val SYSCOACK = IO(Output(Bool()))
  val TXDATFLIT = IO(Output(UInt(392.W))) //  [391 :0]
  val TXDATFLITPEND = IO(Output(Bool()))
  val TXDATFLITV = IO(Output(Bool()))
  val TXLINKACTIVEREQ = IO(Output(Bool()))
  val TXRSPFLIT = IO(Output(UInt(55.W))) //  [54  :0]
  val TXRSPFLITPEND = IO(Output(Bool()))
  val TXRSPFLITV = IO(Output(Bool()))
  val TXSACTIVE_local = IO(Output(Bool()))
  val TXSNPFLIT = IO(Output(UInt(88.W))) //  [87  :0]
  val TXSNPFLITPEND = IO(Output(Bool()))
  val TXSNPFLITV = IO(Output(Bool()))
  val devtoicn_SACTIVE_async = IO(Output(Bool()))
  val devtoicn_dat_fifo_data_mcp = IO(Output(UInt(3136.W))) //  [3135:0]
  val devtoicn_dat_rptr_async = IO(Output(UInt(8.W))) //  [7   :0]
  val devtoicn_dat_wptr_async = IO(Output(UInt(8.W))) //  [7   :0]
  val devtoicn_ptr_reset_req_async = IO(Output(Bool()))
  val devtoicn_pwr_handshake_async = IO(Output(Bool()))
  val devtoicn_pwr_qreqn_async = IO(Output(Bool()))
  val devtoicn_req_fifo_data_mcp = IO(Output(UInt(1016.W))) //   [1015:0]
  val devtoicn_req_wptr_async = IO(Output(UInt(8.W))) //   [7   :0]
  val devtoicn_rsp_fifo_data_mcp = IO(Output(UInt(440.W))) //  [439 :0]
  val devtoicn_rsp_rptr_async = IO(Output(UInt(8.W))) //  [7   :0]
  val devtoicn_rsp_wptr_async = IO(Output(UInt(8.W))) // [7   :0]
  val devtoicn_rxfifo_qactive_async = IO(Output(Bool()))
  val devtoicn_snp_rptr_async = IO(Output(UInt(8.W))) //  [7   :0]
  val devtoicn_syscoreq_async = IO(Output(Bool()))
  val devtoicn_txfifo_qactive_async = IO(Output(Bool()))
}


