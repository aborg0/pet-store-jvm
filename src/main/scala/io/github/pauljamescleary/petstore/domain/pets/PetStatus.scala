package io.github.pauljamescleary.petstore.domain.pets
//import enumeratum._
import org.latestbit.circe.adt.codec.JsonTaggedAdt

enum PetStatus derives JsonTaggedAdt.PureEncoder, JsonTaggedAdt.PureDecoder {
  case Available, Pending, Adopted
}
