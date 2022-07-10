package io.github.pauljamescleary.petstore.domain.orders
//import enumeratum._
import org.latestbit.circe.adt.codec.JsonTaggedAdt

enum OrderStatus derives JsonTaggedAdt.PureEncoder, JsonTaggedAdt.PureDecoder:
  case Approved, Delivered, Placed
