package io.github.pauljamescleary.petstore.infrastructure.repository.quill

//import doobie._
//import doobie.implicits._
import io.getquill.*

/**
  * Pagination is a convenience to simply add limits and offsets to any query
  * Part of the motivation for this is using doobie's typechecker, which fails
  * unexpectedly for H2. H2 reports it requires a VARCHAR for limit and offset,
  * which seems wrong.
  */
trait SQLPagination {
  def limit[A/*: Read*/](lim: Int)(q: Quoted[Query[A]]): Quoted[Query[A]] = quote {
    q.take(lim)
  }


  def paginate[A/*: Read*/](lim: Int, offset: Int)(q: Quoted[Query[A]]): Quoted[Query[A]] = quote {
    q.drop(offset).take(lim)
  }
}

object SQLPagination extends SQLPagination
