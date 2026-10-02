ShopSphere — Phase 5 Beginner-Friendly Notes

Cart Management

Project

ShopSphere — Spring Boot E-commerce Backend

Phase 5 Goal

In Phase 5, we build the Cart functionality.

A cart allows a user to:

View their cart

Add a product to the cart

Increase the quantity of an existing product

Remove a product from the cart

View all items in the cart

Phase 5 is only about Cart.
Orders belong to Phase 6.

1. What is a Cart?

Imagine an online shopping website.

You see a Laptop and click:

Add to Cart

The product is not ordered yet.

It is simply stored in your shopping cart.

Example:

User: Cart Test User

Cart
├── Laptop × 2
└── Mouse × 1

Later, in Phase 6, the cart can be converted into an order.

2. Phase 5 Architecture

The basic flow is:

Postman
↓
CartController
↓
CartService
↓
CartRepository / CartItemRepository / ProductRepository
↓
MySQL

There are two important cart entities:

Cart
↓
CartItem
↓
Product

3. Why Do We Need Cart and CartItem?

A cart belongs to a user.

For example:

User 8
↓
Cart 1

But one cart can contain many products.

Cart 1
├── Laptop × 2
├── Mouse × 1
└── Keyboard × 3

Therefore, we use CartItem to represent each product inside the cart.

4. Cart Entity

The Cart entity connects the cart to a user.

Conceptually:

@Entity
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    private User user;
}

Meaning

Cart
├── id
└── user

The relationship means:

One User → One Cart

5. CartItem Entity

CartItem represents a product inside a cart.

Conceptually:

@Entity
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Cart cart;

    @ManyToOne
    private Product product;

    private Integer quantity;
}

Meaning

CartItem
├── id
├── cart
├── product
└── quantity

Example:

CartItem
├── cart = Cart 1
├── product = Laptop
└── quantity = 2

6. Database Relationship

The relationship looks like this:

User
│
│ 1
↓
Cart
│
│ 1
↓
CartItem
│
│ many
↓
Product

A more practical view:

users
┌────┬───────────────┐
│ id │ email         │
├────┼───────────────┤
│ 8  │ cartuser@...  │
└────┴───────────────┘
│
↓
cart
┌────┬─────────┐
│ id │ user_id │
├────┼─────────┤
│ 1  │ 8       │
└────┴─────────┘
│
↓
cart_item
┌────┬─────────┬────────────┬──────────┐
│ id │ cart_id │ product_id │ quantity │
├────┼─────────┼────────────┼──────────┤
│ 2  │ 1       │ 2          │ 2        │
└────┴─────────┴────────────┴──────────┘
│
↓
Product
Laptop

7. Repositories

We need repositories to communicate with the database.

CartRepository

public interface CartRepository
extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUserId(Long userId);
}

This allows us to find the cart using the user ID.

Example:

findByUserId(8)

means:

Find the cart belonging to user 8.

CartItemRepository

We need to find items belonging to a cart.

Example:

List<CartItem> findByCartId(Long cartId);

We also need to find a specific product inside a cart:

Optional<CartItem> findByCartIdAndProductId(
Long cartId,
Long productId
);

This is important when the user adds the same product again.

8. Adding a Product

Our endpoint is:

POST /api/cart/{userId}/add/{productId}?quantity=...

Example:

POST /api/cart/8/add/2?quantity=2

Meaning:

User ID     = 8
Product ID  = 2
Quantity    = 2

9. Add Product Flow

The complete flow is:

POST /api/cart/8/add/2?quantity=2
↓
CartController
↓
CartService
↓
Find User's Cart
↓
Find Product
↓
Is product already in cart?
/             \
YES              NO
↓                ↓
Increase quantity     Create CartItem
\             /
\           /
Save
↓
MySQL

10. What Happens When Product Is NOT Already in Cart?

Suppose the cart is empty.

Request:

POST /api/cart/8/add/2?quantity=2

The service creates:

CartItem
├── cart = Cart 1
├── product = Laptop
└── quantity = 2

Database:

cart_item

id | cart_id | product_id | quantity
---|---------|------------|---------
1  | 1       | 2          | 2

11. What Happens When Product Already Exists?

Suppose:

Laptop × 2

User adds:

Laptop × 3

We do NOT create another CartItem.

Instead:

2 + 3 = 5

The cart becomes:

Laptop × 5

This is why we check:

findByCartIdAndProductId(
cart.getId(),
productId
)

12. Removing a Product

Endpoint:

DELETE /api/cart/{userId}/remove/{productId}

Example:

DELETE /api/cart/8/remove/2

Flow:

Request
↓
CartController
↓
CartService
↓
Find user's Cart
↓
Find CartItem
↓
Delete CartItem
↓
Database

After deletion, the product is no longer in the cart.

13. Viewing the Cart

Endpoint:

GET /api/cart/{userId}

Example:

GET /api/cart/8

This finds the cart belonging to user 8.

14. Viewing Cart Items

