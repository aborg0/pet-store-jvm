package io.github.pauljamescleary.petstore.infrastructure.repository

import io.getquill.{H2JdbcContext, MappedEncoding, SnakeCase}
import io.github.pauljamescleary.petstore.domain.orders.OrderStatus
import tsec.common.SecureRandomId

package object quill {
  private[quill] val ctx = new H2JdbcContext(SnakeCase, "petstore")

  given MappedEncoding[SecureRandomId, String] =
    MappedEncoding(e => e.toString)

  given MappedEncoding[String, SecureRandomId] =
    MappedEncoding(SecureRandomId.apply)

  given MappedEncoding[OrderStatus, String] =
    MappedEncoding(_.toString)

  given MappedEncoding[String, OrderStatus] =
    MappedEncoding(OrderStatus.valueOf)
}
