/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package component.uk.gov.hmrc.individualsmatchingapi.stubs

import java.util.concurrent.TimeUnit

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.scalatest._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.HeaderNames.{ACCEPT, AUTHORIZATION, CONTENT_TYPE}
import play.api.inject.guice.GuiceApplicationBuilder
import play.mvc.Http.MimeTypes.JSON
import uk.gov.hmrc.individualsmatchingapi.repository.NinoMatchRepository

import scala.concurrent.Await.result
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

trait BaseSpec
    extends FeatureSpec with BeforeAndAfterAll with BeforeAndAfterEach with Matchers with GuiceOneServerPerSuite
    with GivenWhenThen {

  override lazy val port = 9000
  implicit override lazy val app: Application = GuiceApplicationBuilder()
    .configure(
      "auditing.enabled"                           -> false,
      "auditing.traceRequests"                     -> false,
      "microservice.services.auth.port"            -> AuthStub.port,
      "microservice.services.citizen-details.port" -> CitizenDetailsStub.port,
      "microservice.services.matching.port"        -> MatchingStub.port,
      "mongodb.uri"                                -> "mongodb://127.0.0.1:27017/nino-match-repository-it",
      "run.mode"                                   -> "It",
      "versioning.unversionedContexts"             -> List("/match-record")
    )
    .build()

  val timeout = Duration(5, TimeUnit.SECONDS)
  val serviceUrl = s"http://127.0.0.1:$port"
  val mocks = Seq(AuthStub, CitizenDetailsStub, MatchingStub)
  val mongoRepository = app.injector.instanceOf[NinoMatchRepository]
  val authToken = "Bearer AUTH_TOKEN"

  val acceptHeaderV1 = ACCEPT                        -> "application/vnd.hmrc.1.0+json"
  val acceptHeaderP1 = ACCEPT                        -> "application/vnd.hmrc.P1.0+json"
  val acceptHeaderP2 = ACCEPT                        -> "application/vnd.hmrc.2.0+json"
  val testCorrelationHeader = "CorrelationId"        -> "188e9400-b636-4a3b-80ba-230a8c72b92a"
  val invalidTestCorrelationHeader = "CorrelationId" -> "test"

  protected def requestHeaders(
    acceptHeader: (String, String) = acceptHeaderV1,
    correlationHeader: (String, String) = testCorrelationHeader) =
    Map(CONTENT_TYPE -> JSON, AUTHORIZATION -> authToken, acceptHeader, correlationHeader)

  protected def errorResponse(message: String) =
    s"""{"code":"INVALID_REQUEST","message":"$message"}"""

  override protected def beforeEach(): Unit = {
    mocks.foreach(m => if (!m.server.isRunning) m.server.start())
    result(mongoRepository.drop, timeout)
    result(mongoRepository.ensureIndexes, timeout)
  }

  override protected def afterEach(): Unit =
    mocks.foreach(_.mock.resetMappings())

  override def afterAll(): Unit = {
    mocks.foreach(_.server.stop())
    result(mongoRepository.drop, timeout)
  }
}

case class MockHost(port: Int) {
  val server = new WireMockServer(WireMockConfiguration.wireMockConfig().port(port))
  val mock = new WireMock("127.0.0.1", port)
  val url = s"http://127.0.0.1:$port"
}
