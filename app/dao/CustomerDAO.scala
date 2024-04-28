package dao

import scala.concurrent.{ExecutionContext, Future}
import javax.inject.Inject
import play.api.db.slick.DatabaseConfigProvider
import play.api.db.slick.HasDatabaseConfigProvider
import slick.jdbc.JdbcProfile
import models.Customer

class CustomerDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {
  import profile.api._

  private val Customers = TableQuery[CustomersTable]

  def all(): Future[Seq[Customer]] = db.run(Customers.result).map(rows => rows.map(row => Customer(row._1,row._2,row._3,row._4,row._5,row._6,row._7,row._8)))

  def insert(customer: Customer): Future[Any] = db.run(Customers += (
    customer.customerId,customer.firstName,customer.lastName,customer.email,
    customer.contactNumber,customer.address,customer.age,customer.gender
  )).map(_ => ())

  def delete(customerId: String): Future[Int] = db.run(Customers
    .filter(x => (x.customerId === customerId)).delete)

  def update(customerId: String,updatedCustomer: Customer): Future[Any] = {
    val updateCustomerAction = if(customerId==updatedCustomer.customerId) {
      (Customers.filter(x => (x.customerId === customerId))
        .update(customerId,updatedCustomer.firstName,updatedCustomer.lastName,updatedCustomer.email,
          updatedCustomer.contactNumber,updatedCustomer.address,updatedCustomer.age,updatedCustomer.gender)).map(_ => ())
    }
    else{
      Customers.filter(x => (x.customerId === updatedCustomer.customerId))
        .exists
        .result
        .flatMap { exists =>
          if (!exists) {
            (Customers.filter(x => (x.customerId === customerId))
              .update(updatedCustomer.customerId,updatedCustomer.firstName,updatedCustomer.lastName,updatedCustomer.email,
                updatedCustomer.contactNumber,updatedCustomer.address,updatedCustomer.age,updatedCustomer.gender)).map(_ => ())
          } else {
            // Customer already exists, print error
            println("Customer already exists!")
            DBIO.successful(())
          }
        }
    }
    db.run(updateCustomerAction)
  }

  def findById(customerId: String): Future[Option[Customer]] = {
    all().map(x => x.find(x => x.customerId == customerId))
  }

  def findByEmail(email: String): Future[Option[Customer]] = {
    all().map(x => x.find(x => x.email == email))
  }

  private class CustomersTable(tag: Tag) extends Table[(String,String,String,String,Int,String,Int,String)](tag, "CUSTOMER") {
    def customerId = column[String]("CUSTOMER_ID", O.PrimaryKey)
    def firstName = column[String]("FIRST_NAME")
    def lastName = column[String]("LAST_NAME")
    def email = column[String]("EMAIL")
    def contactNumber = column[Int]("CONTACT_NO")
    def address = column[String]("ADDRESS")
    def age = column[Int]("AGE")
    def gender = column[String]("GENDER")

    def * = (customerId,firstName,lastName,email,contactNumber,address,age,gender)
    def pk = primaryKey("pk_customer", (customerId))
  }
}