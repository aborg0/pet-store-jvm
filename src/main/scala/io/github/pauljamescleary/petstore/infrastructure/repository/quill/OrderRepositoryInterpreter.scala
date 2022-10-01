package io.github.pauljamescleary.petstore.infrastructure.repository.quill

import cats.data.OptionT
import cats.effect.{MonadCancel, MonadCancelThrow}
import cats.syntax.all.*
import io.github.pauljamescleary.petstore.domain
import io.github.pauljamescleary.petstore.domain.orders.{Order, OrderRepositoryAlgebra, OrderStatus}
import io.github.pauljamescleary.petstore.infrastructure.repository.quill.given
import io.getquill.*

import java.util.Date
import java.time.Instant

private object OrderSQL {
  final case class Orders(id: Long, petId: Long, shipDate: Option[Date], status: OrderStatus, complete: Boolean, userId: Option[Long])

  /* We require type StatusMeta to handle our ADT Status */
//  implicit val StatusMeta: Meta[OrderStatus] =
//    Meta[String].imap(OrderStatus.valueOf)(_.toString)

  import ctx.*

  val table: Quoted[EntityQuery[Orders]] = quote {
    query[Orders]
  }

  inline def select(orderId: Long): Quoted[EntityQuery[Orders]] = quote {
    table.filter(_.id == lift(orderId))
  }

//    sql"""
//    SELECT PET_ID, SHIP_DATE, STATUS, COMPLETE, ID, USER_ID
//    FROM ORDERS
//    WHERE ID = $orderId
//  """.query[Order]

  inline def insert(inline order: Order): Quoted[ActionReturning[Orders, Long]] = quote {
    table.insertValue(lift(Orders(order.id.getOrElse(0L), order.petId, order.shipDate.map(Date.from), order.status, order.complete, order.userId)))
      .returningGenerated(_.id)
  }
//    sql"""
//    INSERT INTO ORDERS (PET_ID, SHIP_DATE, STATUS, COMPLETE, USER_ID)
//    VALUES (${order.petId}, ${order.shipDate}, ${order.status}, ${order.complete}, ${order.userId.get})
//  """.update

  def delete(orderId: Long) = quote {
    table.filter(_.id == orderId).delete
  }

//    sql"""
//    DELETE FROM ORDERS
//    WHERE ID = $orderId
//  """.update
}

class OrderRepositoryInterpreter[F[_]: MonadCancelThrow](/*val xa: Transactor[F]*/)
  extends OrderRepositoryAlgebra[F] {
  import OrderSQL.*
  import ctx.*
  import ctx.given

  def create(order: Order): F[Order] =
    ctx.run(insert(order)).pure[F]
      .map(id => order.copy(id = id.some))
//      .transact(xa)

  def get(orderId: Long): F[Option[Order]] =
    ctx.run(OrderSQL.select(orderId)).headOption.pure[F].map(opt => opt.map(o =>
      Order(
        o.petId,
        o.shipDate.map(_.toInstant),
        o.status,
        o.complete,
        o.id.some,
        o.userId
      ))
    ) //.transact(xa)

  def delete(orderId: Long): F[Option[Order]] =
    OptionT(get(orderId))
      .semiflatMap(order => ctx.run(OrderSQL.delete(orderId)).pure[F]/*.transact(xa)*/.as(order))
      .value
}

object OrderRepositoryInterpreter {
  def apply[F[_]: MonadCancelThrow](
//                                     xa: Transactor[F],
                                   ): OrderRepositoryInterpreter[F] =
    new OrderRepositoryInterpreter(/*xa*/)
}
