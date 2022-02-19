import sbt.Keys.{publishMavenStyle, scalaVersion}
name := "ergopuppet"
organization := "io.github.dav009"
publishArtifact in (Compile, packageSrc) := true
publishArtifact in (Compile, packageDoc) := true
publishMavenStyle := true
publishTo := sonatypePublishToBundle.value
scalaVersion := "2.12.10"
enablePlugins(GitVersioning)
scmInfo := Some(
    ScmInfo(url("https://github.com/dav009/ergo-puppet"), "scm:git@github.com:dav009/ergo-puppet.git")
)

lazy val sonatypePublic = "Sonatype Public" at "https://oss.sonatype.org/content/groups/public/"
lazy val sonatypeReleases = "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/"
lazy val sonatypeSnapshots = "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

resolvers ++= Seq(Resolver.mavenLocal, sonatypeReleases, sonatypeSnapshots, Resolver.mavenCentral)

libraryDependencies += "org.ergoplatform" %% "ergo-appkit" % "4.0.7"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" 
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % Test
libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.14.+"

libraryDependencies += ("org.scorexfoundation" %% "sigma-state" % "4.0.5" ).classifier("tests") % "compile->compile;test->compile"







