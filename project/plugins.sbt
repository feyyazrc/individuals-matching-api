import sbt.Resolver

resolvers += Resolver.typesafeRepo("releases")
resolvers += MavenRepository("HMRC-open-artefacts-maven2", "https://open.artefacts.tax.service.gov.uk/maven2")
resolvers += Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)

addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "3.3.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-git-versioning" % "2.4.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-distributables" % "2.1.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-artifactory" % "2.0.0")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.23" exclude("org.slf4j", "slf4j-simple"))

addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.16")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.1")