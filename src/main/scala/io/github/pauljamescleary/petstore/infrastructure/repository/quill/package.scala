package io.github.pauljamescleary.petstore.infrastructure.repository

import io.getquill.{H2JdbcContext, MappedEncoding, SnakeCase}
import io.github.pauljamescleary.petstore.domain.orders.OrderStatus
import tsec.common.SecureRandomId

package object quill {
  private[quill] val ctx = new H2JdbcContext(SnakeCase, "petstore")
}
