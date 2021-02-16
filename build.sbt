lazy val Version_CE = "3.0.0-RC1"
lazy val Version_Cats = "2.4.1"
lazy val Version_Coursier = "2.0.12"
lazy val Version_Decline = "1.3.0"

lazy val core = project
  .in(file("."))
  .settings(
    moduleName := "razoryak",
    libraryDependencies += "org.typelevel"   %% "cats-effect" % Version_CE,
    libraryDependencies += "org.typelevel"   %% "cats-core"   % Version_Cats,
    libraryDependencies += "io.get-coursier" %% "coursier"    % Version_Coursier,
    libraryDependencies += "com.monovore"    %% "decline"     % Version_Decline,
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier-cache"     % Version_Coursier,
      "io.get-coursier" %% "coursier-core"      % Version_Coursier,
      "org.typelevel"   %% "cats-effect-kernel" % Version_CE,
      "org.typelevel"   %% "cats-effect-std"    % Version_CE,
      "org.typelevel"   %% "cats-kernel"        % Version_Cats
    )
  )

val scalafixRules = Seq(
  "OrganizeImports",
  "DisableSyntax",
  "LeakingImplicitClassVal",
  "ProcedureSyntax",
  "NoValInForComprehension"
).mkString(" ")

val CICommands = Seq(
  "clean",
  "compile",
  "test",
  "scalafmtCheckAll",
  s"scalafix --check $scalafixRules",
  "headerCheck",
  "undeclaredCompileDependenciesTest",
  "unusedCompileDependenciesTest",
  "missinglinkCheck"
).mkString(";")

val PrepareCICommands = Seq(
  s"compile:scalafix --rules $scalafixRules",
  s"test:scalafix --rules $scalafixRules",
  "test:scalafmtAll",
  "compile:scalafmtAll",
  "scalafmtSbt",
  "headerCreate",
  "undeclaredCompileDependenciesTest"
).mkString(";")

inThisBuild(
  Seq(
    scalaVersion := "2.13.4",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.5.0",
    organization := "com.indoorvivants",
    organizationName := "Anton Sviridov",
    homepage := Some(url("https://github.com/indoorvivants/razoryak")),
    startYear := Some(2021),
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers := List(
      Developer(
        "keynmol",
        "Anton Sviridov",
        "keynmol@gmail.com",
        url("https://blog.indoorvivants.com")
      )
    )
  )
)

addCommandAlias("ci", CICommands)
addCommandAlias("preCI", PrepareCICommands)