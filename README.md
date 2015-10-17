# AppConfig
和etcd结合使用，负责获得应用程序的配置。

## 示例

```scala
import com.lvxingpai.appconfig.AppConfig

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by zephyre on 6/9/15.
 */
object Sandbox extends App {
  main(args)

  override def main(args: Array[String]): Unit = {
    val conf = AppConfig.buildConfig(Some(Seq("hedylogos")), Some(Seq(("mongo", "mongo"))))
    val result = Await.result(conf, 10 seconds)
    println(result)
  }
}
```
