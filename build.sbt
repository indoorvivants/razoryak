val Version = new {
  val CE            = "3.2.9"
  val Cats          = "2.6.1"
  val Coursier      = "2.0.16"
  val Decline       = "2.1.0"
  val CatsParse     = "0.3.1"
  val Semver4s      = "0.3.0"
  val Circe         = "0.14.1"
  val Weaver        = "0.7.6"
  val KindProjector = "0.13.2"

  val Scala           = "2.13.6"
  val OrganizeImports = "0.5.0"
}

lazy val razoryak = project
  .in(file("."))
  .settings(
    moduleName                             := "razoryak",
    name                                   := "razoryak",
    libraryDependencies += "org.typelevel" %% "cats-effect" % Version.CE,
    libraryDependencies += "org.typelevel" %% "cats-core"   % Version.Cats,
    libraryDependencies += "org.typelevel" %% "cats-parse"  % Version.CatsParse,
    libraryDependencies += "io.get-coursier" %% "coursier"   % Version.Coursier,
    libraryDependencies += "com.monovore"    %% "decline"    % Version.Decline,
    libraryDependencies += "com.heroestools" %% "semver4s"   % Version.Semver4s,
    libraryDependencies += "io.circe"        %% "circe-core" % Version.Circe,
    libraryDependencies += "io.circe" %% "circe-parser" % Version.Circe,
    libraryDependencies += "com.disneystreaming" %% "weaver-cats" % Version.Weaver % Test,
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier-cache"     % Version.Coursier,
      "io.get-coursier" %% "coursier-core"      % Version.Coursier,
      "org.typelevel"   %% "cats-effect-kernel" % Version.CE,
      "org.typelevel"   %% "cats-effect-std"    % Version.CE,
      "org.typelevel"   %% "cats-kernel"        % Version.Cats
    ),
    Compile / mainClass := Some("razoryak.Main"),
    unusedCompileDependenciesFilter -= moduleFilter(
      "org.scalameta",
      "svm-subs"
    ),
    nativeImageOptions ++= Seq(
      "--enable-https",
      "--enable-http",
      "--no-fallback"
    ),
    run / fork := true,
    nativeImageOptions ++= {
      Seq("-H:+DashboardAll", "-H:DashboardDump=razoryak-contents").filter(_ =>
        sys.env.contains("DEBUG_GRAAL")
      )
    },
    addCompilerPlugin(
      "org.typelevel" %% "kind-projector" % Version.KindProjector cross CrossVersion.full
    ),
    testFrameworks += new TestFramework("weaver.framework.CatsEffect")
  )
  .enablePlugins(NativeImagePlugin)

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
  s"Compile/scalafix --rules $scalafixRules",
  s"Test/scalafix --rules $scalafixRules",
  "Test/scalafmtAll",
  "Compile/scalafmtAll",
  "scalafmtSbt",
  "headerCreate",
  "undeclaredCompileDependenciesTest"
).mkString(";")

inThisBuild(
  Seq(
    scalaVersion      := Version.Scala,
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalafixDependencies += "com.github.liancheng" %% "organize-imports" % Version.OrganizeImports,
    organization     := "com.indoorvivants",
    organizationName := "Anton Sviridov",
    homepage         := Some(url("https://github.com/indoorvivants/razoryak")),
    startYear        := Some(2021),
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
