package com.lvxingpai.appconfig

import java.util.{ HashMap => JMap }

import com.typesafe.config.Config

import scala.concurrent.Future

/**
 * Created by zephyre on 6/28/15.
 */
class EtcdConfBuilder(etcdHost: String,
    etcdPort: Int,
    val keys: Seq[(String, String)] = Seq()) extends EtcdBuilder {

  override val urlTemplate = s"http://$etcdHost:$etcdPort/v2/keys/project-conf/%s?recursive=true"

  override def addKey(key: String, alias: String): EtcdConfBuilder = {
    val newKeys = this.keys :+ key -> alias
    EtcdConfBuilder(etcdHost, etcdPort, newKeys)
  }

  override def addKey(key: String): EtcdBuilder = addKey(key, key)

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
  def apply(etcdHost: String, etcdPort: Int, keys: Seq[(String, String)] = Seq()) =
    new EtcdConfBuilder(etcdHost, etcdPort, keys)
}