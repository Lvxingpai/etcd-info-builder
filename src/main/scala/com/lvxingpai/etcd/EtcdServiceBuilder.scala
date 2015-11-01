//The MIT License (MIT)
//
//Copyright (c) 2015 Zephyre
//
//Permission is hereby granted, free of charge, to any person obtaining a copy
//of this software and associated documentation files (the "Software"), to deal
//in the Software without restriction, including without limitation the rights
//to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
//copies of the Software, and to permit persons to whom the Software is
//furnished to do so, subject to the following conditions:
//
//The above copyright notice and this permission notice shall be included in all
//copies or substantial portions of the Software.
//
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
//SOFTWARE.

package com.lvxingpai.etcd

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.typesafe.config.{ Config, ConfigFactory, ConfigValue, ConfigValueFactory }
import dispatch.Future

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext

/**
 * 从etcd获得服务发现信息
 *
 * Created by zephyre on 10/31/15.
 */
class EtcdServiceBuilder(host: String, port: Int, schema: String = "http", auth: Option[EtcdAuth] = None)
    extends EtcdBaseBuilder(host, port, schema, auth) {
  /**
   * 返回配置信息
   * @return
   */
  override def build()(implicit executor: ExecutionContext): Future[Config] = {
    val jsonFactory = new JsonNodeFactory(false)

    // 从nodes列表，获得整个service的入口信息
    def buildServiceEntries(node: JsonNode): Seq[(String, ConfigValue)] = {
      val serviceNodes = Option(node get "nodes") getOrElse jsonFactory.arrayNode

      // 建立service对象
      (serviceNodes flatMap (entry => {
        val nodeName = (Option(entry get "key") map (_.asText) getOrElse "" split "/").last
        val ret = Option(entry get "value") map (_.asText) getOrElse "" split ":"
        if (ret.length == 2) {
          Seq(s"$nodeName.host" -> ConfigValueFactory.fromAnyRef(ret.head),
            s"$nodeName.port" -> ConfigValueFactory.fromAnyRef(ret.last.toInt))
        } else {
          Seq()
        }
      })).toSeq
    }

    // 得到key和alias之间的关系
    val aliasMap = Map(this.keys.toSeq: _*)

    for {
      etcdData <- fetchEtcdData(key => s"/v2/keys/backends/$key?recursive=true")
    } yield {
      // 获得所有的配置项目
      val configItems = etcdData.entrySet().toSeq flatMap (entry => {
        val key = entry.getKey
        val alias = aliasMap(key)
        val node = entry.getValue

        buildServiceEntries(node) map {
          case (entryKey, configItem) =>
            s"$alias.$entryKey" -> configItem
        }
      })

      // 将所有的key对应的配置项目列表汇总
      configItems.foldLeft(ConfigFactory.empty) {
        case (c, (path, node)) =>
          c.withValue(path, node)
      }
    }
  }
}
