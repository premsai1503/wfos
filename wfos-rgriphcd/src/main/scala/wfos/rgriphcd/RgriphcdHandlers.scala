package wfos.rgriphcd

import akka.actor.typed.scaladsl.ActorContext
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandResponse._
import csw.params.core.models.ObsId
import csw.params.commands.CommandIssue.UnsupportedCommandIssue
import csw.params.commands.{ControlCommand, Observe, Setup, CommandName}
// import csw.params.commands.ControlCommand
import csw.params.core.generics.{Key, KeyType, Parameter}
import csw.prefix.models.Prefix
import csw.time.core.models.UTCTime
import csw.params.core.models.Id

import scala.concurrent.{ExecutionContextExecutor}

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to Rgriphcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw/commons/framework.html
 */
class RgriphcdHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext) extends ComponentHandlers(ctx, cswCtx) {

  import cswCtx._
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger
  private val prefix                        = cswCtx.componentInfo.prefix

  val obsId: ObsId = ObsId("2020A-001-123")

  val k1: Key[Int]    = KeyType.IntKey.make("encoder")
  val k2: Key[String] = KeyType.StringKey.make("stringThing")
  val source: Prefix  = Prefix(s"$prefix")

  val i1: Parameter[Int]    = k1.set(22)  // number of degrees to be rotated (have to use degree unit. Change it later)
  val i2: Parameter[String] = k2.set("R") // left or right

  val sc1: Setup = Setup(source, CommandName("move"), Some(obsId)).add(i1).add(i2)
  this.onSubmit(Id("Test_command1"), sc1)

  // Called when the component is created
  override def initialize(): Unit = {
    // log.info(this.isOnline)
    if (this.isOnline == false) log.info("it is not Online")
    log.info(s"Initializing $prefix ... on Prem's System")
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {}

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse =
    controlCommand match {
      case setup: Setup =>
        setup.commandName match {
          case CommandName("move") => {
            log.info("Command Validation Successfull")
            Accepted(runId)
          }
          case _ => {
            log.info("Command Validation Failure")
            Invalid(runId, UnsupportedCommandIssue("Setup only take 'move' as command"))
          }
        }
      case _: Observe =>
        log.info("Invalid(Observe commands not supported)")
        Invalid(runId, UnsupportedCommandIssue("Observe commands not supported"))
    }
  // Accepted(runId)

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = {
    val response: ValidateCommandResponse = validateCommand(runId, controlCommand)
    response match {
      case Accepted(runId) => {
        log.info("Command is Valid")
        Completed(runId)
      }
      case _ => {
        log.info("Invalid command is received")
        Invalid(runId, UnsupportedCommandIssue("Invalid Command"))
      }
    }

  }
  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {
    log.info("shutting Down")
  }

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

  object Hello {
    def main(args: Array[String]) = {
      println("Hello, world")
    }
  }

}
