name := "InfoQ Video RSS"

version := "0.0.1"

organization := "com.blogspot.sindu"

scalaVersion := "2.11.1"

resolvers ++= Seq("snapshots"     at "https://oss.sonatype.org/content/repositories/snapshots",
                  "staging"       at "https://oss.sonatype.org/content/repositories/staging",
                  "releases"      at "https://oss.sonatype.org/content/repositories/releases"
                 )

jetty()

unmanagedResourceDirectories in Test <+= (baseDirectory) { _ / "src/main/webapp" }

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature")

libraryDependencies ++= {
  val liftVersion = "2.6-M4"
  Seq(
    "net.liftweb"       %% "lift-webkit"        % liftVersion        % "compile",
    "org.eclipse.jetty" % "jetty-webapp"        % "9.2.1.v20140609"  % "container,test",
    "org.eclipse.jetty" % "jetty-plus"          % "9.2.1.v20140609"  % "container,test", // For Jetty Config
    "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container,test" artifacts Artifact("javax.servlet", "jar", "jar"),
    "ch.qos.logback"    % "logback-classic"     % "1.0.6",
	"net.databinder.dispatch" %% "dispatch-core" % "0.11.1"
  )
}
