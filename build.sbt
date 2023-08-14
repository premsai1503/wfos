
lazy val aggregatedProjects: Seq[ProjectReference] = Seq(
  `wfos-bgrxassembly`,
  `wfos-rgriphcd`,
  `wfos-lgriphcd`,
  `wfos-wfosdeploy`
)

lazy val `wfos-root` = project
  .in(file("."))
  .aggregate(aggregatedProjects: _*)

// assembly module
lazy val `wfos-bgrxassembly` = project
  .settings(
    libraryDependencies ++= Dependencies.Bgrxassembly
  )

// hcd module
lazy val `wfos-rgriphcd` = project
  .settings(
    libraryDependencies ++= Dependencies.Rgriphcd
  )

// hcd module
lazy val `wfos-lgriphcd` = project
  .settings(
    libraryDependencies ++= Dependencies.Lgriphcd
  )

// deploy module
lazy val `wfos-wfosdeploy` = project
  .dependsOn(
    `wfos-bgrxassembly`,
    `wfos-rgriphcd`,
    `wfos-lgriphcd`
  )
  .enablePlugins(CswBuildInfo)
  .settings(
    libraryDependencies ++= Dependencies.WfosDeploy
  )
