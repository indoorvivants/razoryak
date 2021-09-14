package razoryak

object ResolutionTests extends weaver.SimpleIOSuite with Setup {
  resolutionTest(
    "simple case - just publish the library",
    basic("com.geirsson", "metaconfig-core", Scala3_RC2),
    "metaconfig-3.0.0-RC2.json"
  ) { case (_, upgrade, publish) =>
    expect.all(
      upgrade.isEmpty,
      publish.count(_.artifact.name == "metaconfig-core") == publish.size
    )
  }

  resolutionTest(
    "scala 3 final",
    basic("com.nrinaudo", "kantan.csv", Scala3),
    "kantan-3.json"
  ) { case (_, upgrade, publish) =>
    expect.all(
      upgrade.isEmpty
    ) and
      exists(publish) { v =>
        expect.all(
          v.artifact.name == "kantan.codecs",
          v.artifact.org == "com.nrinaudo"
        )
      } and
      exists(publish) { v =>
        expect.all(v.artifact.name == "imp", v.artifact.org == "org.spire-math")
      } and
      exists(publish) { v =>
        expect.all(
          v.artifact.name == "kantan.csv",
          v.artifact.org == "com.nrinaudo"
        )
      }
  }
}
