package dao

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import models.{CartItem, Product}

class CartDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  private val cart = TableQuery[CartTable]

  def all(email: String): Future[Seq[CartItem]] = {
    val query = cart.filter(_.email === email)
    val result = db.run(query.result).map(rows =>
      rows.map(row => CartItem(row._1,row._2,row._3,row._4,row._5))
    )
    result
  }

  def insert(email: String, productId: String, size: String, color: String): Future[Any] = {
    val query = cart.filter(x => x.email === email && x.productId === productId && x.size === size && x.color === color)

    db.run(query.result.headOption).flatMap {
      case Some(existingProduct) =>
        val updatedQuantity = existingProduct._5 + 1
        db.run(query.update(existingProduct._1,existingProduct._2,existingProduct._3,existingProduct._4,updatedQuantity))
      case None =>
        db.run(cart += (email, productId, size, color, 1))
    }
  }

  def delete(email: String, productId: String, size: String, color: String): Future[Int] = {
    val query = cart.filter(x => x.email === email && x.productId === productId && x.size === size && x.color === color)
    db.run(query.result.headOption).flatMap {
      case Some(existingProduct) =>
        db.run(query.delete)
    }
  }

  def deleteAll(email: String): Future[Int] = {
    val query = cart.filter(_.email === email)
    db.run(query.delete)
  }

  def updateQuantity(email: String, productId: String, size: String, color: String, newQuantity: Int): Future[Int] = {
    val query = cart.filter(x => x.email === email && x.productId === productId && x.size === size && x.color === color)
    db.run(query.update((email, productId, size, color, newQuantity)))
  }

  private class CartTable(tag: Tag) extends Table[(String,String,String,String,Int)](tag, "CART") {
    def email = column[String]("EMAIL", O.PrimaryKey)
    def productId = column[String]("PRODUCT_ID")
    def size = column[String]("SIZE")
    def color = column[String]("COLOR")
    def quantity = column[Int]("QUANTITY")

    def * = (email,productId,size,color,quantity)
    def pk = primaryKey("pk_cart", (email,productId,size,color))
  }
}
