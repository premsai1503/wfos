package wfos.rgriphcd

import akka.actor.typed.scaladsl.ActorContext
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandResponse._
import csw.params.core.models.ObsId
import csw.params.commands.CommandIssue.{MissingKeyIssue, ParameterValueOutOfRangeIssue, UnsupportedCommandIssue}
import csw.params.commands.{ControlCommand, Observe, Setup, CommandName}
// import csw.params.commands.ControlCommand
import csw.params.core.generics.{Key, KeyType, Parameter}
// import csw.prefix.models.Prefix
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

  // rgripHcd configurations
  private val position: Key[Int]               = KeyType.IntKey.make("position")
  private val current_position: Parameter[Int] = position.set(10)
  private val initial_position: Parameter[Int] = position.set(0)

  private val direction: Key[String]               = KeyType.StringKey.make("direction")
  private val current_direction: Parameter[String] = direction.set("right")
  private val initial_direction: Parameter[String] = direction.set("right")

  private val toPositionKey: Key[Int]     = KeyType.IntKey.make("toPosition")
  private val toDirectionKey: Key[String] = KeyType.StringKey.make("toDirection")

  private val maxPosition: Int = 35

  private val obsId: ObsId = ObsId("2023A-001-123")

  // Called when the component is created
  override def initialize(): Unit = {
    log.info(s"Initializing $prefix ... on Prem's System")
    log.info(s"Checking if $prefix is at exchange position")

    val temp = current_position.values

    log.info(s"$current_position, $current_direction")

    // (current_position, current_direction) match {
    //   case (`initial_position`, `initial_direction`) =>
    //     log.info(s"$prefix's current configuration is matching the initial configuration")
    //   case _ => {
    //     val toPosition: Parameter[Int]     = toPositionKey.set(initial_position.head)
    //     val toDirection: Parameter[String] = toDirectionKey.set(initial_direction.head)

    //     val sc1: Setup = Setup(prefix, CommandName("rotate"), Some(obsId)).add(toPosition).add(toDirection)
    //     log.info(s"$sc1")
    //     val validation_response: ValidateCommandResponse = validateCommand(Id("SelfCommand1"), sc1)

    //     validation_response match {
    //       case Accepted(id) => {
    //         log.info(s"Command $id is accepted")
    //         onSubmit(id, sc1)
    //       }
    //       case _ =>
    //         Invalid(Id("SelfCommand1"), UnsupportedCommandIssue(""))
    //     }
    //   }
    // }
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {}

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = {
    controlCommand match {
      case setup: Setup =>
        setup.commandName match {
          case CommandName("rotate") => {
            log.info(s"$runId: Validation Successfull")
            Accepted(runId)
          }
          case _ => {
            log.info(s"$runId: Validation Failure")
            Invalid(runId, UnsupportedCommandIssue("Setup only take 'rotate' as command"))
          }
        }
      case _: Observe =>
        // log.info("Invalid(Observe commands not supported)")
        Invalid(runId, UnsupportedCommandIssue("Observe commands not supported"))
    }
  }

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = {
    log.info(s"HCD: $prefix handling command: ${controlCommand.commandName}")
    controlCommand match {
      case s: Setup => onSetup(runId, s)
      case _: Observe =>
        Invalid(runId, UnsupportedCommandIssue("Observe commands not supported"))
    }
  }

  private def onSetup(runId: Id, setup: Setup): SubmitResponse = {
    log.info(s"HCD: $prefix onSubmit received command: $setup")

    if (setup.exists(toPositionKey) && setup.exists(toDirectionKey)) {

      val positionChange: Int     = setup(toPositionKey).head
      val directionChange: String = setup(toDirectionKey).head

      if (positionChange < maxPosition) {
        log.info(s"$prefix's position is changes to $positionChange towards $directionChange")

        Completed(runId)
      }
      else
        Invalid(runId, ParameterValueOutOfRangeIssue("position must be < 35"))
    }
    else
      Invalid(runId, MissingKeyIssue("Required keys: toPositionKey, toDirectionKey are Missing"))
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
