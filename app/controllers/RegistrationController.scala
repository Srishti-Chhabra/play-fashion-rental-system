package controllers

import javax.inject.Inject
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import scala.concurrent.{ExecutionContext, Future}
import services.AuthService
import models.User
import play.api.i18n.{ I18nSupport, MessagesApi, MessagesProvider }

class RegistrationController @Inject()(cc: ControllerComponents, authService: AuthService, messagesApi: MessagesApi)(implicit ec: ExecutionContext) extends AbstractController(cc) with I18nSupport {

  // Form definition
  val userForm: Form[User] = Form(
    mapping(
      "email" -> nonEmptyText.verifying("Invalid email format", email => isValidEmail(email)),
      "password" -> nonEmptyText.verifying("Password must be at least 8 characters long, contain at least one lowercase letter, one uppercase letter, one digit, and one special character",
        password => isValidPassword(password))
    )(User.apply)(User.unapply)
  )

  // Regular expressions for email and password validation
  private val emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$".r
  private val passwordRegex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%?&])[A-Za-z\\d@$!%?&]{8,}$".r

  private def isValidEmail(email: String): Boolean = {
    emailRegex.pattern.matcher(email).matches
  }

  private def isValidPassword(password: String): Boolean = {
    passwordRegex.pattern.matcher(password).matches
  }

  // Action to show registration form
  def showRegistrationForm: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.register(userForm))
  }

  // Action to handle registration form submission
  def register: Action[AnyContent] = Action.async { implicit request =>
    userForm.bindFromRequest().fold(
      formWithErrors => {
        play.Logger.info(s"Binding error! Please check your form mapping")
        Future.successful(BadRequest(views.html.register(formWithErrors.withGlobalError("Can't process your request, please try again later!"))))
      },
      user => {
        authService.register(user).map {
          case Some(registeredUser) =>
            if(registeredUser.email=="admin@gmail.com"){
              play.Logger.info(s"Admin registered: ${registeredUser.email}")
              Redirect(routes.AdminDashboardController.showProducts()).withSession("email" -> registeredUser.email)
            }
            else {
              play.Logger.info(s"Customer registered: ${registeredUser.email}")
              Redirect(routes.ClientController.showHome()).withSession("email" -> registeredUser.email)
            }
          case None =>
            // Log registration failure
            play.Logger.warn(s"Failed registration attempt for user: ${user.email}")
            BadRequest(views.html.register(userForm.withGlobalError("Oops! User already exists")))
        }
      }
    )
  }
}
