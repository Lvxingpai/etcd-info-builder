package com.lvxingpai.appconfig.json

/**
 * Created by zephyre on 6/28/15.
 */
case class EtcdConfNode(key: String,
    key2: String,
    dir: Boolean,
    value: String,
    nodes: Seq[EtcdNode],
    createdIndex: Long,
    modifiedIndex: Long) {

}
