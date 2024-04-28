package models

case class Customer (
                      customerId: String,
                      firstName: String,
                      lastName: String,
                      email: String,
                      contactNumber: Int,
                      address: String,
                      age: Int,
                      gender: String
                    )
