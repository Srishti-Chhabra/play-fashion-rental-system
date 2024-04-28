package models

case class OrderItems (
                        productId: String,
                        size: String,
                        color: String,
                        quantity: Double,
                        orderStatus: String
                      )
