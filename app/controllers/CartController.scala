package controllers

import play.api.mvc._
import play.api.mvc.ControllerComponents
import dao.{CartDAO, CustomerDAO, OrderDAO, ProductDAO}
import models.{CartItem, Order, OrderItems, Product}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CartController @Inject()(cc: ControllerComponents, productDAO: ProductDAO,customerDAO: CustomerDAO, orderDAO: OrderDAO, cartDAO: CartDAO)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  def addToCart(productId: String, size: String, color: String): Action[AnyContent] = Action.async { implicit request =>
    val userOption = request.session.get("email")
    userOption.map { userEmail =>
      cartDAO.insert(userEmail, productId, size, color).map { _ =>
        Redirect(routes.ClientController.productDetails(productId, size, color))
      }.recover {
        case _: Exception => InternalServerError("Failed to add product to cart")
      }
    }.getOrElse {
      val requestedPage = routes.ClientController.productDetails(productId, size, color).url
      Future.successful(Redirect(routes.LoginController.login()).withSession("requestedPage" -> requestedPage))
    }
  }

  def viewCart(): Action[AnyContent] = Action.async { implicit request =>
    val userOption = request.session.get("email")
    userOption.map { userEmail =>
      // Fetch cart items
      val cartItemsFuture = cartDAO.all(userEmail)
      // Fetch product details for each cart item
      val cartItemsWithDetailsFuture: Future[Seq[(CartItem, Option[Product])]] = cartItemsFuture.flatMap { cartItems =>
        Future.sequence(cartItems.map { cartItem =>
          productDAO.findByIdSizeColor(cartItem.productId, cartItem.size, cartItem.color).map { productOption =>
            (cartItem, productOption)
          }
        })
      }
      cartItemsWithDetailsFuture.map { cartItemsWithDetails =>
        val cartItems = cartItemsWithDetails.map { case (cartItem, productOption) =>
          (cartItem, productOption.getOrElse(Product("", "", "", "", "", "", 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, "", ""))) // Default product if not found
        }
        Ok(views.html.clientCart(cartItems, userOption))
      }.recover {
        case _: Exception => InternalServerError("Failed to retrieve cart items or product details")
      }
    }.getOrElse {
      Future.successful(Redirect(routes.LoginController.login()))
    }
  }

  def removeFromCart(productId: String, size: String, color: String): Action[AnyContent] = Action.async { implicit request =>
    val userOption = request.session.get("email")
    userOption.map { userEmail =>
      cartDAO.delete(userEmail, productId, size, color).map { _ =>
        Redirect(routes.CartController.viewCart())
      }.recover {
        case _: Exception => InternalServerError("Failed to remove product from cart")
      }
    }.getOrElse {
      Future.successful(Redirect(routes.LoginController.login()))
    }
  }

  def changeQuantity(productId: String, size: String, color: String, newQuantity: Int): Action[AnyContent] = Action.async { implicit request =>
    val userOption = request.session.get("email")
    userOption.map { userEmail =>
      cartDAO.updateQuantity(userEmail, productId, size, color, newQuantity).map { _ =>
        Redirect(routes.CartController.viewCart())
      }.recover {
        case _: Exception => InternalServerError("Failed to change quantity of product in cart")
      }
    }.getOrElse {
      Future.successful(Redirect(routes.LoginController.login()))
    }
  }

  def placeOrder(): Action[AnyContent] = Action.async { implicit request =>
    val userOption = request.session.get("email")
    userOption match {
      case Some(userEmail) =>
        customerDAO.findByEmail(userEmail).flatMap {
          case Some(customer) =>
            val cartItemsFuture = cartDAO.all(userEmail)
            val cartItemsWithDetailsFuture = cartItemsFuture.flatMap { cartItems =>
              Future.sequence(cartItems.map { cartItem =>
                productDAO.findByIdSizeColor(cartItem.productId, cartItem.size, cartItem.color).map { productOption =>
                  (cartItem, productOption)
                }
              })
            }
            cartItemsWithDetailsFuture.flatMap { cartItemsWithDetails =>
              val cartItems = cartItemsWithDetails.map { case (cartItem, productOption) =>
                (cartItem, productOption.getOrElse(Product("", "", "", "", "", "", 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, "", "")))
              }
              if (cartItems.isEmpty) {
                Future.successful(BadRequest("Your cart is empty."))
              } else {
                val totalAmount: Double = cartItems.map { case (cartItem, product) =>
                  product.rentalPrice * cartItem.quantity
                }.sum
                val orderItems: Seq[OrderItems] = cartItems.map { case (cartItem, product) =>
                  OrderItems(product.productId, product.size, product.color, cartItem.quantity, "Confirmed")
                }
                val orderId = orderDAO.generateOrderId()
                val order = Order(
                  orderId = orderId,
                  customerId = customer.customerId,
                  orderAddress = customer.address,
                  total = totalAmount,
                  paymentId = s"${customer.customerId}$orderId",
                  orderItems = orderItems
                )
                orderDAO.insert(order).flatMap { _ =>
                  Future.sequence(cartItems.map { case (cartItem, product) =>
                    val updatedProduct = product.copy(quantity = product.quantity - cartItem.quantity)
                    productDAO.update(product.productId, product.size, product.color, product.categories, product.material, updatedProduct)
                  }).map { _ =>
                    cartDAO.deleteAll(userEmail)
                    Redirect(routes.CartController.orderConfirmation(order.orderId))
                  }
                }
              }
            }.recover {
              case _: Exception => InternalServerError("Failed to retrieve cart items or product details")
            }
          case None =>
            Future.successful(Redirect(routes.LoginController.login()))
        }
      case None =>
        Future.successful(Redirect(routes.LoginController.login()))
    }
  }

  // Define the orderConfirmation action
  def orderConfirmation(orderId: String): Action[AnyContent] = Action { implicit request =>
    val userOption = request.session.get("email")
    userOption.map { userEmail =>
      Ok(views.html.clientOrderConfirmation(orderId, userOption))
    }.getOrElse {
      Redirect(routes.LoginController.login())
    }
  }

}
