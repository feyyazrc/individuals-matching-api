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

package unit.uk.gov.hmrc.individualsmatchingapi.controllers.v2

import java.util.UUID
import org.mockito.BDDMockito.given
import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.Mockito.{times, verify, verifyNoInteractions, when}
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.json.Json.parse
import play.api.mvc.{AnyContentAsEmpty, ControllerComponents, PlayBodyParsers, Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, _}
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, Enrolments, InsufficientEnrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.individualsmatchingapi.audit.AuditHelper
import uk.gov.hmrc.individualsmatchingapi.controllers.v2.PrivilegedCitizenMatchingController
import uk.gov.hmrc.individualsmatchingapi.domain._
import uk.gov.hmrc.individualsmatchingapi.services.{LiveCitizenMatchingService, ScopesService}
import unit.uk.gov.hmrc.individualsmatchingapi.support.SpecBase

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.failed
import scala.util.Random

class PrivilegedCitizenMatchingControllerSpec
    extends SpecBase with Matchers with MockitoSugar with Results with BeforeAndAfterEach {

  trait Setup extends ScopesConfigHelper {

    val sampleCorrelationId = "188e9400-b636-4a3b-80ba-230a8c72b92a"
    val sampleCorrelationIdHeader: (String, String) = "CorrelationId" -> sampleCorrelationId

    val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

    val controllerComponents: ControllerComponents = fakeApplication.injector.instanceOf[ControllerComponents]
    val bodyParsers: PlayBodyParsers = fakeApplication.injector.instanceOf[PlayBodyParsers]

    val mockLiveCitizenMatchingService: LiveCitizenMatchingService = mock[LiveCitizenMatchingService]

    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val mockAuditHelper: AuditHelper = mock[AuditHelper]

    val mockScopesService = new ScopesService(mockScopesConfig)

    implicit val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]

    val liveController = new PrivilegedCitizenMatchingController(
      mockLiveCitizenMatchingService,
      mockScopesService,
      mockAuthConnector,
      controllerComponents,
      bodyParsers,
      mockAuditHelper)

    given(mockAuthConnector.authorise(any(), refEq(Retrievals.allEnrolments))(any(), any()))
      .willReturn(Future.successful(Enrolments(Set(Enrolment("test-scope")))))
  }

  "live matching citizen controller" should {

    val matchId = UUID.randomUUID()

    "return 200 (Ok) for a matched citizen" in new Setup {
      when(mockLiveCitizenMatchingService.matchCitizen(any[CitizenMatchingRequest])(any[HeaderCarrier]))
        .thenReturn(Future.successful(matchId))

      val eventualResult: Future[Result] = liveController.matchCitizen()(
        fakeRequest.withBody(parse(matchingRequest())).withHeaders(("CorrelationId", sampleCorrelationId)))

      status(eventualResult) mustBe OK
      contentAsJson(eventualResult) mustBe parse(
        s"""
             {
               "_links": {
                 "individual": {
                   "href": "/individuals/matching/$matchId",
                   "title": "Get a matched individual’s information"
                 },
                 "self": {
                   "href": "/individuals/matching/"
                 }
               }
             }"""
      )

      verify(liveController.auditHelper, times(1)).auditApiResponse(any(), any(), any(), any(), any(), any())(any())

    }

    "return 200 Ok when matching a user with a '.' in their name" in new Setup {

      when(mockLiveCitizenMatchingService.matchCitizen(any())(any()))
        .thenReturn(Future.successful(matchId))

      val payload: JsObject = Json.obj(
        "firstName"   -> "Mr.",
        "lastName"    -> "St. John",
        "nino"        -> "AA112233B",
        "dateOfBirth" -> "1900-01-01"
      )

      val res: Future[Result] =
        liveController.matchCitizen()(fakeRequest.withBody(payload).withHeaders(("CorrelationId", sampleCorrelationId)))
      status(res) mustBe OK
      verify(liveController.auditHelper, times(1)).auditApiResponse(any(), any(), any(), any(), any(), any())(any())
    }

    "return 404 (Not Found) for a citizen not found" in new Setup {
      when(mockLiveCitizenMatchingService.matchCitizen(any[CitizenMatchingRequest])(any[HeaderCarrier]))
        .thenReturn(Future.failed(new CitizenNotFoundException))

      val eventualResult: Future[Result] = liveController.matchCitizen()(
        fakeRequest.withBody(parse(matchingRequest())).withHeaders(("CorrelationId", sampleCorrelationId))
      )

      status(eventualResult) mustBe NOT_FOUND
      contentAsJson(eventualResult) mustBe Json.obj(
        "code"    -> "MATCHING_FAILED",
        "message" -> "There is no match for the information provided"
      )

      verify(liveController.auditHelper, times(1)).auditApiFailure(any(), any(), any(), any(), any())(any())
    }

    "return 403 (Forbidden) when a matching exception is thrown" in new Setup {
      when(mockLiveCitizenMatchingService.matchCitizen(any[CitizenMatchingRequest])(any[HeaderCarrier]))
        .thenReturn(Future.failed(new MatchingException))

      val eventualResult: Future[Result] = liveController.matchCitizen()(
        fakeRequest.withBody(parse(matchingRequest())).withHeaders(("CorrelationId", sampleCorrelationId)))

      status(eventualResult) mustBe NOT_FOUND
      contentAsJson(eventualResult) mustBe Json.obj(
        "code"    -> "MATCHING_FAILED",
        "message" -> "There is no match for the information provided"
      )

      verify(liveController.auditHelper, times(1)).auditApiFailure(any(), any(), any(), any(), any())(any())
    }

    "return 404 (Not Found) when an invalid nino exception is thrown" in new Setup {
      when(mockLiveCitizenMatchingService.matchCitizen(any[CitizenMatchingRequest])(any[HeaderCarrier]))
        .thenReturn(Future.failed(new InvalidNinoException()))

      val eventualResult: Future[Result] = liveController.matchCitizen()(
        fakeRequest.withBody(parse(matchingRequest())).withHeaders(("CorrelationId", sampleCorrelationId)))

      status(eventualResult) mustBe NOT_FOUND
      contentAsJson(eventualResult) mustBe Json.obj(
        "code"    -> "MATCHING_FAILED",
        "message" -> "There is no match for the information provided"
      )

      verify(liveController.auditHelper, times(1)).auditApiFailure(any(), any(), any(), any(), any())(any())
    }

    "return 400 (BadRequest) for an invalid dateOfBirth" in new Setup {
      var requestBody: JsValue =
        parse("""{"firstName":"Amanda","lastName":"Joseph","nino":"NA000799C","dateOfBirth":"2020-01-32"}""")
      var eventualResult: Future[Result] =
        liveController.matchCitizen()(
          fakeRequest.withBody(requestBody).withHeaders(("CorrelationId", sampleCorrelationId)))

      status(eventualResult) mustBe BAD_REQUEST
      contentAsJson(eventualResult) mustBe Json.obj(
        "code"    -> "INVALID_REQUEST",
        "message" -> "dateOfBirth: invalid date format"
      )

      verify(liveController.auditHelper, times(1)).auditApiFailure(any(), any(), any(), any(), any())(any())

      requestBody = parse("""{"firstName":"Amanda","lastName":"Joseph","nino":"NA000799C","dateOfBirth":"20200131"}""")
      eventualResult = liveController.matchCitizen()(
        fakeRequest.withBody(requestBody).withHeaders(("CorrelationId", sampleCorrelationId)))

      status(eventualResult) mustBe BAD_REQUEST
      contentAsJson(eventualResult) mustBe Json.obj(
        "code"    -> "INVALID_REQUEST",
        "message" -> "dateOfBirth: invalid date format"
      )
    }

    "return 400 (BadRequest) for an invalid nino" in new Setup {
      val requestBody: JsValue =
        parse("""{"firstName":"Amanda","lastName":"Joseph","nino":"AB1234567","dateOfBirth":"2020-01-31"}""")
      val eventualResult: Future[Result] =
        liveController.matchCitizen()(
          fakeRequest.withBody(requestBody).withHeaders(("CorrelationId", sampleCorrelationId)))

      status(eventualResult) mustBe BAD_REQUEST
      contentAsJson(eventualResult) mustBe Json.obj(
        "code"    -> "INVALID_REQUEST",
        "message" -> "Malformed nino submitted"
      )

      verify(liveController.auditHelper, times(1)).auditApiFailure(any(), any(), any(), any(), any())(any())
    }

    "return 400 Bad Request when the first name is empty" in new Setup {
      val emptyFirstName: JsObject = Json.obj(
        "firstName"   -> "",
        "lastName"    -> "Person",
        "nino"        -> "AA112233B",
        "dateOfBirth" -> "1900-01-01"
      )

      val res: Future[Result] =
        liveController.matchCitizen()(
          fakeRequest.withBody(emptyFirstName).withHeaders(("CorrelationId", sampleCorrelationId)))

      status(res) mustBe BAD_REQUEST
      contentAsJson(res) mustBe Json.obj("code" -> "INVALID_REQUEST", "message" -> "firstName is required")

      verify(liveController.auditHelper, times(1)).auditApiFailure(any(), any(), any(), any(), any())(any())
    }

    "return 400 Bad Request when the last name is empty" in new Setup {
      val emptyLastName: JsObject = Json.obj(
        "firstName"   -> "Mr",
        "lastName"    -> "",
        "nino"        -> "AA112233B",
        "dateOfBirth" -> "1900-01-01"
      )

      val res: Future[Result] = liveController.matchCitizen()(
        fakeRequest.withBody(emptyLastName).withHeaders(("CorrelationId", sampleCorrelationId)))

      status(res) mustBe BAD_REQUEST
      contentAsJson(res) mustBe Json.obj("code" -> "INVALID_REQUEST", "message" -> "lastName is required")

      verify(liveController.auditHelper, times(1)).auditApiFailure(any(), any(), any(), any(), any())(any())
    }

    "return 400 Bad Request when the first name is greater than 35 characters" in new Setup {
      val firstNameTooLong: JsObject = Json.obj(
        "firstName"   -> Random.nextString(36),
        "lastName"    -> "Person",
        "nino"        -> "AA112233B",
        "dateOfBirth" -> "1900-01-01"
      )

      val res: Future[Result] =
        liveController.matchCitizen()(
          fakeRequest.withBody(firstNameTooLong).withHeaders(("CorrelationId", sampleCorrelationId)))

      status(res) mustBe BAD_REQUEST
      contentAsJson(res) mustBe Json
        .obj("code" -> "INVALID_REQUEST", "message" -> "firstName must be no more than 35 characters")

      verify(liveController.auditHelper, times(1)).auditApiFailure(any(), any(), any(), any(), any())(any())
    }

    "return 400 Bad Request when the last name is greater than 35 characters" in new Setup {
      val lastNameTooLong: JsObject = Json.obj(
        "firstName"   -> "Mr",
        "lastName"    -> Random.nextString(36),
        "nino"        -> "AA112233B",
        "dateOfBirth" -> "1900-01-01"
      )

      val res: Future[Result] =
        liveController.matchCitizen()(
          fakeRequest.withBody(lastNameTooLong).withHeaders(("CorrelationId", sampleCorrelationId)))

      status(res) mustBe BAD_REQUEST
      contentAsJson(res) mustBe Json
        .obj("code" -> "INVALID_REQUEST", "message" -> "lastName must be no more than 35 characters")

      verify(liveController.auditHelper, times(1)).auditApiFailure(any(), any(), any(), any(), any())(any())
    }

    "return 400 Bad Request when the first name contains invalid characters" in new Setup {
      val invalidFirstName: JsObject = Json.obj(
        "firstName"   -> """/\/\/\/\""",
        "lastName"    -> "Person",
        "nino"        -> "AA112233B",
        "dateOfBirth" -> "1900-01-01"
      )

      val res: Future[Result] = liveController.matchCitizen()(
        fakeRequest.withBody(invalidFirstName).withHeaders(("CorrelationId", sampleCorrelationId)))

      status(res) mustBe BAD_REQUEST
      contentAsJson(res) mustBe Json
        .obj("code" -> "INVALID_REQUEST", "message" -> "firstName contains invalid characters")

      verify(liveController.auditHelper, times(1)).auditApiFailure(any(), any(), any(), any(), any())(any())
    }

    "return 400 Bad Request when the last name contains invalid characters" in new Setup {
      val invalidFirstName: JsObject = Json.obj(
        "firstName"   -> "Mr",
        "lastName"    -> """/\/\/\/\""",
        "nino"        -> "AA112233B",
        "dateOfBirth" -> "1900-01-01"
      )

      val res: Future[Result] = liveController.matchCitizen()(
        fakeRequest.withBody(invalidFirstName).withHeaders(("CorrelationId", sampleCorrelationId)))

      status(res) mustBe BAD_REQUEST
      contentAsJson(res) mustBe Json
        .obj("code" -> "INVALID_REQUEST", "message" -> "lastName contains invalid characters")

      verify(liveController.auditHelper, times(1)).auditApiFailure(any(), any(), any(), any(), any())(any())
    }

    "fail with UnauthorizedException when the bearer token does not have enrolment read:individuals-matching" in new Setup {
      val requestBody: JsValue =
        parse("""{"firstName":"Amanda","lastName":"Joseph","nino":"NA000799C","dateOfBirth":"2020-01-32"}""")

      given(mockAuthConnector.authorise(any(), refEq(Retrievals.allEnrolments))(any(), any()))
        .willReturn(failed(InsufficientEnrolments()))

      val res: Future[Result] = liveController.matchCitizen()(
        fakeRequest.withBody(requestBody).withHeaders(("CorrelationId", sampleCorrelationId)))

      contentAsJson(res) mustBe Json
        .obj("code" -> "UNAUTHORIZED", "message" -> "Insufficient Enrolments")
      verifyNoInteractions(mockLiveCitizenMatchingService)

      verify(liveController.auditHelper, times(1)).auditApiFailure(any(), any(), any(), any(), any())(any())
    }
  }

  private def matchingRequest(
    firstName: String = "Amanda",
    dateOfBirth: String = "1960-01-15",
    nino: String = "NA000799C") =
    s"""{
            "firstName":"$firstName",
            "lastName":"Joseph",
            "nino":"$nino",
            "dateOfBirth":"$dateOfBirth"
          }
        """
}
