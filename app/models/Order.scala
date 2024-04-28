package models

case class Order (
                    orderId: String,
                    customerId: String,
                    orderAddress: String,
                    total: Double,
                    paymentId: String,
                    orderItems: Seq[OrderItems]
                 )
