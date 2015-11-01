# etcd-core [![Build Status](http://ci2.lvxingpai.com/buildStatus/icon?job=etcdStore)](http://ci2.lvxingpai.com/job/etcdStore/)

和etcd结合使用，负责获得应用程序的配置。

## 示例

```scala
object Sandbox extends App {
  main(args)

  override def main(args: Array[String]): Unit = {
    // 创建builder
    val builder = new EtcdServiceBuilder("localhost", 2389)
    
    // 指定需要获取信息的key
    val conf = builder.addKeysWithAliases("redis-main" -> "redis", "mongo-dev" -> "mongo").addKeys("k2").build()
    println(Await.result(conf, 10 seconds))
  }
}
```

## 构建状态

[![Build Status](http://ci2.lvxingpai.com/buildStatus/icon?job=etcdStore)](http://ci2.lvxingpai.com/job/etcdStore/)
