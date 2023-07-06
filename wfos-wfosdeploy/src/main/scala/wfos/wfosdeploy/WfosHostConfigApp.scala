package wfos.wfosdeploy

import csw.framework.deploy.hostconfig.HostConfig
import csw.prefix.models.Subsystem

object WfosHostConfigApp extends App {

  HostConfig.start("wfos_host_config_app",Subsystem.withNameInsensitive("wfos"), args)

}