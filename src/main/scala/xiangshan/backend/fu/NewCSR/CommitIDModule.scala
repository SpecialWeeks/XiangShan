package xiangshan.backend.fu.NewCSR

import chisel3._
import com.typesafe.scalalogging.LazyLogging

import java.util.Properties

class CommitIDModule(shaWidth: Int) extends Module with LazyLogging {
  val io = IO(new Bundle {
    val commitID = Output(UInt(shaWidth.W))
    val dirty    = Output(Bool())
  })

  val props = new Properties()
  props.load((os.resource / "gitStatus").getInputStream)

  val sha = props.get("SHA").asInstanceOf[String].take(shaWidth / 4)
  val dirty = props.get("dirty").asInstanceOf[String].toInt

  logger.info(s"commit SHA=$sha")
  logger.info(s"dirty=${if (dirty == 1) "true" else "false" }")

  io.commitID := BigInt(sha, 16).U(shaWidth.W)
  io.dirty := dirty.U
}
