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
}
