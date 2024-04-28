package controllers

import javax.inject.Inject
import play.api.mvc._
import services.AuthService
import scala.concurrent.{ ExecutionContext, Future }
import play.api.data._
import play.api.data.Forms._
import models.User
import play.api.i18n.{ I18nSupport, MessagesApi, MessagesProvider }

class LoginController @Inject()(cc: ControllerComponents, authService: AuthService,messagesApi: MessagesApi)(implicit ec: ExecutionContext) extends AbstractController(cc) with I18nSupport {

  // Define form for user
  val userForm: Form[User] = Form(
    mapping(
      "email" -> nonEmptyText,
      "password" -> nonEmptyText
    )(User.apply)(User.unapply)
  )

  // Display the user login form
  def login(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.login(userForm))
  }

  // Handle form submission for authentication
  def authenticate(): Action[AnyContent] = Action.async { implicit request =>
    userForm.bindFromRequest.fold(
      formWithErrors => {
        play.Logger.info(s"Binding error! Please check your form mapping")
        // Form data is invalid
//        Future.successful(Redirect(routes.LoginController.login()).flashing("error" -> "Invalid form data"))
        Future.successful(BadRequest(views.html.login(userForm.withGlobalError("Can't process your request, please try again later!"))))
      },
      user => {
        authService.authenticate(user.email, user.password).map { authenticatedUserOption =>
          authenticatedUserOption.map { authenticatedUser =>
           if(authenticatedUser.email=="admin@gmail.com"){
             // Log successful login
             play.Logger.info(s"Admin logged in: ${authenticatedUser.email}")
             // Successful login, set the user session and redirect to the home page
             Redirect(routes.AdminDashboardController.showProducts()).withSession("email" -> authenticatedUser.email)
           }
           else{
             // Log successful login
             play.Logger.info(s"Customer logged in: ${authenticatedUser.email}")
             // Get the requested page from the session, defaulting to ClientController.showHome() if not present
             val requestedPage = request.session.get("requestedPage").getOrElse(routes.ClientController.showHome().url)
             // Successful login, set the user session and redirect to the requested page
             Redirect(requestedPage).withSession("email" -> authenticatedUser.email).removingFromSession("requestedPage")
           }
          }.getOrElse {
            // Log authentication failure
            play.Logger.warn(s"Failed login attempt for user: ${user.email}")
            // Invalid credentials
//            Redirect(routes.LoginController.login()).flashing("error" -> "Invalid email or password")
            BadRequest(views.html.login(userForm.withGlobalError("Invalid email-id or password")))
          }
        }.recover {
          case exception: Exception =>
            // Handle other exceptions and log them
            play.Logger.error(s"An error occurred during authentication: ${exception.getMessage}")
//            Redirect(routes.LoginController.login()).flashing("error" -> "An error occurred during authentication")
            BadRequest(views.html.login(userForm.withGlobalError("An error occurred during authentication")))
        }
      }
    )
  }

  // Logout: Clear the user session
  def logout(): Action[AnyContent] = Action {
    Redirect(routes.LoginController.login()).withNewSession
  }
}