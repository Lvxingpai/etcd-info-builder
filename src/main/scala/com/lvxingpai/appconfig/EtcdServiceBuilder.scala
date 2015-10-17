package com.lvxingpai.appconfig

import java.util.{ HashMap => JMap }

import com.typesafe.config.{ Config, ConfigFactory }

import scala.concurrent.Future
import scala.language.implicitConversions

/**
 * Created by zephyre on 6/28/15.
 */
class EtcdServiceBuilder(etcdHost: String,
    etcdPort: Int,
    val keys: Seq[(String, String)] = Seq()) extends EtcdBuilder {

  override val urlTemplate = s"http://$etcdHost:$etcdPort/v2/keys/backends/%s?recursive=true"

  override def addKey(key: String, alias: String): EtcdServiceBuilder = {
    val newKeys = this.keys :+ key -> alias
    EtcdServiceBuilder(etcdHost, etcdPort, newKeys)
  }

  override def addKey(key: String): EtcdBuilder = addKey(key, key)

  override def build(): Future[Config] = {
    import scala.concurrent.ExecutionContext.Implicits.global

    implicit val fnMapper: (String, String) => AnyRef = (key, value) => {
      val tmp = value.split(":")
      val host = tmp.head
      val port = tmp.last
      val m = new JMap[String, AnyRef]()
      m.put("host", host)
      m.put("port", Integer.valueOf(port))
      val ret = new JMap[String, AnyRef]()
      ret.put(key, m)
      ret
    }

    val nodes = keys map (entry => {
      val (key, alias) = entry
      implicit val reqUrl = urlTemplate format key
      buildEntry(key, alias)(global, fnMapper)
    })

    val future = Future.sequence(nodes) map (configList => {
      configList.reduce((c1, c2) => c1.withFallback(c2))
    }) map (c => {
      ConfigFactory.empty().withValue("backends", c.root())
    })
    future
  }

}

object EtcdServiceBuilder {
  def apply(etcdHost: String, etcdPort: Int, keys: Seq[(String, String)] = Seq()) =
    new EtcdServiceBuilder(etcdHost, etcdPort, keys)
}
