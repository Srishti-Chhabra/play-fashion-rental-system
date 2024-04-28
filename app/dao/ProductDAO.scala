package dao

import scala.concurrent.{ExecutionContext, Future}
import javax.inject.Inject
import play.api.db.slick.DatabaseConfigProvider
import play.api.db.slick.HasDatabaseConfigProvider
import slick.jdbc.JdbcProfile
import models.Product

class ProductDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {
  import profile.api._

  private val Products = TableQuery[ProductsTable]
  private val Categories = TableQuery[ProductCategoryTable]
  private val Materials = TableQuery[ProductMaterialTable]

  def all(): Future[Seq[Product]] = {
    val query = for {
      ((p, c), m) <- Products
        .joinLeft(Categories).on((p, c) => p.productId === c.productId && p.size === c.size && p.color === c.color)
        .joinLeft(Materials).on { case ((p, c), m) => m.productId === p.productId }
    } yield (p, c, m)

    val result = db.run(query.result).map { rows =>
      rows.groupBy(_._1).map { case (productRow, groupedRows) =>
        val categories = groupedRows.flatMap(_._2).map(_._4).distinct.mkString(", ")
        val materials = groupedRows.flatMap(_._3).map(_._4).distinct.mkString(", ")
        Product(
          productRow._1,
          productRow._2,
          productRow._3,
          productRow._4,
          productRow._5,
          productRow._6,
          productRow._7,
          productRow._8,
          productRow._9,
          productRow._10,
          productRow._11,
          productRow._12,
          productRow._13,
          categories,
          materials
        )
      }.toSeq
    }
    result
  }

  def insert(product: Product): Future[Any] = {
    // Split categories and materials on comma
    val categories = product.categories.split(", ")
    val materials = product.material.split(", ")

    val insertProductAction = (Products += (
      product.productId, product.productName, product.description, product.brand,
      product.size, product.color, product.actualPrice, product.discount, product.rentalPrice, product.securityCharges,
      product.convenienceFee, product.quantity, product.sustainabilityRatings
    )).map(_ => ())

    // Convert category insertions to DBIOAction[Unit, NoStream, Effect.Write]
    val insertCategoryActions = categories.map(category =>
      (Categories += (product.productId, product.size, product.color, category)).map(_ => ())
    )

    // Convert material insertions to DBIOAction[Unit, NoStream, Effect.Write]
    val insertMaterialActions = materials.map(material =>
      (Materials += (product.productId, product.size, product.color, material)).map(_ => ())
    )

    // Combine all insertion actions into a single DBIOAction
    val combinedAction: DBIO[Any] = insertProductAction.flatMap { _ =>
      DBIO.seq(insertCategoryActions ++ insertMaterialActions: _*)
    }

    // Execute the combined action
    db.run(combinedAction)
  }

  def delete(productId: String, size: String, color: String): Future[Int] = db.run(Products.filter(x => (x.productId === productId) && (x.size === size) && (x.color === color)).delete)

  def update(productId: String, size: String, color: String, categories: String, material: String, updatedProduct: Product): Future[Any] = {
    // Split categories and materials on comma

    val originalCategories = categories.split(", ")
    val originalMaterial = material.split(", ")

    val updatedCategories = updatedProduct.categories.split(", ")
    val updatedMaterial = updatedProduct.material.split(", ")

    val updateProductAction = if(productId==updatedProduct.productId && size==updatedProduct.size && color==updatedProduct.color) {
       (Products.filter(x => (x.productId === productId) && (x.size === size) && (x.color === color))
      .update(productId, updatedProduct.productName, updatedProduct.description, updatedProduct.brand,
      size, color, updatedProduct.actualPrice, updatedProduct.discount, updatedProduct.rentalPrice,
      updatedProduct.securityCharges, updatedProduct.convenienceFee, updatedProduct.quantity,
      updatedProduct.sustainabilityRatings)).map(_ => ())
    }
    else{
      Products.filter(x => (x.productId === updatedProduct.productId) && (x.size === updatedProduct.size) && (x.color === updatedProduct.color))
        .exists
        .result
        .flatMap { exists =>
          if (!exists) {
            (Products.filter(x => (x.productId === productId) && (x.size === size) && (x.color === color))
              .update(updatedProduct.productId, updatedProduct.productName, updatedProduct.description, updatedProduct.brand,
              updatedProduct.size, updatedProduct.color, updatedProduct.actualPrice, updatedProduct.discount, updatedProduct.rentalPrice,
              updatedProduct.securityCharges, updatedProduct.convenienceFee, updatedProduct.quantity,
              updatedProduct.sustainabilityRatings)).map(_ => ())
          } else {
            // Product already exists, print error
            println("Product already exists!")
            DBIO.successful(())
          }
        }
    }

    val updateCategoryActionsAdd = updatedCategories.map { category =>
      Categories
        .filter(x => (x.productId === productId) && (x.size === size) && (x.color === color) && (x.categoryName === category))
        .exists
        .result
        .flatMap { exists =>
          if (!exists) {
            (Categories += (productId, size, color, category)).map(_ => ())
          } else {
            DBIO.successful(())
          }
        }
    }

    val updateCategoryActionsDelete = originalCategories.map { category =>
      if (!updatedCategories.contains(category)) {
        Categories
          .filter(x => (x.productId === productId) && (x.size === size) && (x.color === color) && (x.categoryName === category))
          .delete
      } else {
        DBIO.successful(0) // Return a successful action that does nothing
      }
    }

    val updateMaterialActionsAdd = updatedMaterial.map { material =>
      Materials
        .filter(x => (x.productId === productId) && (x.size === size) && (x.color === color) && (x.materialName === material))
        .exists
        .result
        .flatMap { exists =>
          if (!exists) {
            (Materials += (productId, size, color, material)).map(_ => ())
          } else {
            DBIO.successful(())
          }
        }
    }

    val updateMaterialActionsDelete = originalMaterial.map { material =>
      if (!updatedMaterial.contains(material)) {
        Materials
          .filter(x => (x.productId === productId) && (x.size === size) && (x.color === color) && (x.materialName === material))
          .delete
      } else {
        DBIO.successful(0) // Return a successful action that does nothing
      }
    }

    val combinedAction1: DBIO[Any] = updateProductAction.flatMap { _ =>
      DBIO.seq(updateMaterialActionsAdd ++ updateMaterialActionsDelete: _*)
    }

    val combinedAction2: DBIO[Any] = updateProductAction.flatMap { _ =>
      DBIO.seq(updateCategoryActionsAdd ++ updateCategoryActionsDelete: _*)
    }

    val combinedAction3: DBIO[Any] = updateProductAction.flatMap { _ =>
      DBIO.seq(updateCategoryActionsAdd ++ updateCategoryActionsDelete ++ updateMaterialActionsAdd ++ updateMaterialActionsDelete: _*)
    }

    if(categories==updatedProduct.categories && material==updatedProduct.material){
      db.run(updateProductAction)
    }
    else if(categories==updatedProduct.categories){
      db.run(combinedAction1)
    }
    else if(material==updatedProduct.material){
      db.run(combinedAction2)
    }
    else{
      db.run(combinedAction3)
    }
  }

