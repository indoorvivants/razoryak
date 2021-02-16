resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"              % "0.1.13")
addSbtPlugin("ch.epfl.scala"             % "sbt-missinglink"           % "0.3.1")
addSbtPlugin("com.github.cb372"          % "sbt-explicit-dependencies" % "0.2.16")
addSbtPlugin("org.scalameta"             % "sbt-scalafmt"              % "2.4.2")
addSbtPlugin("com.geirsson"              % "sbt-ci-release"            % "1.5.5")
addSbtPlugin("ch.epfl.scala"             % "sbt-scalafix"              % "0.9.25")
addSbtPlugin("com.eed3si9n"              % "sbt-projectmatrix"         % "0.7.0")
addSbtPlugin("de.heikoseeberger"         % "sbt-header"                % "5.6.0")
addSbtPlugin("ch.epfl.lamp"              % "sbt-dotty"                 % "0.5.2")
addSbtPlugin(
  "com.indoorvivants" % "subatomic-plugin" % "0.0.5+23-75e6dcab-SNAPSHOT"
)
