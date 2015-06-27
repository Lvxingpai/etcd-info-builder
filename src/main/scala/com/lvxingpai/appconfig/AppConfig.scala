package com.lvxingpai.appconfig

import java.io.{BufferedReader, InputStreamReader}
import java.net.URL
import java.util.{Map => JavaMap}

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}

/**
 * Created by zephyre on 5/18/15.
 */
object AppConfig {

  /**
   * 默认的配置（从命令行变量和application.conf文件中读取，不访问etcd数据库）
   */
  lazy val defaultConfig = buildDefaultConfig()

  def buildDefaultConfig() = {
    val etcdDefaults = ConfigFactory.empty()
      .withValue("etcd.host", ConfigValueFactory.fromAnyRef("etcd"))
      .withValue("etcd.port", ConfigValueFactory.fromAnyRef(2379))
    ConfigFactory.load().withFallback(etcdDefaults)
  }

  def buildConfig(confKeys: Option[Seq[(String, String)]] = None, services: Option[Seq[(String, String)]] = None)
                 (implicit executor: ExecutionContext): Future[Config] = {
    val confList = confKeys.getOrElse(Seq()) map (v => {
      val (key, alias) = v
      buildConfigSingle(key, alias)
    })

    val serviceList = services.getOrElse(Seq()) map (v => {
      val (key, alias) = v
      getServiceConfEntry(key, alias)
    })

    val result = Future.sequence(confList ++ serviceList)
    val conf = result map (_.reduceLeft(_.withFallback(_)))
    conf map (_.withFallback(defaultConfig))
  }

  // 获得数据库的配置。主要的键有两个：host和port
  private def getServiceConfEntry(serviceName: String, alias: String)
                                 (implicit executor: ExecutionContext): Future[Config] = {
    val (etcdHost, etcdPort) = getEtcdProperties
    val reqUrl = s"http://$etcdHost:$etcdPort/v2/keys/backends/$serviceName?recursive=true"

    var in: BufferedReader = null
    val response = Future {
      try {
        val con = new URL(reqUrl).openConnection()
        in = new BufferedReader(new InputStreamReader(con.getInputStream))
        in.readLine()
      } finally {
        if (in != null)
          in.close()
      }
    }

    // 从一个节点，得到以下形式的HashMap：("mongo": {"a521fe": {"host": "..", "port": 1234}})
    def buildEntry(node: JsonNode): (String, JavaMap[String, Any]) = {
      val key = node.get("key").asText().split("/").last
      val tmp = node.get("value").asText().split(":")
      val host = tmp(0)
      val port = tmp(1).toInt

      key -> Map("host" -> host, "port" -> port)
    }

    response map (body => {
      val mapper = new ObjectMapper() with ScalaObjectMapper
      mapper.registerModule(DefaultScalaModule)
      val confNode = mapper.readValue[JsonNode](body)

      val innerMap: JavaMap[String, String] = Map(confNode.get("node").get("nodes").toSeq map (jn => {
        jn.get("key").asText().split("/").last -> jn.get("value").asText()
      }): _*)
      val outerMap: JavaMap[String, Any] = Map(alias -> innerMap)

      ConfigFactory.parseMap(Map("backends" -> outerMap))
    })
  }

  /**
   * 获得etcd配置。按照以下优先级：-Doptions > default values
   * @return
   */
  private def getEtcdProperties: (String, Int) = {
    val etcdHost = defaultConfig.getString("etcd.host")
    val etcdPort = defaultConfig.getInt("etcd.port")
    (etcdHost, etcdPort)
  }

  private def buildConfNode(node: JsonNode): java.util.Map[String, Object] = {
    // 是否为dir类型的键
    def isDir(rootNode: JsonNode): Boolean = {
      val dirNode = node.get("dir")
      dirNode != null && dirNode.asBoolean()
    }

    // 获得key
    def getKeyName(rootNode: JsonNode): String = {
      val keyStr = rootNode.get("key").asText()
      (keyStr split "/").last
    }

    if (isDir(node)) {
      val innerMap = new java.util.HashMap[String, Object]()
      for (item <- node.get("nodes")) {
        val item2 = buildConfNode(item)
        for (entrySet <- item2.entrySet())
          innerMap.put(entrySet.getKey, entrySet.getValue)
      }

      val key = getKeyName(node)
      val m = new java.util.HashMap[String, Object]()
      m.put(key, innerMap)
      m
    } else {
      val key = getKeyName(node)
      val valueNode = node.get("value")

      val value = if (valueNode.canConvertToInt)
        Int.box(valueNode.asInt())
      else if (valueNode.canConvertToLong)
        Long.box(valueNode.asLong())
      else if (valueNode.isDouble)
        Double.box(valueNode.asDouble())
      else if (valueNode.isTextual) {
        val rawVal = valueNode.asText()
        try {
          Int.box(rawVal.toInt)
        } catch {
          case _: NumberFormatException => try {
            Long.box(rawVal.toLong)
          } catch {
            case _: NumberFormatException => try {
              Double.box(rawVal.toDouble)
            } catch {
              case _: NumberFormatException => rawVal
            }
          }
        }
      } else if (valueNode.isBoolean)
        Boolean.box(valueNode.asBoolean())
      else if (valueNode.isNull)
        null
      else
        throw new IllegalArgumentException

      val m = new java.util.HashMap[String, Object]()
      m.put(key, value)
      m
    }
  }

  // 从etcd数据库获取配置数据
  private def buildConfigSingle(etcdKey: String, alias: String)(implicit executor: ExecutionContext): Future[Config] = {
    val (etcdHost, etcdPort) = getEtcdProperties
    val reqUrl = s"http://$etcdHost:$etcdPort/v2/keys/project-conf/$etcdKey?recursive=true"

    var in: BufferedReader = null
    val response = Future {
      try {
        val con = new URL(reqUrl).openConnection()
        in = new BufferedReader(new InputStreamReader(con.getInputStream))
        in.readLine()
      } finally {
        if (in != null)
          in.close()
      }
    }

    response map (body => {
      val mapper = new ObjectMapper() with ScalaObjectMapper
      mapper.registerModule(DefaultScalaModule)
      val confNode = mapper.readValue[JsonNode](body)

      val confList = for {
        confEntry <- confNode.get("node").get("nodes")
        conf <- buildConfNode(confEntry)
      } yield conf

      val innerMap = new java.util.HashMap[String, Object]()
      confList foreach (v => {
        val (key, value) = v
        innerMap.put(key, value)
      })

      val configMap = new java.util.HashMap[String, Object]()
      configMap.put(alias, innerMap)
      ConfigFactory.parseMap(configMap)
    })
  }
}
