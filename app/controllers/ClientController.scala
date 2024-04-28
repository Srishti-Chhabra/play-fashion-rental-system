package controllers

import javax.inject._
import play.api.mvc._
import play.api.i18n._
import dao.{CartDAO, CustomerDAO, OrderDAO, ProductDAO}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClientController @Inject()(cc: ControllerComponents, productDAO: ProductDAO, orderDAO: OrderDAO, customerDAO: CustomerDAO, cartDAO: CartDAO, messagesApi: MessagesApi)
                                (implicit ec: ExecutionContext) extends AbstractController(cc) with I18nSupport {
  // Product

  // Render the client home page
  def showHome(): Action[AnyContent] = Action { implicit request =>
    val userOption = request.session.get("email")
    userOption.fold {
      Ok(views.html.clientHome(userOption))
    } { userEmail =>
      if (userEmail == "admin@gmail.com") {
        Redirect(routes.ClientController.showHome()).withNewSession
      } else {
        Ok(views.html.clientHome(userOption))
      }
    }
  }

  // Render the products page
  def showProducts(): Action[AnyContent] = Action.async { implicit request =>
    val userOption = request.session.get("email")
    userOption.fold {
      productDAO.all().map { products =>
        Ok(views.html.clientProducts(products, userOption))
      }
    } { userEmail =>
      if (userEmail == "admin@gmail.com") {
        Future.successful(Redirect(routes.ClientController.showProducts()).withNewSession)
      } else {
        productDAO.all().map { products =>
          Ok(views.html.clientProducts(products, userOption))
        }
      }
    }
  }

  def productDetails(productId: String, size: String, color: String): Action[AnyContent] = Action.async { implicit request =>

    val userOption = request.session.get("email")

    def fetchProductDetails = productDAO.findByIdSizeColor(productId, size, color).flatMap {
      case Some(product) =>
        for {
          colors <- productDAO.getColorsById(productId)
          sizes <- productDAO.getSizesByIdColor(productId, color)
        } yield Ok(views.html.clientProductDetails(product, colors, sizes, None, 0, userOption))
      case None =>
        Future.successful(NotFound("Product not found"))
    }

    def fetchProductDetailsWithCartDetails(userEmail: String) = productDAO.findByIdSizeColor(productId, size, color).flatMap {
      case Some(product) =>
        for {
          colors <- productDAO.getColorsById(productId)
          sizes <- productDAO.getSizesByIdColor(productId, color)
          cartItems <- cartDAO.all(userEmail)
        } yield {
          val quantityInCart: Int = Some(cartItems).flatMap { items =>
            items.find { cartItem =>
              cartItem.productId == product.productId && cartItem.size == product.size && cartItem.color == product.color
            }.map(_.quantity)
          }.headOption.getOrElse(0)
          Ok(views.html.clientProductDetails(product, colors, sizes, Some(cartItems), quantityInCart, userOption))
        }
      case None =>
        Future.successful(NotFound("Product not found"))
    }

    userOption.fold {
      fetchProductDetails
    } { userEmail =>
      if (userEmail == "admin@gmail.com") {
        Future.successful(Redirect(routes.ClientController.productDetails(productId, size, color)).withNewSession)
      } else {
        fetchProductDetailsWithCartDetails(userEmail)
      }
    }
  }

}
