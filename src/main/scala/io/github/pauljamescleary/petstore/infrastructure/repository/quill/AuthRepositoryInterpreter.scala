package io.github.pauljamescleary.petstore.infrastructure.repository.quill

import java.time.Instant
import cats.*
import cats.data.*
import cats.effect.{MonadCancel, MonadCancelThrow}
import cats.syntax.all.*
import io.circe.{Decoder, Encoder, given}
import io.getquill.*
import tsec.authentication.{AugmentedJWT, BackingStore}
import tsec.common.SecureRandomId
import tsec.jws.JWSSerializer
import tsec.jws.mac.{JWSMacCV, JWSMacHeader, JWTMacImpure}
import tsec.mac.jca.{MacErrorM, MacSigningKey}

import java.util.Date

private object AuthSQL {
//  given Encoder[SecureRandomId] =
//    Encoder[String].contramap((_: Id[SecureRandomId]).widen)
//
//  given Decoder[SecureRandomId] =
//    Decoder[String].map(SecureRandomId.apply)

  final case class Jwt(id: SecureRandomId, jwt: String, identity: Long, expiry: Date, lastTouched: Option[Date])

  //  implicit val secureRandomIdPut: Put[SecureRandomId] =
  //    Put[String].contramap((_: Id[SecureRandomId]).widen)

  import ctx.*

  given MappedEncoding[SecureRandomId, String] =
    MappedEncoding(e => e.toString)

  given MappedEncoding[String, SecureRandomId] =
    MappedEncoding(SecureRandomId.apply)

  def insert[A](jwt: AugmentedJWT[A, Long])(implicit hs: JWSSerializer[JWSMacHeader[A]]): Quoted[Insert[Jwt]] = quote {
    query[Jwt].insertValue(lift(Jwt(jwt.id, jwt.jwt.toEncodedString, jwt.identity, Date.from(jwt.expiry), jwt.lastTouched.map(Date.from))))
  }

  def update[A](jwt: AugmentedJWT[A, Long])(implicit hs: JWSSerializer[JWSMacHeader[A]]): Quoted[Update[Jwt]] = quote {
    query[Jwt].filter(_.id == jwt.id).update(
      _.jwt -> lift(jwt.jwt.toEncodedString),
      _.identity -> lift(jwt.identity),
      _.expiry -> lift(Date.from(jwt.expiry)),
      _.lastTouched -> lift(jwt.lastTouched.map(Date.from))
    )
  }

  def delete(id: SecureRandomId): Quoted[Delete[Jwt]] =
    quote {
      query[Jwt].filter(_.id == lift(id)).delete//.returning(identity)
    }
  def select(id: SecureRandomId): Quoted[EntityQuery[Jwt]] = quote {
    query[Jwt].filter(_.id == lift(id))
  }
}

class AuthRepositoryInterpreter[F[_] : MonadCancelThrow, A](
                                                            val key: MacSigningKey[A],
                                                            //                                                                  val xa: Transactor[F]
                                                          )(implicit
                                                            hs: JWSSerializer[JWSMacHeader[A]],
                                                            s: JWSMacCV[MacErrorM, A]
                                                          ) extends BackingStore[F, SecureRandomId, AugmentedJWT[A, Long]] {
  import AuthSQL.*
  import AuthSQL.given
  override def put(jwt: AugmentedJWT[A, Long]): F[AugmentedJWT[A, Long]] =
    ctx.run(AuthSQL.insert(jwt)).pure[F].as(jwt)

  override def update(jwt: AugmentedJWT[A, Long]): F[AugmentedJWT[A, Long]] =
    ctx.run(AuthSQL.update(jwt)).pure[F].as(jwt)

  override def delete(id: SecureRandomId): F[Unit] =
    ctx.run(AuthSQL.delete(id)).pure[F].void

  override def get(id: SecureRandomId): OptionT[F, AugmentedJWT[A, Long]] =
    OptionT[F, AugmentedJWT[A, Long]](None.pure[F])
//    OptionT(ctx.run(select(id)).headOption.pure[F]).semiflatMap {
//      case Jwt(_, jwtStringify, identity, expiry, lastTouched) =>
//        JWTMacImpure.verifyAndParse(jwtStringify, key) match {
//          case Left(err) => err.raiseError[F, AugmentedJWT[A, Long]]
//          case Right(jwt) => AugmentedJWT(id, jwt, identity, expiry.toInstant, lastTouched.map(_.toInstant)).pure[F]
//        }
//    }
}

object AuthRepositoryInterpreter {
  def apply[F[_] : MonadCancelThrow, A](key: MacSigningKey[A],
                                        //                                       xa: Transactor[F]
                                       )(implicit
                                         hs: JWSSerializer[JWSMacHeader[A]],
                                         s: JWSMacCV[MacErrorM, A]
                                       ): AuthRepositoryInterpreter[F, A] =
    new AuthRepositoryInterpreter(key/*, xa*/)
}
