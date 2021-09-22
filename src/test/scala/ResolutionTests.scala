package razoryak

object ResolutionTests extends weaver.SimpleIOSuite with Setup {
  resolutionTest(
    "simple case - just publish the library",
    basic("com.geirsson", "metaconfig-core", Scala3_RC2),
    "metaconfig-3.0.0-RC2.json"
  ) { plan =>
    expect(
      plan.publish.count(
        _.artifact.name == "metaconfig-core"
      ) == plan.publish.size
    )
  }

  resolutionTest(
    "scala 3 final",
    basic("com.nrinaudo", "kantan.csv", Scala3),
    "kantan-3.json"
  ) { plan =>
    expect(plan.upgrade.isEmpty) and
      plan.hasPublish("com.nrinaudo", "kantan.codecs") and
      plan.hasPublish("org.spire-math", "imp") and
      plan.hasPublish("com.nrinaudo", "kantan.csv")
  }

  resolutionTest(
    "attempt to publish self",
    basic("com.indoorvivants", "razoryak", Scala3),
    "razoryak-scala3.json"
  ) { plan =>
    plan.hasPublish("com.heroestools", "semver4s") and
      plan.hasUpgrade("com.lihaoyi", "utest") and
      plan.hasUse("com.monovore", "decline")
  }

  resolutionTest(
    "handle non-semver version",
    basic("net.debasishg", "redisclient", Scala3),
    "redisclient-scala3.json"
  ) { plan =>
    plan.hasPublish("net.debasishg", "redisclient") and
      expect(plan.publish.size == 1) and
      expect(plan.upgrade.size == 0) and
      expect(plan.use.size == 0)
  }
}
