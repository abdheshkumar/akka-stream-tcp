import sbt._

/**
  * Created by abdhesh on 16/06/17.
  */
object Dependencies {

  import Versions._

  val akkaStream = Seq(
    "com.typesafe.akka" %% "akka-stream" % akkaStreamV,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaStreamV % Test
  )
}
