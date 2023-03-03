package part4infra

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory

object L1_AkkaConfiguration {

  // You can find the reference Akka configuration file, along with everything you can possibly configure, at this link:
  // https://doc.akka.io/docs/akka/current/general/configuration-reference.html
  object SimpleLoggingActor {
    def apply(): Behavior[String] = Behaviors.receive { (context, message) =>
      context.log.info(message)
      Behaviors.same
    }
  }

  // 1 - inline configuration
  def demoInlineConfig(): Unit = {
    // HOCON, (Human-Optimized Config Object Notation) superset of JSON, managed by Lightbend
    // this format also is used by play framework
    val configString: String =
      """
        |akka {
        |  loglevel = "DEBUG"
        |}
        |""".stripMargin
    val config = ConfigFactory.parseString(configString)
    val system = ActorSystem(SimpleLoggingActor(), "ConfigDemo", config)

    system ! "A message to remember"

    Thread.sleep(1000)
    system.terminate()
  }

  // 2 - default config file
  def demoConfigFile(): Unit = {
    // here we're loading a specific configuration (mySpecialConfig) inside application.conf file
    val specialConfig = ConfigFactory.load().getConfig("mySpecialConfig")
    val system = ActorSystem(SimpleLoggingActor(), "ConfigDemo", specialConfig)

    system ! "A message to remember"

    Thread.sleep(1000)
    system.terminate()
  }

  // 3 - a different config in another file
  def demoSeparateConfigFile(): Unit = {
    val separateConfig = ConfigFactory.load("secretDir/secretConfiguration.conf")
    println(separateConfig.getString("akka.loglevel"))
  }

  // 4 - different file formats (JSON, properties)
  def demoOtherFileFormats(): Unit = {
    val jsonConfig = ConfigFactory.load("json/jsonConfiguration.json")
    println(s"json config with custom property: ${jsonConfig.getString("aJsonProperty")}")
    println(s"json config with Akka property: ${jsonConfig.getString("akka.loglevel")}")

    // properties format
    val propsConfig = ConfigFactory.load("properties/propsConfiguration.properties")
    println(s"properties config with custom property: ${propsConfig.getString("mySimpleProperty")}")
    println(s"properties config with Akka property: ${propsConfig.getString("akka.loglevel")}")
  }

  def main(args: Array[String]): Unit = {
    demoInlineConfig()
  }

}
