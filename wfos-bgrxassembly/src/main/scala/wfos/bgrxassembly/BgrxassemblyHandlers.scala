package wfos.bgrxassembly

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.params.commands.CommandResponse._
import csw.params.commands.{ControlCommand, CommandName, Setup, Observe, CommandIssue}
import csw.params.commands.CommandIssue.{MissingKeyIssue, ParameterValueOutOfRangeIssue, UnsupportedCommandIssue}
import csw.time.core.models.UTCTime
import csw.params.core.models.{Id, ObsId}

import csw.location.api.models.{AkkaLocation, ComponentId, ComponentType, LocationRemoved, LocationUpdated, TrackingEvent}
import csw.location.api.models.Connection.AkkaConnection
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

  private val hcdConnection = AkkaConnection(ComponentId(Prefix(Subsystem.WFOS, "rgripHcd"), ComponentType.HCD))
  // private var hcdLocation: AkkaLocation     = _
  private var hcdCS: Option[CommandService] = None

  // Keys, parameters and Commands
  val sourcePrefix: Prefix        = Prefix("CSW.wfos.bgrxassembly")
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

  private val obsId: ObsId = ObsId("2023A-001-123")

  override def initialize(): Unit = {
    log.info("Initializing bgrxAssembly")
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {
    log.info("Bgrx Assembly: Locations of components in the assembly are updated")
    log.debug(s"Bgrx Assembly: onLocationTrackingEvent called: $trackingEvent")
    trackingEvent match {
      case LocationUpdated(location) => {
        // hcdLocation = location.asInstanceOf[AkkaLocation]
        // log.info(s"$hcdLocation")
        hcdCS = Some(CommandServiceFactory.make(location))
        sendCommand(Id("AssemblyCommand-1"))
      }
      case LocationRemoved(connection) => log.info("Location Removed")
    }
  }

  private def sendCommand(runId: Id): SubmitResponse = {
    val targetAngle: Parameter[Int]    = targetAngleKey.set(30)
    val gratingMode: Parameter[String] = gratingModeKey.set("bgid3")
    val cw: Parameter[Int]             = cwKey.set(6000)
    val sc1: Setup                     = Setup(sourcePrefix, CommandName("move"), Some(obsId)).madd(targetAngle, gratingMode, cw)

    val validateResponse = validateCommand(runId, sc1)
    validateResponse match {
      case Accepted(runId)       => onSubmit(runId, sc1)
      case Invalid(runId, error) => Invalid(runId, UnsupportedCommandIssue(error.reason))
    }
  }

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = {
    log.info(s"Bgrx Assembly: Command - $runId is being validated")

    controlCommand match {
      case setup: Setup =>
        log.info("Bgrx Assembly: Command type validation is Successful")
        log.info("Bgrx Assembly: Validating command name")

        setup.commandName match {
          case CommandName("move") => {

            log.info("Bgrx Assembly: Command name validation is successful")
            log.info("Bgrx Assembly: Validating Parameters")

            val validateParamasRes = validateParams(setup)
            validateParamasRes match {

              case Right(_) => {
                log.info("Bgrx Assembly: Parameters' validation is Successful")
                Accepted(runId)
              }
              case Left(error) => {
                log.error(s"RgrpHcd: Validation is Failure. ${error.reason}")
                Invalid(runId, error)
              }
            }
          }
          case _ => {
            log.error(s"Bgrx Assembly: Validation is Failure. $sourcePrefix takes only 'move' Setup as commands")
            Invalid(runId, UnsupportedCommandIssue(s"$sourcePrefix takes only 'move' Setup as commands"))
          }
        }
      case _: Observe =>
        log.error(s"Bgrx Assembly: Validation is Failure. $sourcePrefix prefix only accepts Setup Commands")
        Invalid(runId, UnsupportedCommandIssue("Observe commands are not supported"))
    }
  }

  private def validateParams(setup: Setup): Either[CommandIssue, Parameter[Int]] = {
    val issueOraccepted = for {
      bgid        <- setup.get(gratingModeKey).toRight(MissingKeyIssue("bgid not found"))
      targetAngle <- setup.get(targetAngleKey).toRight(MissingKeyIssue("targetAngle not found"))
      cw          <- setup.get(cwKey).toRight(MissingKeyIssue("CW not found"))
      // _           <- setup.get(GratingModeKey).toRight(CommandIssue.WrongParameterTypeIssue("GratingMode not found"))
      _     <- inRange(cw, minCW, maxCW)
      _     <- inRange(targetAngle, minTargetAngle, maxTargetAngle)
      param <- validateParam(bgid, targetAngle)
    } yield param
    issueOraccepted
  }

  private def inRange(parameter: Parameter[Int], minVal: Parameter[Int], maxVal: Parameter[Int]) = {
    if (parameter.head >= minVal.head & parameter.head <= maxVal.head) Right(parameter)
    else Left(ParameterValueOutOfRangeIssue(s"${parameter.keyName} should be in range of $minVal and $maxVal"))
  }

  private def validateParam(bgid: Parameter[String], targetAngle: Parameter[Int]) = {
    bgid.head match {
      case "bgid1" =>
        if (targetAngle.values.head == 0) Right(targetAngle)
        else Left(ParameterValueOutOfRangeIssue(s"TargetAngle should be in the given grating mode's range"))
      case "bgid2" =>
        if (targetAngle.values.head == 15) Right(targetAngle)
        else Left(ParameterValueOutOfRangeIssue(s"TargetAngle should be in the given grating mode's range"))
      case "bgid3" =>
        if (targetAngle.values.head >= 25 & targetAngle.values.head <= 35) Right(targetAngle)
        else Left(ParameterValueOutOfRangeIssue(s"TargetAngle should be in the given grating mode's range"))
      case "bgid4" =>
        if (targetAngle.values.head == 45) Right(targetAngle)
        else Left(ParameterValueOutOfRangeIssue(s"TargetAngle should be in the given grating mode's range"))
      case "bgid5" =>
        if (targetAngle.values.head == 45) Right(targetAngle)
        else Left(ParameterValueOutOfRangeIssue(s"TargetAngle should be in the given grating mode's range"))
      case _ => Left(CommandIssue.WrongParameterTypeIssue(s"Wrong Grating Mode"))
    }
  }

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = {
    log.info(s"Bgrx Assembly: handling command: ${controlCommand.commandName} $controlCommand")
    controlCommand match {
      case setup: Setup => {
        hcdCS match {
          case Some(cs) => {
            cs.submit(controlCommand)
            Completed(runId)
          }
          case None => {
            log.error("no HCD available to send the command")
            Error(runId, s"A needed HCD is not available: ${hcdConnection.componentId}")
          }
        }
      }
      case _: Observe => Invalid(runId, UnsupportedCommandIssue("Observe commands not supported"))
    }
  }

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

}
