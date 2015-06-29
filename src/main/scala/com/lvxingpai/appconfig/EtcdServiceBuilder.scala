package com.lvxingpai.appconfig

import java.util.{HashMap => JMap}

import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Future
import scala.language.implicitConversions

/**
 * Created by zephyre on 6/28/15.
 */
class EtcdServiceBuilder(val etcdHost: String,
                         val etcdPort: Int,
                         val keys: Seq[(String, String)]) extends EtcdBuilder {

  override val urlTemplate = s"http://$etcdHost:$etcdPort/v2/keys/backends/%s?recursive=true"

  override def addKey(key: String, alias: String = null): EtcdServiceBuilder = {
    val newKeys = this.keys :+ key -> (Option(alias) getOrElse key)
    EtcdServiceBuilder(etcdHost -> etcdPort, newKeys)
  }

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
  def apply(address: (String, Int) = null, keys: Seq[(String, String)] = Seq()): EtcdServiceBuilder = {
    val addr = Option(address).getOrElse({
      val etcdHost: String = Option(System.getenv("ETCD_HOST")) getOrElse "etcd"
      val etcdPort: Int = (Option(System.getenv("ETCD_PORT")) getOrElse "2379").toInt
      etcdHost -> etcdPort
    })

    new EtcdServiceBuilder(addr._1, addr._2, keys)
  }
}
