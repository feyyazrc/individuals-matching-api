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

package unit.uk.gov.hmrc.individualsmatchingapi.connectors

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.joda.time.LocalDate
import org.scalatest.{BeforeAndAfterEach, Matchers}
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.individualsmatchingapi.connectors.CitizenDetailsConnector
import uk.gov.hmrc.individualsmatchingapi.domain.{CitizenDetails, CitizenNotFoundException, InvalidNinoException}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import unit.uk.gov.hmrc.individualsmatchingapi.support.SpecBase

class CitizenDetailsConnectorSpec extends SpecBase with Matchers with BeforeAndAfterEach {
  val stubPort = sys.env.getOrElse("WIREMOCK", "11121").toInt
  val stubHost = "127.0.0.1"
  val wireMockServer = new WireMockServer(wireMockConfig().port(stubPort))

  val http = fakeApplication.injector.instanceOf[DefaultHttpClient]
  val config = fakeApplication.injector.instanceOf[Configuration]
  val servicesConfig = fakeApplication.injector.instanceOf[ServicesConfig]

  trait Setup {
    implicit val hc = HeaderCarrier()

    val underTest = new CitizenDetailsConnector(config, http, servicesConfig) {
      override val serviceUrl = "http://127.0.0.1:11121"
    }
  }

  override def beforeEach() {
    wireMockServer.start()
    configureFor(stubHost, stubPort)
  }

  override def afterEach() {
    wireMockServer.stop()
  }

  "citizen details" should {
    val nino = "A123456AA"

    "retrieve citizen details for a valid nino" in new Setup {
      stubFor(
        get(urlEqualTo(s"/citizen-details/nino/$nino"))
          .willReturn(aResponse().withBody(s"""{
                "name":{
                  "current":{"firstName":"Amanda","lastName":"Joseph"},
                "previous":[]
                },
                "ids":{"sautr":"2432552635","nino":"$nino"},
                "dateOfBirth":"13101972"
              }""")))

      val result = await(underTest.citizenDetails(nino))

      result shouldBe CitizenDetails(Some("Amanda"), Some("Joseph"), Some(nino), Some(LocalDate.parse("1972-10-13")))
    }

    "retrieve citizen details for partial details" in new Setup {
      stubFor(
        get(urlEqualTo(s"/citizen-details/nino/$nino"))
          .willReturn(aResponse().withBody(s"""{
                "name":{
                  "current":{"firstName":"Amanda","lastName":"Joseph"},
                  "previous":[]
                },
                "ids":{"sautr":"2432552635","nino":"$nino"}
              }""")))

      val result = await(underTest.citizenDetails(nino))

      result shouldBe CitizenDetails(Some("Amanda"), Some("Joseph"), Some(nino), None)
    }

    "throw citizen not found exception if nino is not found" in new Setup {
      stubFor(
        get(urlEqualTo(s"/citizen-details/nino/$nino"))
          .willReturn(aResponse().withStatus(404)))

      intercept[CitizenNotFoundException](await(underTest.citizenDetails(nino)))
    }

    "throw invalid nino exception when nino is invalid" in new Setup {
      val invalidNino = "A123456A"
      stubFor(
        get(urlEqualTo(s"/citizen-details/nino/$invalidNino"))
          .willReturn(aResponse().withStatus(400).withBody(s"Invalid Nino: $invalidNino")))

      intercept[InvalidNinoException](await(underTest.citizenDetails(invalidNino)))
    }
  }
}
