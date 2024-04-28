package controllers

import javax.inject._
import play.api.mvc._
import play.api.i18n._
import play.api.data._
import play.api.data.Forms._
import play.api.data.format.Formats._
import models.Product
import dao.{ProductDAO,OrderDAO,CustomerDAO}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AdminDashboardController @Inject()(cc: ControllerComponents, productDAO: ProductDAO, orderDAO: OrderDAO, customerDAO: CustomerDAO, messagesApi: MessagesApi)
                                        (implicit ec: ExecutionContext) extends AbstractController(cc) with I18nSupport {


  // Authentication check
  private def isAdminAuthenticated(request: RequestHeader): Boolean = {
    request.session.get("email").contains("admin@gmail.com") // Change this email to the admin's email
  }

  // Product

  // Define a form for adding or editing products
  val productForm: Form[Product] = Form(
    mapping(
      "productId" -> nonEmptyText,
      "productName" -> nonEmptyText,
      "description" -> nonEmptyText,
      "brand" -> nonEmptyText,
      "size" -> nonEmptyText,
      "color" -> nonEmptyText,
      "actualPrice" -> of(doubleFormat),
      "discount" -> of(doubleFormat),
      "rentalPrice" -> of(doubleFormat),
      "securityCharges" -> of(doubleFormat),
      "convenienceFee" -> of(doubleFormat),
      "quantity" -> number,
      "sustainabilityRatings" -> number(min = 0, max = 5),
      "categories" -> nonEmptyText,
      "material" -> nonEmptyText
    )(Product.apply)(Product.unapply)
  )

  // Render the admin dashboard page
  def showProducts(): Action[AnyContent] = Action.async { implicit request =>
    if (isAdminAuthenticated(request)) {
      productDAO.all().map { products =>
        Ok(views.html.adminProducts(products))
      }
    } else {
      Future.successful(Redirect(routes.LoginController.login()))
    }
  }

  // Handle form submission to add a new product
  def showAddProductForm(): Action[AnyContent] = Action.async { implicit request =>
    if (isAdminAuthenticated(request)) {
      Future.successful(Ok(views.html.adminAddProduct(productForm)))
    } else {
      Future.successful(Redirect(routes.LoginController.login()))
    }
  }

  // Handle addition of new product
  def addProduct(): Action[AnyContent] = Action.async { implicit request =>
    if(isAdminAuthenticated(request)){
      productForm.bindFromRequest().fold(
        formWithErrors => {
          // Form validation failed, render admin dashboard with error messages
          productDAO.all().map { products =>
            BadRequest(views.html.adminProducts(products))
          }
        },
        product => {
          // Form validation passed, insert the new product into the database
          productDAO.insert(product).map { _ =>
            Redirect(routes.AdminDashboardController.showProducts())
              .flashing("success" -> "Product added successfully!")
          }
        }
      )
    }
    else{
      Future.successful(Redirect(routes.LoginController.login()))
    }
  }

  // Handle form submission to edit an existing product
  def showUpdateProductForm(productId: String, size: String, color: String): Action[AnyContent] = Action.async { implicit request =>
    if(isAdminAuthenticated(request)){
      productDAO.findByIdSizeColor(productId,size,color).map {
        case Some(product) =>
          val filledForm = productForm.fill(product)
          Ok(views.html.adminUpdateProduct(filledForm, product.productId, product.size, product.color, product.categories, product.material))
        case None =>
          NotFound("Product not found")
      }
    }
    else{
      Future.successful(Redirect(routes.LoginController.login()))
    }
  }

  //  Handle the product updating
  def updateProduct(productId: String, size: String, color: String, categories: String, material: String): Action[AnyContent] = Action.async { implicit request =>
    if(isAdminAuthenticated(request)){
      productForm.bindFromRequest().fold(
        formWithErrors => {
          // Form validation failed, render the editProduct view with errors
          Future.successful(BadRequest(views.html.adminUpdateProduct(formWithErrors, productId, size, color, categories, material)))
        },
        updatedProduct => {
          // Form validation passed, update the product in the database
          productDAO.update(productId, size, color, categories, material, updatedProduct).map { _ =>
            Redirect(routes.AdminDashboardController.showProducts())
              .flashing("success" -> "Product updated successfully!")
          }
        }
      )
    }
    else{
      Future.successful(Redirect(routes.LoginController.login()))
    }
  }

  // Handle deletion of a product
  def deleteProduct(productId: String, size: String, color: String): Action[AnyContent] = Action.async { implicit request =>
    if(isAdminAuthenticated(request)){
      productDAO.delete(productId, size, color).map { _ =>
        Redirect(routes.AdminDashboardController.showProducts())
          .flashing("success" -> "Product deleted successfully!")
      }
    }
    else{
      Future.successful(Redirect(routes.LoginController.login()))
    }
  }


  // Order

  // Render the admin dashboard page for orders
  def showOrders(): Action[AnyContent] = Action.async { implicit request =>
    if(isAdminAuthenticated(request)){
      orderDAO.all().map { orders =>
        Ok(views.html.adminOrders(orders))
      }
    }
    else{
      Future.successful(Redirect(routes.LoginController.login()))
    }
  }

  // Define the action to show the update status form
  def showUpdateStatusForm(orderId: String, productId: String, size: String, color: String): Action[AnyContent] = Action.async { implicit request =>
    if(isAdminAuthenticated(request)){
      // Fetch the order item based on orderId, productId, size, and color
      orderDAO.findOrderItem(orderId, productId, size, color).map {
        case Some(orderItem) =>
          // Render the update status form with the retrieved order item
          Ok(views.html.adminUpdateOrderItemStatus(orderId, orderItem))
        case None =>
          // If order item not found, return not found response
          NotFound("Order item not found")
      }.recover {
        case ex: Exception =>
          // Handle any exception that occurs during the process
          InternalServerError(s"An error occurred: ${ex.getMessage}")
      }
    }
    else{
      Future.successful(Redirect(routes.LoginController.login()))
    }
  }

  def updateStatus() = Action.async { implicit request =>
    if(isAdminAuthenticated(request)){
      val formData = request.body.asFormUrlEncoded
      formData.map { data =>
        val orderId = data("orderId").headOption.getOrElse("")
        val productId = data("productId").headOption.getOrElse("")
        val size = data("size").headOption.getOrElse("")
        val color = data("color").headOption.getOrElse("")
        val newStatus = data("status").headOption.getOrElse("")

        // Call your DAO method to update the status of the order item
        orderDAO.updateStatus(orderId, productId, size, color, newStatus).map { _ =>
          Redirect(routes.AdminDashboardController.showOrders())
            .flashing("success" -> "Status updated successfully")
        }.recover {
          case ex: Exception =>
            Redirect(routes.AdminDashboardController.showUpdateStatusForm(orderId,productId,size,color))
              .flashing("error" -> s"Failed to update status: ${ex.getMessage}")
        }
      }.getOrElse {
        Future.successful(
          Redirect(routes.AdminDashboardController.showOrders())
            .flashing("error" -> "Failed to update status: Form data missing")
        )
      }
    }
    else{
      Future.successful(Redirect(routes.LoginController.login()))
    }
  }

  // Customer

  // Render the admin dashboard page for customers
  def showCustomers(): Action[AnyContent] = Action.async { implicit request =>
    if(isAdminAuthenticated(request)){
      customerDAO.all().map { customers =>
        Ok(views.html.adminCustomers(customers))
      }
    }
    else{
      Future.successful(Redirect(routes.LoginController.login()))
    }
  }

}