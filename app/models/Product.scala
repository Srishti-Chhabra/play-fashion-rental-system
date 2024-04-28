package models

import play.api.libs.json._

case class Product(
                    productId: String,
                    productName: String,
                    description: String,
                    brand: String,
                    size: String,
                    color: String,
                    actualPrice: Double,
                    discount: Double,
                    rentalPrice: Double,
                    securityCharges: Double,
                    convenienceFee: Double,
                    quantity: Int,
                    sustainabilityRatings: Int,
                    categories: String,
                    material: String
                  )

object Product {
  implicit val productFormat: Format[Product] = Json.format[Product]
}