package com.lvxingpai.appconfig

import org.scalatest.{FeatureSpec, GivenWhenThen, ShouldMatchers}

/**
 * Created by zephyre on 6/21/15.
 */
class AppConfigTest extends FeatureSpec with ShouldMatchers with GivenWhenThen {
  feature("AppConfig's default config should contain etcd entrypoints") {

    def assertEtcdEntrypoints(assume: (String, Int)) = {
      val (host, port) = assume
      val conf = AppConfig.buildDefaultConfig()
      conf.getString("etcd.host") should be(host)
      conf.getInt("etcd.port") should be(port)
    }

    scenario("The default entrypoint is etcd:2379") {
      assertEtcdEntrypoints("etcd" -> 2379)
    }
  }


}
