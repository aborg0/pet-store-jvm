organization := "io.github.pauljamescleary"
name := "scala-pet-store"
version := "0.0.1-SNAPSHOT"
//crossScalaVersions := Seq("3.1.2")

/*ThisBuild / */scalaVersion := "3.1.2"

resolvers += Resolver.sonatypeRepo("snapshots")

val CatsVersion = "2.8.0"
val CirceVersion = "0.14.2"
//val CirceGenericExVersion = "0.14.2"
val CirceConfigVersion = "0.9.0"
val DoobieVersion = "1.0.0-RC2"
//val EnumeratumCirceVersion = "1.7.0"
val H2Version = "2.1.214"
val Http4sVersion = "0.23.12"
val KindProjectorVersion = "0.13.2"
val LogbackVersion = "1.2.11"
val Slf4jVersion = "1.7.36"
val ScalaCheckVersion = "1.16.0"
val ScalaTestVersion = "3.2.12"
val ScalaTestPlusVersion = "3.2.12.0"
val FlywayVersion = "8.5.13"
val TsecVersion = "0.4.0"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % CatsVersion,
  "io.circe" %% "circe-generic" % CirceVersion,
  "io.circe" %% "circe-literal" % CirceVersion,
//  "io.circe" %% "circe-generic-extras" % CirceGenericExVersion,
  "io.circe" %% "circe-parser" % CirceVersion,
  "com.hunorkovacs" %% "circe-config" % CirceConfigVersion,
  "org.tpolecat" %% "doobie-core" % DoobieVersion,
  "org.tpolecat" %% "doobie-h2" % DoobieVersion,
  "org.tpolecat" %% "doobie-scalatest" % DoobieVersion,
  "org.tpolecat" %% "doobie-hikari" % DoobieVersion,
//  "com.beachape" %% "enumeratum-circe" % EnumeratumCirceVersion,
  "com.h2database" % "h2" % H2Version,
  "org.http4s" %% "http4s-blaze-server" % Http4sVersion,
  "org.http4s" %% "http4s-circe" % Http4sVersion,
  "org.http4s" %% "http4s-dsl" % Http4sVersion,
  "ch.qos.logback" % "logback-classic" % LogbackVersion,
  "org.flywaydb" % "flyway-core" % FlywayVersion,
  "org.http4s" %% "http4s-blaze-client" % Http4sVersion % Test,
  "org.scalacheck" %% "scalacheck" % ScalaCheckVersion % Test,
  "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
  "org.scalatestplus" %% "scalacheck-1-16" % ScalaTestPlusVersion % Test,
  // Authentication dependencies
  "io.github.jmcardon" %% "tsec-common" % TsecVersion,
  "io.github.jmcardon" %% "tsec-password" % TsecVersion,
  "io.github.jmcardon" %% "tsec-mac" % TsecVersion,
  "io.github.jmcardon" %% "tsec-signatures" % TsecVersion,
  "io.github.jmcardon" %% "tsec-jwt-mac" % TsecVersion,
  "io.github.jmcardon" %% "tsec-jwt-sig" % TsecVersion,
  "io.github.jmcardon" %% "tsec-http4s" % TsecVersion,
)

dependencyOverrides += "org.slf4j" % "slf4j-api" % Slf4jVersion

//addCompilerPlugin(
//  ("org.typelevel" %% "kind-projector" % KindProjectorVersion).cross(CrossVersion.full),
//)

enablePlugins(ScalafmtPlugin, JavaAppPackaging, GhpagesPlugin, MicrositesPlugin, MdocPlugin)

// Microsite settings
git.remoteRepo := "git@github.com:pauljamescleary/scala-pet-store.git"

micrositeGithubOwner := "pauljamescleary"

micrositeGithubRepo := "scala-pet-store"

micrositeName := "Scala Pet Store"

micrositeDescription := "An example application using FP techniques in Scala"

micrositeBaseUrl := "scala-pet-store"

// Note: This fixes error with sbt run not loading config properly
run / fork := true

dockerExposedPorts ++= Seq(8080)
