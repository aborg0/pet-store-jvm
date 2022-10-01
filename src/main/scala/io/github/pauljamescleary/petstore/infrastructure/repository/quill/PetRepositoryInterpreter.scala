package io.github.pauljamescleary.petstore.infrastructure.repository.quill

import cats.data.*
import cats.syntax.all.*
import cats.effect.MonadCancelThrow
import io.getquill.*
import io.github.pauljamescleary.petstore.domain.pets.{Pet, PetRepositoryAlgebra, PetStatus}
import io.github.pauljamescleary.petstore.infrastructure.repository.quill.SQLPagination.*

private object PetSQL {
  final case class PetDb(
    id: Long,
    name: String,
    category: String,
    bio: String,
    status: PetStatus = PetStatus.Available,
    tags: Set[String] = Set.empty,
    photoUrls: Set[String] = Set.empty,
  )

  given MappedEncoding[PetStatus, String] =
    MappedEncoding(_.toString)

  given MappedEncoding[String, PetStatus] =
    MappedEncoding(PetStatus.valueOf)

  import io.circe.*
  import io.circe.parser.decode
  import io.circe.syntax.*
  given MappedEncoding[Set[String], String] =
    MappedEncoding(_.asJson.toString)
  given MappedEncoding[List[String], String] =
    MappedEncoding(_.asJson.toString)

  given MappedEncoding[String, Set[String]] =
    MappedEncoding(decode[Set[String]](_).leftMap(throw _).merge)
  given MappedEncoding[String, List[String]] =
    MappedEncoding(decode[List[String]](_).leftMap(throw _).merge)
  given MappedEncoding[String, NonEmptyList[String]] =
    MappedEncoding(decode[NonEmptyList[String]](_).leftMap(throw _).merge)

  import ctx.*
  val table: Quoted[EntityQuery[PetDb]] = quote {
    querySchema[PetDb]("pet")
  }

  /* We require type StatusMeta to handle our ADT Status */
//  implicit val StatusMeta: Meta[PetStatus] =
//    Meta[String].imap(PetStatus.valueOf)(_.toString)

  /* This is used to marshal our sets of strings */
//  implicit val SetStringMeta: Meta[Set[String]] =
//    Meta[String].imap(_.split(',').toSet)(_.mkString(","))

  def insert(pet: Pet): Quoted[ActionReturning[PetDb, Long]] = quote {
    table.insertValue(lift(PetDb(0L, pet.name, pet.category, pet.bio, pet.status, pet.tags, pet.photoUrls))).returning(_.id)
  }
//    sql"""
//    INSERT INTO PET (NAME, CATEGORY, BIO, STATUS, TAGS, PHOTO_URLS)
//    VALUES (${pet.name}, ${pet.category}, ${pet.bio}, ${pet.status}, ${pet.tags}, ${pet.photoUrls})
//  """.update

  def update(pet: Pet, id: Long): Quoted[Update[PetDb]] = quote {
    table.updateValue(lift(PetDb(id, pet.name, pet.category, pet.bio, pet.status, pet.tags, pet.photoUrls)))
  }
//    sql"""
//    UPDATE PET
//    SET NAME = ${pet.name}, BIO = ${pet.bio}, STATUS = ${pet.status}, TAGS = ${pet.tags}, PHOTO_URLS = ${pet.photoUrls}
//    WHERE id = $id
//  """.update

  def select(id: Long): Quoted[EntityQuery[Pet]] = quote {
    table.filter(_.id == lift(id)).map{ dbToPet }
  }
//    sql"""
//    SELECT NAME, CATEGORY, BIO, STATUS, TAGS, PHOTO_URLS, ID
//    FROM PET
//    WHERE ID = $id
//  """.query

  private def dbToPet(p: PetDb):Pet =
      Pet(
        p.name,
        p.category,
        p.bio,
        p.status,
        p.tags,
        p.photoUrls,
        p.id.some
      )

  def delete(id: Long): Quoted[Delete[PetDb]] = quote {
    table.filter(_.id == lift(id)).delete
  }
//    sql"""
//    DELETE FROM PET WHERE ID = $id
//  """.update

