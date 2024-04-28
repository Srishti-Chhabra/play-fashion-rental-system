package services

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import models.User
import dao.UserDAO

class AuthService @Inject()(userDao: UserDAO)(implicit ec: ExecutionContext) {

  // Authenticate a user based on the provided email and password
  def authenticate(email: String, password: String): Future[Option[User]] = {
    userDao.all().map(users => users.find(user => user.email == email && user.password == password))
  }

  // Register a new user
  def register(user: User): Future[Option[User]] = {
    userDao.all().flatMap { users =>
      if (users.exists(existingUser => existingUser.email == user.email)) {
        // User with the same email already exists
        Future.successful(None)
      } else {
        // Add the new user to the database
        userDao.insert(user).map(_ => Some(user))
      }
    }
  }
}