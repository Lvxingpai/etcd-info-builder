package com.lvxingpai.appconfig.json

/**
 * Created by zephyre on 6/28/15.
 */
case class EtcdNode(key: String,
  rawKey: String,
  dir: Boolean,
  value: String,
  nodes: Seq[EtcdNode],
  createdIndex: Long,
  modifiedIndex: Long)