  def selectByNameAndCategory(name: String, category: String): Quoted[EntityQuery[Pet]] = quote {
    table.filter(p => p.name == lift(name) && p.category == lift(category)).map(dbToPet)
  }
//    sql"""
//    SELECT NAME, CATEGORY, BIO, STATUS, TAGS, PHOTO_URLS, ID
//    FROM PET
//    WHERE NAME = $name AND CATEGORY = $category
//  """.query[Pet]

  def selectAll: Quoted[Query[Pet]] = quote {
    table.sortBy(_.name).map(dbToPet)
  }
//    sql"""
//    SELECT NAME, CATEGORY, BIO, STATUS, TAGS, PHOTO_URLS, ID
//    FROM PET
//    ORDER BY NAME
//  """.query

//  import ctx.extras.*
  def selectByStatus(statuses: NonEmptyList[PetStatus]): Quoted[Query[Pet]] = quote {
    table.filter(p => lift(statuses).exists(_ == p.status)).map(dbToPet)
  }
//    (
//      sql"""
//      SELECT NAME, CATEGORY, BIO, STATUS, TAGS, PHOTO_URLS, ID
//      FROM PET
//      WHERE """ ++ Fragments.in(fr"STATUS", statuses)
//    ).query

  def selectTagLikeString(tags: NonEmptyList[String]): Quoted[Query[Pet]] = {
//    val likes = tags.map(t => s"%$t%")
    quote {
//      table.filter(p => lazyLift(likes).exists(t => p.tags.like(t)))
      table.filter(p => lift(tags.map(t => s"%$t%")).exists(t => p.tags.exists(_.like(t)))).map(dbToPet)
    }
  }
//    /* Handle dynamic construction of query based on multiple parameters */
//
//    /* To piggyback off of comment of above reference about tags implementation, findByTag uses LIKE for partial matching
//    since tags is (currently) implemented as a comma-delimited string */
//    val tagLikeString: String = tags.toList.mkString("TAGS LIKE '%", "%' OR TAGS LIKE '%", "%'")
//    (sql"""SELECT NAME, CATEGORY, BIO, STATUS, TAGS, PHOTO_URLS, ID
//         FROM PET
//         WHERE """ ++ Fragment.const(tagLikeString))
//      .query[Pet]
//  }
}

class PetRepositoryInterpreter[F[_]: MonadCancelThrow](/*val xa: Transactor[F]*/)
    extends PetRepositoryAlgebra[F] {
  import PetSQL.*
  import PetSQL.given
  import ctx.*

  def create(pet: Pet): F[Pet] =
    ctx.run(insert(pet)).pure[F].map(id => pet.copy(id = id.some))

  def update(pet: Pet): F[Option[Pet]] =
    OptionT.fromOption(pet.id).semiflatMap(id => ctx.run(PetSQL.update(pet, id)).pure[F].as(pet))
      .value
//    OptionT
//      .fromOption[ConnectionIO](pet.id)
//      .semiflatMap(id => PetSQL.update(pet, id).run.as(pet))
//      .value
//      .transact(xa)

  def get(id: Long): F[Option[Pet]] = ctx.run(select(id)).headOption.pure[F]//.transact(xa)

  def delete(id: Long): F[Option[Pet]] =
    OptionT(ctx.run(select(id)).headOption.pure[F]).semiflatMap(pet => ctx.run(PetSQL.delete(id)).pure[F].as(pet)).value//.transact(xa)

  def findByNameAndCategory(name: String, category: String): F[Set[Pet]] =
//    selectByNameAndCategory(name, category).to[List].transact(xa).map(_.toSet)
    ctx.run(selectByNameAndCategory(name, category)).to(Set).pure[F]

  def list(pageSize: Int, offset: Int): F[List[Pet]] =
    ctx.run(paginate(pageSize, offset)(selectAll)).to(List).pure[F]//.transact(xa)

  def findByStatus(statuses: NonEmptyList[PetStatus]): F[List[Pet]] =
    ctx.run(selectByStatus(statuses)).to(List).pure[F]//.transact(xa)

  def findByTag(tags: NonEmptyList[String]): F[List[Pet]] =
    ctx.run(selectTagLikeString(tags)).to(List).pure[F] //.transact(xa)
}

object PetRepositoryInterpreter {
  def apply[F[_]: MonadCancelThrow](/*xa: Transactor[F]*/): PetRepositoryInterpreter[F] =
    new PetRepositoryInterpreter(/*xa*/)
}
