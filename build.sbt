name := "ergopilot"

scalaVersion := "2.12.10"

lazy val sonatypePublic = "Sonatype Public" at "https://oss.sonatype.org/content/groups/public/"
lazy val sonatypeReleases = "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/"
lazy val sonatypeSnapshots = "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

resolvers ++= Seq(Resolver.mavenLocal, sonatypeReleases, sonatypeSnapshots, Resolver.mavenCentral)

libraryDependencies += "org.ergoplatform" %% "ergo-appkit" % "dp-sigma-401-signing-func-623ece4d-SNAPSHOT"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" 
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % Test
libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.14.+"
//libraryDependencies += ("org.ergoplatform" %% "ergo-appkit" % "4.0.6" % Test).classifier("tests")// %  "compile->test"
libraryDependencies += ("org.scorexfoundation" %% "sigma-state" % "4.0.5" ).classifier("tests") % "compile->compile;test->compile"
//libraryDependencies += ("org.scorexfoundation" %% "sigma-state" % "4.0.5" % Test).classifier("tests")