  def findByIdSizeColor(productId: String, size: String, color: String): Future[Option[Product]] = {
    all().map(x => x.find(x => x.productId == productId && x.size == size && x.color == color))
  }

  def getColorsById(productId: String): Future[List[String]] = {
    val query = Products.filter(_.productId === productId).map(_.color).distinct
    db.run(query.result).map(_.toList)
  }

  def getSizesByIdColor(productId: String, color: String): Future[List[String]] = {
    val query = Products.filter(p => p.productId === productId && p.color === color).map(_.size).distinct
    db.run(query.result).map(_.toList)
  }

  private class ProductsTable(tag: Tag) extends Table[(String,String,String,String,String,String,Double,Double,Double,Double,Double,Int,Int)](tag, "PRODUCT") {

    def productId = column[String]("PRODUCT_ID", O.PrimaryKey)
    def productName = column[String]("PRODUCT_NAME")
    def description = column[String]("DESCRIPTION")
    def brand = column[String]("BRAND")
    def size = column[String]("SIZE")
    def color = column[String]("COLOR")
    def actualPrice = column[Double]("ACTUAL_PRICE")
    def discount = column[Double]("DISCOUNT")
    def rentalPrice = column[Double]("RENTAL_PRICE")
    def securityCharges = column[Double]("SECURITY_CHARGES")
    def convenienceFee = column[Double]("CONVENIENCE_FEE")
    def quantity = column[Int]("QUANTITY")
    def sustainabilityRatings = column[Int]("SUSTAINABILITY_RATINGS")

    def * = (productId, productName, description, brand, size, color, actualPrice, discount, rentalPrice, securityCharges, convenienceFee, quantity, sustainabilityRatings)
    def pk = primaryKey("pk_product", (productId, size, color))
  }

  private class ProductCategoryTable(tag: Tag) extends Table[(String, String, String, String)](tag, "PRODUCT_CATEGORY") {

    def productId = column[String]("PRODUCT_ID")
    def size = column[String]("SIZE")
    def color = column[String]("COLOR")
    def categoryName = column[String]("CATEGORY_NAME")

    def * = (productId, size, color, categoryName)
    def pk = primaryKey("pk_product_category", (productId, size, color, categoryName))
    def product = foreignKey("fk_product_category_product", (productId, size, color), Products)(x => (x.productId, x.size, x.color), onDelete = ForeignKeyAction.Cascade)
  }

  private class ProductMaterialTable(tag: Tag) extends Table[(String, String, String, String)](tag, "PRODUCT_MATERIAL") {

    def productId = column[String]("PRODUCT_ID")
    def size = column[String]("SIZE")
    def color = column[String]("COLOR")
    def materialName = column[String]("MATERIAL_NAME")

    def * = (productId, size, color, materialName)
    def pk = primaryKey("pk_product_material", (productId, size, color, materialName))
    def product = foreignKey("fk_product_material_product", (productId, size, color), Products)(x => (x.productId, x.size, x.color), onDelete = ForeignKeyAction.Cascade)
  }
}