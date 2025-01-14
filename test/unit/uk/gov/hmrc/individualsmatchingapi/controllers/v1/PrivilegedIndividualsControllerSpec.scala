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

package unit.uk.gov.hmrc.individualsmatchingapi.controllers.v1

import java.util.UUID
import org.mockito.BDDMockito.given
import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.Mockito.{verifyNoInteractions, when}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.mvc.{ControllerComponents, Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, _}
import uk.gov.hmrc.auth.core.retrieve.EmptyRetrieval
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, InsufficientEnrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.individualsmatchingapi.controllers.v1.{LivePrivilegedIndividualsController, SandboxPrivilegedIndividualsController}
import uk.gov.hmrc.individualsmatchingapi.domain.MatchNotFoundException
import uk.gov.hmrc.individualsmatchingapi.domain.SandboxData.sandboxMatchId
import uk.gov.hmrc.individualsmatchingapi.services.{LiveCitizenMatchingService, SandboxCitizenMatchingService}
import unit.uk.gov.hmrc.individualsmatchingapi.support.SpecBase
import unit.uk.gov.hmrc.individualsmatchingapi.util.Individuals

import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}

class PrivilegedIndividualsControllerSpec
    extends SpecBase with Matchers with Results with MockitoSugar with BeforeAndAfterEach with Individuals {

  implicit val headerCarrier: HeaderCarrier = new HeaderCarrier()
  val uuid: UUID = UUID.randomUUID()

  trait Setup {

    val mockCitizenMatchingService: LiveCitizenMatchingService = mock[LiveCitizenMatchingService]
    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val controllerComponents: ControllerComponents =
      fakeApplication.injector.instanceOf[ControllerComponents]

    val liveController =
      new LivePrivilegedIndividualsController(mockCitizenMatchingService, mockAuthConnector, controllerComponents)
    val sandboxController = new SandboxPrivilegedIndividualsController(
      new SandboxCitizenMatchingService(),
      mockAuthConnector,
      controllerComponents)

    given(mockAuthConnector.authorise(any(), refEq(EmptyRetrieval))(any(), any()))
      .willReturn(successful(()))
  }

  "The live matched individual function" should {
    "respond with http 404 (not found) for an invalid matchId" in new Setup {
      when(mockCitizenMatchingService.fetchCitizenDetailsByMatchId(refEq(uuid))(any[HeaderCarrier]))
        .thenReturn(failed(new MatchNotFoundException))

      val eventualResult: Future[Result] =
        liveController.matchedIndividual(uuid.toString).apply(FakeRequest())
      status(eventualResult) mustBe NOT_FOUND
      contentAsJson(eventualResult) mustBe Json.parse(
        """{"code":"NOT_FOUND","message":"The resource can not be found"}""")
    }

    "respond with http 200 (ok) when a nino match is successful and citizen details exist" in new Setup {
      given(mockCitizenMatchingService.fetchCitizenDetailsByMatchId(refEq(uuid))(any[HeaderCarrier]))
        .willReturn(successful(citizenDetails("Joe", "Bloggs", "AB123456C", "1969-01-15")))
      val eventualResult: Future[Result] =
        liveController.matchedIndividual(uuid.toString).apply(FakeRequest())
      status(eventualResult) mustBe OK
      contentAsJson(eventualResult) mustBe Json.parse(response(uuid, "Joe", "Bloggs", "AB123456C", "1969-01-15"))
    }

    "fail with AuthorizedException when the bearer token does not have enrolment read:individuals-matching" in new Setup {
      given(
        mockAuthConnector.authorise(refEq(Enrolment("read:individuals-matching")), refEq(EmptyRetrieval))(any(), any()))
        .willReturn(failed(InsufficientEnrolments()))

      intercept[InsufficientEnrolments] {
        await(liveController.matchedIndividual(uuid.toString).apply(FakeRequest()))
      }

      verifyNoInteractions(mockCitizenMatchingService)
    }
  }

  "The sandbox matched individual function" should {

    "respond with http 404 (not found) for an invalid matchId" in new Setup {
      val eventualResult: Future[Result] =
        sandboxController.matchedIndividual(uuid.toString).apply(FakeRequest())
      status(eventualResult) mustBe NOT_FOUND
      contentAsJson(eventualResult) mustBe Json.parse(
        """{"code":"NOT_FOUND","message":"The resource can not be found"}""")
    }

    "respond with http 200 (ok) for sandbox valid matchId and citizen details exist" in new Setup {
      val eventualResult: Future[Result] = sandboxController
        .matchedIndividual(sandboxMatchId.toString)
        .apply(FakeRequest())
      status(eventualResult) mustBe OK
      contentAsJson(eventualResult) mustBe Json.parse(response(sandboxMatchId))
    }

    "not require bearer token authentication" in new Setup {
      val eventualResult: Future[Result] = sandboxController
        .matchedIndividual(sandboxMatchId.toString)
        .apply(FakeRequest())
      status(eventualResult) mustBe OK
      verifyNoInteractions(mockAuthConnector)
    }
  }

  private def response(
    matchId: UUID,
    firstName: String = "Amanda",
    lastName: String = "Joseph",
    nino: String = "NA000799C",
    dateOfBirth: String = "1960-01-15") =
    s"""
        {
           "_links": {
             "income": {
               "href": "/individuals/income/?matchId=$matchId",
               "name": "GET",
               "title": "View individual's income"
             },
             "employments": {
               "href": "/individuals/employments/?matchId=$matchId",
               "name": "GET",
               "title": "View individual's employments"
             },
             "self": {
               "href": "/individuals/matching/$matchId"
             }
           },
           "individual": {
             "firstName": "$firstName",
             "lastName": "$lastName",
             "nino": "$nino",
             "dateOfBirth": "$dateOfBirth"
           }
        }"""
}
