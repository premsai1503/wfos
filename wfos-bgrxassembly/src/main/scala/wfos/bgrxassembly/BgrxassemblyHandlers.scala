package wfos.bgrxassembly

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
// import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandResponse._
import csw.params.commands.{ControlCommand, CommandName, Setup}
import csw.time.core.models.UTCTime
import csw.params.core.models.{Id, ObsId}

import csw.location.api.models.{AkkaLocation, ComponentId, ComponentType, LocationRemoved, LocationUpdated, TrackingEvent}
import csw.location.api.models.Connection.AkkaConnection
// import csw.params.commands.{CommandName}
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.prefix.models.{Prefix, Subsystem}
import csw.params.core.generics.{Key, KeyType, Parameter}

import scala.concurrent.{ExecutionContextExecutor, Future}

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to Rgriphcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw/commons/framework.html
 */
class BgrxassemblyHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext) extends ComponentHandlers(ctx, cswCtx) {

  import cswCtx._
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger
  // #resolve-hcd-and-create-commandservice
  private implicit val system: ActorSystem[Nothing] = ctx.system
  // #resolve-hcd-and-create-commandservice

  private val hcdConnection                 = AkkaConnection(ComponentId(Prefix(Subsystem.WFOS, "rgripHcd"), ComponentType.HCD))
  private var hcdLocation: AkkaLocation     = _
  private var hcdCS: Option[CommandService] = None

  // Keys, parameters and Commands
  val sourcePrefix: Prefix       = Prefix("CSW.wfos.bgrxassembly")
  val rotateCommand: CommandName = CommandName("rotate")

  val rotation: Key[Int]          = KeyType.IntKey.make("rotation_amount")
  val goodDegrees: Parameter[Int] = rotation.set(10)
  val badDegrees: Parameter[Int]  = rotation.set(100)

  private val obsId: ObsId = ObsId("2023A-001-123")

  override def initialize(): Unit = {
    log.info("Initializing bgrxAssembly...on Prem's System")
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {
    log.info("Locations of components in the assembly are updated")
    log.debug(s"onLocationTrackingEvent called: $trackingEvent")
    trackingEvent match {
      case LocationUpdated(location) => {
        hcdLocation = location.asInstanceOf[AkkaLocation]
        log.info(s"$hcdLocation")
        hcdCS = Some(CommandServiceFactory.make(location))
        sendCommand(Id("AssemblyCommand-1"))
      }
      case LocationRemoved(connection) => log.info("Location Removed")
    }
  }

  private def sendCommand(runId: Id): Future[SubmitResponse] = {
    val setup: Setup = Setup(sourcePrefix, rotateCommand, Some(obsId))
    hcdCS match {
      case Some(cs) =>
        cs.submit(setup)
      case None =>
        Future(Error(runId, s"A needed HCD is not available: ${hcdConnection.componentId}"))
    }
  }

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = Accepted(runId)

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = Completed(runId)

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

}
