package com.lvxingpai.appconfig

import java.util.{HashMap => JMap}

import com.typesafe.config.Config

import scala.concurrent.Future

/**
 * Created by zephyre on 6/28/15.
 */
class EtcdConfBuilder(val etcdHost: String,
                      val etcdPort: Int,
                      val keys: Seq[(String, String)]) extends EtcdBuilder {
  override val urlTemplate = s"http://$etcdHost:$etcdPort/v2/keys/project-conf/%s?recursive=true"

  override def addKey(key: String, alias: String = null): EtcdConfBuilder = {
    val newKeys = this.keys :+ key -> (Option(alias) getOrElse key)
    EtcdConfBuilder(etcdHost -> etcdPort, newKeys)
  }

  override def build(): Future[Config] = {
    import scala.concurrent.ExecutionContext.Implicits.global

    implicit val fnMapper: (String, String) => AnyRef = (key, value) => {
      val m = new JMap[String, AnyRef]()
      m.put(key, value)
      m
    }

    val nodes = keys map (entry => {
      val (key, alias) = entry
      implicit val reqUrl = urlTemplate format key
      buildEntry(key, alias)(global, fnMapper)
    })

    val future = Future.sequence(nodes) map (configList => {
      configList.reduce((c1, c2) => c1.withFallback(c2))
    })
    future
  }
}

object EtcdConfBuilder {
  def apply(address: (String, Int) = null, keys: Seq[(String, String)] = Seq()): EtcdConfBuilder = {
    val addr = Option(address).getOrElse({
      val etcdHost: String = Option(System.getenv("ETCD_HOST")) getOrElse "etcd"
      val etcdPort: Int = (Option(System.getenv("ETCD_PORT")) getOrElse "2379").toInt
      etcdHost -> etcdPort
    })

    new EtcdConfBuilder(addr._1, addr._2, keys)
  }
}