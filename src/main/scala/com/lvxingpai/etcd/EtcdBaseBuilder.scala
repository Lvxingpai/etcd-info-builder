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

import com.fasterxml.jackson.databind.{ JsonNode, ObjectMapper }
import com.typesafe.config.Config
import dispatch._

import scala.concurrent.ExecutionContext

/**
 * EtcdConfBuilder和EtcdServiceBuilder的抽象基类
 *
 * Created by zephyre on 10/30/15.
 */
abstract class EtcdBaseBuilder(host: String, port: Int, schema: String = "http", auth: Option[EtcdAuth]) {

  /**
   * 需要读取的键
   */
  protected val keys = scala.collection.mutable.Set[(String, String)]()

  /**
   * 添加新的键
   * @param keys
   */
  def addKeys(keys: String*): this.type = {
    keys foreach (v => this.keys += (v -> v))
    this
  }

  /**
   * 添加新的键
   * @param keys
   */
  def addKeysWithAliases(keys: (String, String)*): this.type = {
    keys foreach (this.keys += _)
    this
  }

  /**
   * 从etcd服务器获得原始的JSON数据
   * @param pathBuilder 给定一个key，获得相应的path
   */
  protected def fetchEtcdData(pathBuilder: (String => String))(implicit executor: ExecutionContext): Future[Map[String, JsonNode]] = {
    // 获得某个具体的key的内容
    def fetchEntry(key: String): Future[(String, JsonNode)] = {
      val etcdUrl = s"${this.schema}://${this.host}:${this.port}" + pathBuilder(key)
      val svc = (if (this.auth.nonEmpty) {
        val auth = this.auth.get
        url(etcdUrl) as (auth.user, auth.password)
      } else {
        url(etcdUrl)
      }) OK as.String

      val mapper = new ObjectMapper
      Http(svc) map (mapper readTree _) recover {
        // 在出错的情况下，返回：{ "node": {} }，即一个空的数据
        case _ =>
          val node = mapper.createObjectNode
          val subNode = mapper.createObjectNode
          subNode.put("dir", true)
          node set ("node", subNode)
          node
      } map (key -> _.get("node"))
    }

    // 针对每一个key，都要请求etcd数据库，获取相应的值
    Future.sequence(this.keys.toSeq map {
      case (key, _) => fetchEntry(key)
    }) map (v => Map(v: _*))
  }

  /**
   * 返回配置信息
   * @return
   */
  def build()(implicit executor: ExecutionContext): Future[Config]
}
