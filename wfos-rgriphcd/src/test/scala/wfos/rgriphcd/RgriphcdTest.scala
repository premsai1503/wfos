package wfos.rgriphcd

import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{ComponentId, ComponentType}
import csw.prefix.models.Prefix
import csw.testkit.scaladsl.CSWService.{AlarmServer, EventServer}
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import org.scalatest.funsuite.AnyFunSuiteLike
import csw.params.commands.CommandName

import scala.concurrent.Await
import scala.concurrent.duration._

class RgriphcdTest extends ScalaTestFrameworkTestKit(AlarmServer, EventServer) with AnyFunSuiteLike {

  import frameworkTestKit._

  override def beforeAll(): Unit = {
    super.beforeAll()
    // uncomment if you want one HCD run for all tests
    spawnStandalone(com.typesafe.config.ConfigFactory.load("RgriphcdStandalone.conf"))
  }

  test("HCD should be locatable using Location Service") {
    val connection   = AkkaConnection(ComponentId(Prefix("wfos.rgripHcd"), ComponentType.HCD))
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    akkaLocation.connection shouldBe connection
  }

  test("should be able to send move command to HCD") {
    import scala.concurrent.duration._
    implicit val sleepCommandTimeout: Timeout = Timeout(10000.millis)

    // Construct Setup command
    val sourcePrefix: Prefix        = Prefix("CSW.wfos.bgrxassembly")
    val targetAngleKey: Key[Int]    = KeyType.IntKey.make("targetAngle")
    val gratingModeKey: Key[String] = KeyType.StringKey.make("gratingMode")
    val cwKey: Key[Int]             = KeyType.IntKey.make("cw")

    val targetAngle: Parameter[Int]    = targetAngleKey.set(30)
    val gratingMode: Parameter[String] = gratingModeKey.set("bgid3")
    val cw: Parameter[Int]             = cwKey.set(6000)
    val sc1: Setup                     = Setup(sourcePrefix, CommandName("move"), Some(obsId)).madd(targetAngle, gratingMode, cw)
    val obsId: ObsId                   = ObsId("2023A-001-123")

    val setupCommand = Setup(Prefix("csw.move"), CommandName("sleep"), Some(ObsId("2020A-001-123"))).add(sleepTimeParam)

    val connection   = AkkaConnection(ComponentId(Prefix(WFOS, "rgriphcd"), ComponentType.HCD))
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    val hcd = CommandServiceFactory.make(akkaLocation)
    // submit command and handle response
    val responseF = hcd.submitAndWait(setupCommand)

    Await.result(responseF, 10000.millis) shouldBe a[Completed]
  }
}
