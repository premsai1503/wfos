package wfos.rgriphcd.shared

import csw.params.commands.CommandName
import csw.params.core.generics.{Key, KeyType, Parameter}
import csw.params.core.models.{Id, Units, ObsId}
import csw.prefix.models.Prefix

object RgripInfo {
  // rgripHcd configurations
  val exchangeAngleKey: Key[Int]    = KeyType.IntKey.make("exchangeAngle")
  val exchangeAngle: Parameter[Int] = exchangeAngleKey.set(35)

  val homeAngleKey: Key[Int]    = KeyType.IntKey.make("homeAngle")
  val homeAngle: Parameter[Int] = homeAngleKey.set(35)

  val currentAngleKey: Key[Int]    = KeyType.IntKey.make("currentAngle")
  var currentAngle: Parameter[Int] = currentAngleKey.set(35)

  val targetAngleKey: Key[Int]    = KeyType.IntKey.make("targetAngle")
  val gratingModeKey: Key[String] = KeyType.StringKey.make("gratingMode")
  val cwKey: Key[Int]             = KeyType.IntKey.make("cw")

  // ranges of targetAngle
  val minTargetAngleKey: Key[Int]    = KeyType.IntKey.make("minTargetAngle")
  val minTargetAngle: Parameter[Int] = minTargetAngleKey.set(0)

  val maxTargetAngleKey: Key[Int]    = KeyType.IntKey.make("maxTargetAngle")
  val maxTargetAngle: Parameter[Int] = maxTargetAngleKey.set(55)

  // ranges of target CommonWavelength
  val minCWKey: Key[Int]    = KeyType.IntKey.make("minCW")
  val minCW: Parameter[Int] = minCWKey.set(3100)

  val maxCWKey: Key[Int]    = KeyType.IntKey.make("maxCW")
  val maxCW: Parameter[Int] = maxCWKey.set(9000)

  val obsId: ObsId = ObsId("2023A-001-123")
}
