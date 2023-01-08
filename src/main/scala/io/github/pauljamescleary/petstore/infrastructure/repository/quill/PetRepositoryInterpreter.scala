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
    tags: String = "[]",
    photoUrls: String = "[]",
  )

  given MappedEncoding[PetStatus, String] =
    MappedEncoding(_.toString)

  given MappedEncoding[String, PetStatus] =
    MappedEncoding(PetStatus.valueOf)

  import io.circe.*
  import io.circe.parser.decode
  import io.circe.syntax.*

  import ctx.*
  inline def table = quote {
    querySchema[PetDb]("pet")
  }

  /* We require type StatusMeta to handle our ADT Status */
//  implicit val StatusMeta: Meta[PetStatus] =
//    Meta[String].imap(PetStatus.valueOf)(_.toString)

  /* This is used to marshal our sets of strings */
//  implicit val SetStringMeta: Meta[Set[String]] =
//    Meta[String].imap(_.split(',').toSet)(_.mkString(","))

//  inline def insert(pet: PetDb) = quote {
//    table.insertValue(lift(pet)).returning(_.id)
//  }

  def petToPetDb(pet: Pet): PetDb = {
    PetDb(0L, pet.name, pet.category, pet.bio, pet.status, pet.tags.asJson.toString, pet.photoUrls.asJson.toString)
  }
  def dbToPet(p: PetDb):Pet =
      Pet(
        p.name,
        p.category,
        p.bio,
        p.status,
        decode[Set[String]](p.tags).leftMap(throw _).merge,
        decode[Set[String]](p.photoUrls).leftMap(throw _).merge,
        p.id.some
      )

  def insert(pet: Pet): Quoted[ActionReturning[PetDb, Long]] = quote {
    table.insertValue(lift(petToPetDb(pet))).returningGenerated(_.id)
  }
//    sql"""
//    INSERT INTO PET (NAME, CATEGORY, BIO, STATUS, TAGS, PHOTO_URLS)
//    VALUES (${pet.name}, ${pet.category}, ${pet.bio}, ${pet.status}, ${pet.tags}, ${pet.photoUrls})
//  """.update

  def update(pet: Pet, id: Long) = quote {
    table.filter(_.id == id).updateValue(lift(petToPetDb(pet)))
  }
//    sql"""
//    UPDATE PET
//    SET NAME = ${pet.name}, BIO = ${pet.bio}, STATUS = ${pet.status}, TAGS = ${pet.tags}, PHOTO_URLS = ${pet.photoUrls}
//    WHERE id = $id
//  """.update

  def select(id: Long) = quote {
    table.filter(_.id == lift(id))//.map{ dbToPet }
  }
//    sql"""
//    SELECT NAME, CATEGORY, BIO, STATUS, TAGS, PHOTO_URLS, ID
//    FROM PET
//    WHERE ID = $id
//  """.query

  def delete(id: Long) = quote {
    table.filter(_.id == lift(id)).delete
  }
//    sql"""
//    DELETE FROM PET WHERE ID = $id
//  """.update

  def selectByNameAndCategory(name: String, category: String) = quote {
    table.filter(p => p.name == lift(name) && p.category == lift(category))//.map(dbToPet)
  }
//    sql"""
//    SELECT NAME, CATEGORY, BIO, STATUS, TAGS, PHOTO_URLS, ID
//    FROM PET
//    WHERE NAME = $name AND CATEGORY = $category
//  """.query[Pet]

  def selectAll = quote {
    table.sortBy(_.name)//.map(dbToPet)
  }
//    sql"""
//    SELECT NAME, CATEGORY, BIO, STATUS, TAGS, PHOTO_URLS, ID
//    FROM PET
//    ORDER BY NAME
//  """.query

//  import ctx.extras.*
  def selectByStatus(statuses: NonEmptyList[PetStatus]) = quote {
    table.filter(p => liftQuery(statuses.toList).contains(p.status))//.map(dbToPet)
  }
//    (
//      sql"""
//      SELECT NAME, CATEGORY, BIO, STATUS, TAGS, PHOTO_URLS, ID
//      FROM PET
//      WHERE """ ++ Fragments.in(fr"STATUS", statuses)
//    ).query

  def selectTagLikeString(tags: NonEmptyList[String]) = {
//    val likes = tags.map(t => s"%$t%")
    val firstTag = tags.map(t => s"%$t%").head
    val tagsLike = tags.map(t => s"%$t%").toList
    quote {
//      table.filter(p => lazyLift(likes).exists(t => p.tags.like(t)))
//      table.filter(p => liftQuery(tags.map(t => s"%$t%").toList).exists(t => p.tags.exists(_.like(t)))).map(dbToPet)
      table.filter(p => {
//        p.tags.exists(_.like(lift(firstTag)))
//        tagsLike.exists(t => p.tags.like(t))
        p.tags.like(firstTag)
      })//.map(dbToPet)
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

  def create(pet: Pet): F[Pet] = {
//    val petDb = petToPetDb(pet)
//    val value = ctx.run(insert(petDb))
//    value.pure[F].map(id => pet.copy(id = id.some))
    ctx.run(insert(pet)).pure[F].map(id => pet.copy(id = id.some))
  }

  def update(pet: Pet): F[Option[Pet]] =
    OptionT.fromOption(pet.id).semiflatMap(id => ctx.run(PetSQL.update(pet, id)).pure[F].as(pet))
      .value

  def get(id: Long): F[Option[Pet]] = ctx.run(select(id)).headOption.map(dbToPet(_)).pure[F]//.transact(xa)

  def delete(id: Long): F[Option[Pet]] =
    OptionT(ctx.run(select(id)).headOption.pure[F]).semiflatMap(pet => ctx.run(PetSQL.delete(id)).pure[F].as(dbToPet(pet))).value//.transact(xa)

  def findByNameAndCategory(name: String, category: String): F[Set[Pet]] =
//    selectByNameAndCategory(name, category).to[List].transact(xa).map(_.toSet)
    ctx.run(selectByNameAndCategory(name, category)).map(dbToPet(_)).to(Set).pure[F]

  def list(pageSize: Int, offset: Int): F[List[Pet]] =
    ctx.run(paginate(pageSize, offset)(selectAll)).to(List).map(dbToPet(_)).to(List).pure[F]//.transact(xa)

  def findByStatus(statuses: NonEmptyList[PetStatus]): F[List[Pet]] =
    ctx.run(selectByStatus(statuses)).map(dbToPet(_)).to(List).pure[F]//.transact(xa)

  def findByTag(tags: NonEmptyList[String]): F[List[Pet]] =
    ctx.run(selectTagLikeString(tags)).map(dbToPet(_)).to(List).pure[F] //.transact(xa)
}

object PetRepositoryInterpreter {
  def apply[F[_]: MonadCancelThrow](/*xa: Transactor[F]*/): PetRepositoryInterpreter[F] =
    new PetRepositoryInterpreter(/*xa*/)
}