Endpoint:

GET /api/cart/{userId}/items

Example:

GET /api/cart/8/items

This returns all CartItem records belonging to the user's cart.

Example:

[
{
"product": "Laptop",
"quantity": 2
},
{
"product": "Mouse",
"quantity": 1
}
]

15. CartController

The controller exposes the REST APIs.

Important endpoints:

GET
/api/cart/{userId}

POST
/api/cart/{userId}/add/{productId}?quantity=2

DELETE
/api/cart/{userId}/remove/{productId}

GET
/api/cart/{userId}/items

The controller's job is mainly:

Receive HTTP request
↓
Call CartService
↓
Return response

Business logic belongs in the service.

16. CartService

The service contains the actual cart logic.

For adding a product:

1. Find cart
2. Find product
3. Check whether product already exists
4. If it exists → increase quantity
5. Otherwise → create CartItem
6. Save CartItem

This is called business logic.

17. Why Do We Use a Service?

Without a service, the controller could become very large:

Controller
├── find cart
├── find product
├── check existing item
├── calculate quantity
├── save item
└── handle errors

Instead:

Controller
↓
Service
↓
Repository

This keeps responsibilities separated.

18. Cart Validation

During Phase 5, we also need to prevent invalid quantities.

For example:

quantity = 2  ✅
quantity = 1  ✅
quantity = 0  ❌
quantity = -1 ❌

The validation logic is:

if (quantity == null || quantity <= 0) {
throw new RuntimeException(
"Quantity must be greater than 0"
);
}

The API returns:

400 Bad Request

with:

Quantity must be greater than 0

19. Error: Product Not Found

If the requested product does not exist:

POST /api/cart/8/add/9999?quantity=1

we throw:

throw new ResourceNotFoundException(
"Product not found"
);

The global exception handler converts this into:

404 Not Found

This error-handling improvement was continued as part of Phase 7.

20. Authentication and Cart

ShopSphere uses JWT authentication.

The general request flow is:

Login
↓
JWT Token
↓
Postman Authorization Header
↓
Cart API

Header:

Authorization: Bearer YOUR_JWT_TOKEN

The cart endpoints are protected by Spring Security because they fall under:

.anyRequest().authenticated()

21. Important Testing Examples

Add Laptop

POST
http://localhost:8080/api/cart/8/add/2?quantity=2

Expected:

CartItem created

Add Same Laptop Again

POST
http://localhost:8080/api/cart/8/add/2?quantity=3

Existing quantity:

2

New quantity:

2 + 3 = 5

View Cart

GET
http://localhost:8080/api/cart/8

View Cart Items

GET
http://localhost:8080/api/cart/8/items

Remove Laptop

DELETE
http://localhost:8080/api/cart/8/remove/2

Expected:

Product removed from cart

22. Complete Phase 5 Flow

Remember this flow for interviews:

User
↓
Login
↓
JWT Token
↓
Add Product
↓
CartController
↓
CartService
↓
Find Cart
↓
Find Product
↓
Check existing CartItem
↓
Create / Increase quantity
↓
CartItemRepository
↓
MySQL

23. What I Learned in Phase 5

After completing Phase 5, you should understand:

Spring Boot

Controller

Service

Repository

REST endpoints

Path variables

Request parameters

JPA

@Entity

@OneToOne

@ManyToOne

JpaRepository

Derived query methods

Cart logic

Cart

CartItem

Adding products

Increasing quantity

Removing products

Viewing cart

Quantity validation

Security

JWT authentication

Protected cart endpoints

Authorization header

Database

Understanding the relationship:

User
↓
Cart
↓
CartItem
↓
Product

24. Interview Explanation

If an interviewer asks:

"Explain the Cart functionality in your project."

You can say:

"In my ShopSphere project, I implemented cart management using Spring Boot and JPA. Each user has a cart, and the cart contains multiple cart items. Each cart item stores the product and quantity. When a user adds a product, the service first checks whether that product already exists in the cart. If it exists, the quantity is increased; otherwise, a new cart item is created. I also implemented APIs to view and remove cart items and added validation to prevent invalid quantities."

25. Phase 5 vs Phase 6

Keep these separate.

Phase 5 — Cart

Cart
CartItem
Add product
Remove product
View cart
Quantity

Phase 6 — Orders

Order
OrderItem
Place order
View orders
Order status

The cart is the shopping area.

The order is created when the user decides to purchase.

26. Final Phase 5 Checklist

☑ Cart entity
☑ CartItem entity
☑ CartRepository
☑ CartItemRepository
☑ CartService
☑ CartController
☑ Add product
☑ Increase existing product quantity
☑ Remove product
☑ View cart
☑ View cart items
☑ Quantity validation
☑ JWT-protected cart APIs
☑ Tested with Postman

Phase 5 Status

COMPLETED ✅

The next phase in the ShopSphere roadmap is:

Phase 6 — Orders

Order
OrderItem
Place order
View orders
Order status