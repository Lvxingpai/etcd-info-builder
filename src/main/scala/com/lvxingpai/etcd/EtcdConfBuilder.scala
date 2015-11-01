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
import com.fasterxml.jackson.databind.node.{ JsonNodeFactory, NullNode }
import com.typesafe.config.{ Config, ConfigFactory, ConfigValue, ConfigValueFactory }
import dispatch.Future

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext

/**
 * 从etcd获得配置
 *
 * Created by zephyre on 10/31/15.
 */
class EtcdConfBuilder(host: String, port: Int, schema: String = "http", auth: Option[EtcdAuth] = None)
    extends EtcdBaseBuilder(host, port, schema, auth) {

  private val jsonFactory = new JsonNodeFactory(false)

  /**
   * 遍历一个JsonNode，并获得其下的所有叶节点，以及对应的path。比如：
   * Seq( ("mongo.host": ConfigText("localhost")), ("mongo.port": ConfigInt(27017)) )
   * @param node 需要遍历的节点
   * @return
   */
  private def walkNode(node: JsonNode, alias: Option[String] = None, prefix: Option[String] = None): Seq[(String, ConfigValue)] = {

    // 根节点的key字段，作为prefix
    val actualPrefix = prefix getOrElse (Option(node get "key") map (_.asText) getOrElse "")
    if (Option(node get "dir") exists (_.asBoolean)) {
      // 中间节点，检查nodes字段
      val tmp = Option(node get "nodes") getOrElse jsonFactory.arrayNode()
      if (!tmp.isArray) {
        // 默认返回[]
        Seq()
      } else {
        // 递归调用
        (tmp flatMap (subNode => walkNode(subNode, alias, Some(actualPrefix)))).toSeq
      }
    } else {
      // 叶节点
      // 获得path
      val key = Option(node get "key") map (_.asText) getOrElse ""
      val pattern = s"""$actualPrefix(.+)""".r
      // 真实的，去除prefix的key
      val actualKey = pattern findFirstMatchIn key map (_.group(1)) getOrElse ""
      val path = actualKey split "/" filter (_.nonEmpty) mkString "."

      // 获得Config对象
      val valueNode = Option(node get "value") getOrElse NullNode.getInstance
      val value: ConfigValue = ConfigValueFactory.fromAnyRef(if (valueNode.isTextual) {
        valueNode.asText
      } else if (valueNode.isDouble) {
        valueNode.asDouble
      } else if (valueNode.isInt) {
        valueNode.asInt
      } else if (valueNode.isLong) {
        valueNode.asLong
      } else if (valueNode.isBoolean) {
        valueNode.asBoolean
      } else if (valueNode.isNull) {
        ConfigValueFactory.fromAnyRef(null)
      })

      // 如果指定了alias
      val actualPath = s"${alias getOrElse (prefix getOrElse "")}.$path"
      Seq(actualPath -> value)
    }
  }

  /**
   * 返回配置信息
   * @return
   */
  override def build()(implicit executor: ExecutionContext): Future[Config] = {

    // 得到key和alias之间的关系
    val aliasMap = Map(this.keys.toSeq: _*)

    for {
      etcdData <- fetchEtcdData(key => s"/v2/keys/project-conf/$key?recursive=true")
    } yield {
      // 将所有的key对应的配置项目列表汇总
      val configItems = etcdData.entrySet().toSeq flatMap (entry => {
        val key = entry.getKey
        val alias = aliasMap(key)
        val node = entry.getValue
        // 获得一系列配置项目
        walkNode(node, Some(alias))
      })

      // 由汇总的配置项目列表，获得最终的Config对象
      configItems.foldLeft(ConfigFactory.empty) {
        case (c, (path, node)) =>
          c.withValue(path, node)
      }
    }
  }
}
