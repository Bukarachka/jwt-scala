package pdi.jwt

import org.scalatest._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._

import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.json._

class JwtSessionSpec extends PlaySpec with OneAppPerSuite with PlayFixture {
  val HEADER_NAME = "Authorization"

  implicit override lazy val app: FakeApplication =
    FakeApplication(
      additionalConfiguration = Map("application.secret" -> secretKey)
    )

  val session = JwtSession().withHeader(JwtHeader(JwtAlgorithm.HmacSHA256))
  val session2 = session ++ (("a", 1), ("b", "c"), ("e", true), ("f", Seq(1, 2, 3)), ("user", user))
  val session3 = JwtSession(JwtHeader(JwtAlgorithm.HmacSHA256), claimClass, "-3BM6yrNy3a8E2QtEYszKes2Rij80sfpgBAmzrJeJuk")
  // This is session3 serialized (if no bug...)
  val token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIbWFjU0hBMjU2In0." + claim64 + ".-3BM6yrNy3a8E2QtEYszKes2Rij80sfpgBAmzrJeJuk"

  "JwtSession" must {
    "read default configuration" in {
      assert(JwtSession.defaultHeader == JwtHeader(JwtAlgorithm.HmacSHA256))
    }

    "init" in {
      assert(session.headerData == Json.obj("typ" -> "JWT", "alg" -> "HmacSHA256"))
      assert(session.claimData == Json.obj())
      assert(session.signature == "")
      assert(session.isEmpty)
    }

    "add stuff" in {
      assert((session + Json.obj("a" -> 1)).claimData == Json.obj("a" -> 1))
      assert((session + ("a", 1) + ("b", "c")).claimData == Json.obj("a" -> 1, "b" -> "c"))
      assert((session + ("user", user)).claimData == Json.obj("user" -> userJson))
      assert((session ++ (("a", 1), ("b", "c"))).claimData == Json.obj("a" -> 1, "b" -> "c"))
    }

    "remove stuff" in {
      assert((session2 - "e" - "f" - "user").claimData == Json.obj("a" -> 1, "b" -> "c"))
      assert((session2 -- ("e", "f", "user")).claimData == Json.obj("a" -> 1, "b" -> "c"))
    }

    "get stuff" in {
      assert(session2("a") == JsNumber(1))
      assert(session2("b") == JsString("c"))
      assert(session2("e") == JsBoolean(true))
      assert(session2("f") == Json.arr(1, 2, 3))
      assert(session2("nope") match { case _: JsUndefined => true; case _ => false })
      assert(session2.get("a") == JsNumber(1))
      assert(session2.get("b") == JsString("c"))
      assert(session2.get("e") == JsBoolean(true))
      assert(session2.get("f") == Json.arr(1, 2, 3))
      assert(session2.get("nope") match { case _: JsUndefined => true; case _ => false })
      assert(session2.getAs[User]("user") == Option(user))
      assert(session2.getAs[User]("nope") == None)
    }

    "test emptiness" in {
      assert(session.isEmpty)
      assert(!session2.isEmpty)
    }

    "serialize" in {
      assert(session3.serialize == token)
    }

    "deserialize" in {
      val mock = mockValidTime
      assert(JwtSession.deserialize(token) == session3)
      mock.tearDown
    }
  }

  val sessionHeader = Some("Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIbWFjU0hBMjU2In0.eyJ1c2VyIjp7ImlkIjoxLCJuYW1lIjoiUGF1bCJ9fQ.McCC-wVflYAnnk6yRoojeNszDfayCsK9C6NVoMFMq24")

  "RichResult" must {
    "access app with no user" in {
      val result = get(classicAction)
      val result2 = get(securedAction)

      status(result) mustEqual OK
      status(result2) mustEqual UNAUTHORIZED
      jwtHeader(result) mustEqual None
      jwtHeader(result2) mustEqual None
    }

    "fail to login" in {
      val result = post(loginAction, Json.obj("username" -> "whatever", "password" -> "wrong"))
      status(result) mustEqual BAD_REQUEST
      jwtHeader(result) mustEqual None
    }

    "login" in {
      val result = post(loginAction, Json.obj("username" -> "whatever", "password" -> "p4ssw0rd"))
      status(result) mustEqual OK
      jwtHeader(result) mustEqual sessionHeader
    }

    "access app with user" in {
      val result = get(classicAction, sessionHeader)
      val result2 = get(securedAction, sessionHeader)

      status(result) mustEqual OK
      status(result2) mustEqual OK
      // Wuuut? Why None? Because since there is no "session.maxAge", we don't need to refresh the token
      // it's up to the client-side code to save it as long as it needs it
      jwtHeader(result) mustEqual None
      jwtHeader(result2) mustEqual None
    }

    "logout" in {
      val result = get(logoutAction)
      status(result) mustEqual OK
      jwtHeader(result) mustEqual None
    }

    "access app with no user again" in {
      val result = get(classicAction)
      val result2 = get(securedAction)

      status(result) mustEqual OK
      status(result2) mustEqual UNAUTHORIZED
      jwtHeader(result) mustEqual None
      jwtHeader(result2) mustEqual None
    }
  }
}
