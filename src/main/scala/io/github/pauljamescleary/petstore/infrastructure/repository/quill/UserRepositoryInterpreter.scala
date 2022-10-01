package io.github.pauljamescleary.petstore.infrastructure.repository.quill

import cats.data.OptionT
import cats.effect.{MonadCancel, MonadCancelThrow}
import cats.syntax.all.*
//import doobie.*
//import doobie.implicits.*
import io.circe.parser.decode
import io.circe.syntax.*
import io.getquill.*
import io.github.pauljamescleary.petstore.domain.users.{Role, User, UserRepositoryAlgebra}
import io.github.pauljamescleary.petstore.infrastructure.repository.quill.SQLPagination.*
import tsec.authentication.IdentityStore

private object UserSQL {
  final case class Users(
                          id: Long,
                          userName: String,
                          firstName: String,
                          lastName: String,
                          email: String,
                          hash: String,
                          phone: String,
                          role: Role,
                        )

  // H2 does not support JSON data type.
  //  implicit val roleMeta: Meta[Role] =
  //    Meta[String].imap(decode[Role](_).leftMap(throw _).merge)(_.asJson.toString)
  given MappedEncoding[Role, String] =
    MappedEncoding(_.asJson.toString)

  given MappedEncoding[String, Role] =
    MappedEncoding(decode[Role](_).leftMap(throw _).merge)

  def usersToUser(users: Users): User = User(
    users.userName,
    users.firstName,
    users.lastName,
    users.email,
    users.hash,
    users.phone,
    users.id.some,
    users.role
  )

  def userToUsers(user: User): Users = Users(
    user.id.getOrElse(0L),
    user.userName,
    user.firstName,
    user.lastName,
    user.email,
    user.hash,
    user.phone,
    user.role
  )

  import ctx.*
  def insert(user: User) = quote {
    query[Users].insertValue(lift(userToUsers(user))).returningGenerated(_.id)
  }
//    sql"""
//    INSERT INTO USERS (USER_NAME, FIRST_NAME, LAST_NAME, EMAIL, HASH, PHONE, ROLE)
//    VALUES (${user.userName}, ${user.firstName}, ${user.lastName}, ${user.email}, ${user.hash}, ${user.phone}, ${user.role})
//  """.update

  def update(user: User, id: Long) = quote {
    query[Users].filter(_.id == lift(id)).updateValue(lift(userToUsers(user)))
  }
//    sql"""
//    UPDATE USERS
//    SET FIRST_NAME = ${user.firstName}, LAST_NAME = ${user.lastName},
//        EMAIL = ${user.email}, HASH = ${user.hash}, PHONE = ${user.phone}, ROLE = ${user.role}
//    WHERE ID = $id
//  """.update

  def select(userId: Long) = quote {
    query[Users].filter(_.id == lift(userId))//.map(usersToUser)
  }
//    sql"""
//    SELECT USER_NAME, FIRST_NAME, LAST_NAME, EMAIL, HASH, PHONE, ID, ROLE
//    FROM USERS
//    WHERE ID = $userId
//  """.query

  def byUserName(userName: String) = quote {
    query[Users].filter(_.userName == lift(userName))//.map(usersToUser(_))
  }
//    sql"""
//    SELECT USER_NAME, FIRST_NAME, LAST_NAME, EMAIL, HASH, PHONE, ID, ROLE
//    FROM USERS
//    WHERE USER_NAME = $userName
//  """.query[User]

  def delete(userId: Long) = quote {
    query[Users].filter(_.id == lift(userId)).delete
  }
//    sql"""
//    DELETE FROM USERS WHERE ID = $userId
//  """.update

  val selectAll = quote {
    query[Users]//.map(usersToUser(_))
  }
//    sql"""
//    SELECT USER_NAME, FIRST_NAME, LAST_NAME, EMAIL, HASH, PHONE, ID, ROLE
//    FROM USERS
//  """.query
}

class UserRepositoryInterpreter[F[_] : MonadCancelThrow](/*val xa: Transactor[F]*/)
  extends UserRepositoryAlgebra[F]
    with IdentityStore[F, Long, User] {
  self =>

  import UserSQL.*
  import UserSQL.given
  import ctx.*

  def create(user: User): F[User] =
    ctx.run(insert(user)).pure[F].map(id => user.copy(id = id.some))//.withUniqueGeneratedKeys[Long]("ID").map(id => user.copy(id = id.some)).transact(xa)

  def update(user: User): OptionT[F, User] =
    OptionT.fromOption[F](user.id).semiflatMap { id =>
      ctx.run(UserSQL.update(user, id)).pure[F]/*.transact(xa)*/.as(user)
    }

  def get(userId: Long): OptionT[F, User] = OptionT(ctx.run(select(userId)).headOption.map(usersToUser(_)).pure[F]/*.transact(xa)*/)

  def findByUserName(userName: String): OptionT[F, User] =
    OptionT(ctx.run(byUserName(userName)).headOption.map(usersToUser(_)).pure[F])

  def delete(userId: Long): OptionT[F, User] =
    get(userId).semiflatMap(user => ctx.run(UserSQL.delete(userId)).pure[F].as(user))

  def deleteByUserName(userName: String): OptionT[F, User] =
    findByUserName(userName).mapFilter(_.id).flatMap(delete)

  def list(pageSize: Int, offset: Int): F[List[User]] =
    ctx.run(paginate(pageSize, offset)(selectAll)).map(usersToUser(_)).to(List).pure[F]//.transact(xa)
}

object UserRepositoryInterpreter {
  def apply[F[_] : MonadCancelThrow](/*xa: Transactor[F]*/): UserRepositoryInterpreter[F] =
    new UserRepositoryInterpreter(/*xa*/)
}
