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

package uk.gov.hmrc.individualsmatchingapi.controllers

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json.toJson
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.individualsmatchingapi.domain.JsonFormatters.matchedCitizenRecordJsonFormat
import uk.gov.hmrc.individualsmatchingapi.services.LiveCitizenMatchingService

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class MatchedCitizenController @Inject()(cc: ControllerComponents, citizenMatchingService: LiveCitizenMatchingService)
    extends CommonController(cc) {

  def matchedCitizen(matchId: String): Action[AnyContent] = Action.async { implicit request =>
    withUuid(matchId) { matchUuid =>
      citizenMatchingService.fetchMatchedCitizenRecord(matchUuid) map { matchedCitizen =>
        Ok(toJson(matchedCitizen))
      }
    } recover recovery
  }
}
