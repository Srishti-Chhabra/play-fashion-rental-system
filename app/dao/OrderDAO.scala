package dao

import scala.concurrent.{ExecutionContext, Future}
import javax.inject.Inject
import play.api.db.slick.DatabaseConfigProvider
import play.api.db.slick.HasDatabaseConfigProvider
import slick.jdbc.JdbcProfile
import models.{Order, OrderItems}
import java.util.UUID

class OrderDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile]{
  import profile.api._

  private val OrderDetails = TableQuery[OrderDetailsTable]
  private val OrderItemsInOrder = TableQuery[OrderItemsTable]

  def generateOrderId(): String = {
    // Generate a unique identifier for the order ID
    UUID.randomUUID().toString.replace("-", "").toUpperCase
  }

  def all(): Future[Seq[Order]] = {
    val query = for {
      (d, i) <- OrderDetails.joinLeft(OrderItemsInOrder).on(_.orderId === _.orderId)
    } yield (d, i)
    val result = db.run(query.result).map { rows =>
      rows.groupBy(_._1).map { case (detailsRow, itemsRows) =>
        val orderItems: Seq[OrderItems] = itemsRows.flatMap { itemRow =>
          for {
            productId <- itemRow._2.map(_._2)
            size <- itemRow._2.map(_._3)
            color <- itemRow._2.map(_._4)
            quantity <- itemRow._2.map(_._5)
            orderStatus <- itemRow._2.map(_._6)
            // Add mappings for other fields as needed
          } yield OrderItems(
            productId,
            size,
            color,
            quantity,
            orderStatus
          )
        }

        Order(
          orderId = detailsRow._1,
          customerId = detailsRow._2,
          orderAddress = detailsRow._3,
          total = detailsRow._4,
          paymentId = detailsRow._5,
          orderItems = orderItems
        )
      }.toSeq
    }
    result
  }

  def insert(order: Order): Future[Any] = {
    val insertOrderAction = (OrderDetails += (
      order.orderId, order.customerId, order.orderAddress, order.total, order.paymentId
    )).map(_ => ())

    val orderItems = order.orderItems

    val insertOrderItemsActions = orderItems.map(orderItem =>
      (OrderItemsInOrder += (order.orderId, orderItem.productId, orderItem.size, orderItem.color,
        orderItem.quantity, orderItem.orderStatus)).map(_ => ())
    )

    val combinedAction: DBIO[Any] = insertOrderAction.flatMap { _ =>
      DBIO.seq(insertOrderItemsActions: _*)
    }

    db.run(combinedAction)
  }

  def updateStatus(orderId: String, productId: String, size: String, color: String, newStatus: String): Future[Int] = {
    val query = OrderItemsInOrder
      .filter(item => item.orderId === orderId && item.productId === productId && item.size === size && item.color === color)
      .map(_.orderStatus)
      .update(newStatus)

    db.run(query)
  }

  def findOrderItem(orderId: String, productId: String, size: String, color: String): Future[Option[OrderItems]] = {
    all().map { orders =>
      orders.find(_.orderId == orderId) match {
        case Some(order) =>
          order.orderItems.find(item =>
            item.productId == productId &&
              item.size == size &&
              item.color == color
          )
        case None => None
      }
    }
  }

  private class OrderDetailsTable(tag: Tag) extends Table[(String,String,String,Double,String)](tag, "ORDER_DETAILS") {
    def orderId = column[String]("ORDER_ID", O.PrimaryKey)
    def customerId = column[String]("CUSTOMER_ID")
    def orderAddress = column[String]("ORDER_ADDRESS")
    def total = column[Double]("TOTAL")
    def paymentId = column[String]("PAYMENT_ID")

    def * = (orderId,customerId,orderAddress,total,paymentId)
    def pk = primaryKey("pk_order_details", (orderId))
  }

  private class OrderItemsTable(tag: Tag) extends Table[(String,String,String,String,Double,String)](tag, "ORDER_ITEMS") {
    def orderId = column[String]("ORDER_ID", O.PrimaryKey)
    def productId = column[String]("PRODUCT_ID")
    def size = column[String]("SIZE")
    def color = column[String]("COLOR")
    def quantity = column[Double]("QUANTITY")
    def orderStatus = column[String]("ORDER_STATUS")

    def * = (orderId,productId,size,color,quantity,orderStatus)
    def pk = primaryKey("pk_order_items", (orderId,productId,size,color))
    }
}
