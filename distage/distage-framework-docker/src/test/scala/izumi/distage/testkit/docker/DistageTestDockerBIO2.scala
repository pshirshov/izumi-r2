package izumi.distage.testkit.docker

import distage.DIKey
import izumi.distage.docker.examples.DynamoDocker
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.docker.fixtures.PgSvcExample
import izumi.distage.testkit.scalatest.DistageBIOSpecScalatest
import izumi.fundamentals.platform.properties.EnvVarsCI
import izumi.logstage.api.Log
import zio.IO

// this tests needed to check mutex for reusable containers during parallel test runs
final class DistageTestDockerBIO2 extends DistageBIOSpecScalatest[IO] {

  // ignore docker tests on CI (nested docker trouble)
  if (!EnvVarsCI.isIzumiCI()) {
    "distage test runner should start only one container for reusable" should {
      "support docker resources 2" in {
        service: PgSvcExample =>
          for {
            _ <- IO(println(s"ports/1: pg=${service.pg} ddb=${service.ddb} kafka=${service.kafka} cs=${service.cs}"))
          } yield ()
      }

      "support memoization 2" in {
        service: PgSvcExample =>
          for {
            _ <- IO(println(s"ports/2: pg=${service.pg} ddb=${service.ddb} kafka=${service.kafka} cs=${service.cs}"))
          } yield ()
      }
    }
  }

  override protected def config: TestConfig = {
    super.config.copy(
      memoizationRoots = Set(
        DIKey.get[PgSvcExample],
        DIKey.get[DynamoDocker.Container],
      ),
      parallelTests = true,
      testRunnerLogLevel = Log.Level.Debug
    )
  }
}
