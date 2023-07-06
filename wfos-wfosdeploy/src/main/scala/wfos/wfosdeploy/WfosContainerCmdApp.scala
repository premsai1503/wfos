package wfos.wfosdeploy

import csw.framework.deploy.containercmd.ContainerCmd
import csw.prefix.models.Subsystem

object WfosContainerCmdApp extends App {

  ContainerCmd.start("wfos_container_cmd_app", Subsystem.withNameInsensitive("wfos"), args)

}
