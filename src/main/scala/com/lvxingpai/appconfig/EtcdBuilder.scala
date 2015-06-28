package com.lvxingpai.appconfig

import java.io.{BufferedReader, InputStreamReader}
import java.net.URL
import java.util.{HashMap => JMap}

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.lvxingpai.appconfig.json.{EtcdDeserializer, EtcdNode}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Created by zephyre on 6/28/15.
 */
abstract class EtcdBuilder {

  protected val prefix: String = null

  protected val urlTemplate = ""

  def build(): Future[Config]

  def addKey(key: String, alias: String = null): EtcdBuilder

  // 从etcd数据库获取配置数据
  protected def buildEntry(etcdKey: String, alias: String)
                          (implicit executionContext: ExecutionContext,
                           fnMapper: (String, String) => AnyRef): Future[Config] = {
    val url = urlTemplate format etcdKey
    getUrl(url) map (body => {
      val mapper = new ObjectMapper() with ScalaObjectMapper
      mapper.registerModule(DefaultScalaModule)
      val module = new SimpleModule()
      module.addDeserializer(classOf[EtcdNode], new EtcdDeserializer)
      mapper.registerModule(module)

      val node = mapper.readValue(body, classOf[EtcdNode])
      ConfigFactory.parseMap(convert2Map(node, Some(alias)))
    })
  }

  protected def getUrl(url: String)(implicit context: ExecutionContext): Future[String] = {
    val ret = Future {
      var in: BufferedReader = null
      try {
        val con = new URL(url).openConnection()
        in = new BufferedReader(new InputStreamReader(con.getInputStream))
        in.readLine()
      } finally {
        if (in != null)
          in.close()
      }
    }(context)
    ret
  }

  protected def convert2Map(etcdNode: EtcdNode, overrideKey: Option[String] = None)
                 (implicit fnMapper: (String, String) => AnyRef): JMap[String, _ <: AnyRef] = {
    val mapKey = overrideKey getOrElse etcdNode.key
    if (etcdNode.dir) {
      val map = new JMap[String, AnyRef]()
      val map2 = new JMap[String, AnyRef]
      etcdNode.nodes foreach (n => map2.putAll(convert2Map(n)))
      map.put(mapKey, map2)
      map
    }
    else
      fnMapper(mapKey, etcdNode.value).asInstanceOf[JMap[String, _ <: AnyRef]]
  }

}
