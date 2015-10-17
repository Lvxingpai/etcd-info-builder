package com.lvxingpai.appconfig.json

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.{ DeserializationContext, JsonDeserializer, JsonNode }

import scala.collection.JavaConversions._
import scala.language.{ implicitConversions, postfixOps }

/**
 * Created by zephyre on 6/28/15.
 */
class EtcdDeserializer extends JsonDeserializer[EtcdNode] {
  implicit def jsonNode2EtcdNode(data: JsonNode): EtcdNode = {
    val dir = Option(data get "dir") exists (_.asBoolean)
    val rawKey = data get "key" asText
    val key = rawKey.split("/").last
    val value = Option(data get "value") map (_.asText) getOrElse ""
    val createdIndex = Option(data get "createIndex") map (_.asLong()) getOrElse 0L
    val modifiedIndex = Option(data get "modifiedIndex") map (_.asLong()) getOrElse 0L

    val etcdNodes = Option(data get "nodes") map (nodes => {
      if (nodes.isArray)
        nodes.toList map jsonNode2EtcdNode
      else
        List()
    }) getOrElse Seq()

    EtcdNode(key, rawKey, dir, value, etcdNodes, createdIndex, modifiedIndex)
  }

  override def deserialize(jp: JsonParser, ctxt: DeserializationContext): EtcdNode = {
    val node = jp.getCodec.readTree[JsonNode](jp)

    if ((Option(node.get("action")) map (_.asText) orNull) == "get" && node.has("node"))
      node.get("node")
    else
      node
  }
}
