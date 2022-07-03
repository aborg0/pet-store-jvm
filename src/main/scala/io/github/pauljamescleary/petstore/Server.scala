package io.github.pauljamescleary.petstore

import config.*
import domain.users.*
import domain.orders.*
import domain.pets.*
import infrastructure.endpoint.*
import infrastructure.repository.doobie.{DoobieAuthRepositoryInterpreter, DoobieOrderRepositoryInterpreter, DoobiePetRepositoryInterpreter, DoobieUserRepositoryInterpreter}
import cats.effect.*
import cats.effect.std.Dispatcher
import org.http4s.server.{Router, Server as H4Server}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits.*
import tsec.passwordhashers.jca.BCrypt
import doobie.util.ExecutionContexts
import io.circe.config.parser
import domain.authentication.Auth
import tsec.authentication.SecuredRequestHandler
import tsec.mac.jca.HMACSHA256

object Server extends IOApp {
  type DispatcherF[F[_]] = Dispatcher[F]

  def createServer[F[_]: Async]: Resource[F, H4Server] =
    for {
      conf <- Resource.eval(parser.decodePathF[F, PetStoreConfig]("petstore"))
      serverEc <- ExecutionContexts.cachedThreadPool[F]
      connEc <- ExecutionContexts.fixedThreadPool[F](conf.db.connections.poolSize)
      txnEc <- ExecutionContexts.cachedThreadPool[F]
      xa <- DatabaseConfig.dbTransactor(conf.db, connEc).evalOn(txnEc)
      key <- Resource.eval(HMACSHA256.generateKey[F])
      authRepo = DoobieAuthRepositoryInterpreter[F, HMACSHA256](key, xa)
      petRepo = DoobiePetRepositoryInterpreter[F](xa)
      orderRepo = DoobieOrderRepositoryInterpreter[F](xa)
      userRepo = DoobieUserRepositoryInterpreter[F](xa)
      petValidation = PetValidationInterpreter[F](petRepo)
      petService = PetService[F](petRepo, petValidation)
      userValidation = UserValidationInterpreter[F](userRepo)
      orderService = OrderService[F](orderRepo)
      userService = UserService[F](userRepo, userValidation)
      authenticator = Auth.jwtAuthenticator[F, HMACSHA256](key, authRepo, userRepo)
      routeAuth = SecuredRequestHandler(authenticator)
      httpApp = Router(
        "/users" -> UserEndpoints
          .endpoints[F, BCrypt, HMACSHA256](userService, BCrypt.syncPasswordHasher[F], routeAuth),
        "/pets" -> PetEndpoints.endpoints[F, HMACSHA256](petService, routeAuth),
        "/orders" -> OrderEndpoints.endpoints[F, HMACSHA256](orderService, routeAuth),
      ).orNotFound
      _ <- Resource.eval(DatabaseConfig.initializeDb(conf.db))
      server <- BlazeServerBuilder[F].withExecutionContext(serverEc)
        .bindHttp(conf.server.port, conf.server.host)
        .withHttpApp(httpApp)
        .resource
    } yield server

  def run(args: List[String]): IO[ExitCode] = {
    val server: Resource[IO, H4Server] = createServer
    server.use(_ => IO.never).as(ExitCode.Success)
  }
}
