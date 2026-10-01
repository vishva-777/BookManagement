ShopSphere — Phase 3 Notes

User Management & Role-Based Authorization

1. Phase 3 Goal

In Phase 3, ShopSphere was extended with user management and Spring Security authorization.

The main goals were:

Create users

Store passwords securely using BCrypt

Support USER and ADMIN roles

Create a login API

Connect Spring Security to the database

Load users from MySQL through CustomUserDetailsService

Use HTTP Basic Authentication

Apply role-based authorization to product APIs

2. Project Flow

The main architecture is:

Client / Postman
↓
Controller
↓
Service
↓
Repository
↓
MySQL

For Spring Security:

Request
↓
Spring Security
↓
CustomUserDetailsService
↓
UserRepository
↓
MySQL
↓
User + Password + Role
↓
Authentication
↓
Authorization

3. User Entity

The User entity represents a user stored in the database.

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
}

Important fields

Field

Purpose

id

Unique user ID

name

User's name

email

User's unique login identifier

password

BCrypt password hash

role

User's authorization role

4. Role Enum

Instead of storing arbitrary strings, roles are represented using an enum.

public enum Role {
USER,
ADMIN
}

The User entity uses:

@Enumerated(EnumType.STRING)
private Role role;

This tells JPA/Hibernate to store:

USER
ADMIN

rather than numeric enum positions.

5. UserRepository

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}

What does it do?

JpaRepository<User, Long> gives database operations for the User entity.

findByEmail()

Optional<User> findByEmail(String email);

Spring Data JPA creates the query automatically.

Conceptually:

SELECT *
FROM users
WHERE email = ?;

existsByEmail()

boolean existsByEmail(String email);

Checks whether an email already exists.

6. User Registration

The registration flow is:

POST /api/users/register
↓
UserController
↓
UserService
↓
Check email
↓
Set USER role
↓
BCrypt password
↓
Save user
↓
UserResponse

Important logic:

if (userRepository.existsByEmail(user.getEmail())) {
throw new RuntimeException("Email already registered");
}

user.setRole(Role.USER);
user.setPassword(passwordEncoder.encode(user.getPassword()));

User savedUser = userRepository.save(user);

Important security point

The public registration API forces:

user.setRole(Role.USER);

This prevents a client from simply sending:

{
"role": "ADMIN"
}

and becoming an administrator.

7. BCrypt Password Encryption

Passwords are not stored as plain text.

The application uses:

PasswordEncoder

with:

@Bean
public PasswordEncoder passwordEncoder() {
return new BCryptPasswordEncoder();
}

During registration:

user.setPassword(
passwordEncoder.encode(user.getPassword())
);

For example:

User enters:
123456

Database stores:
$2a$10$................

The original password is not stored.

8. UserResponse DTO

The API should not return the user's password.

@Data
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String name;
    private String email;
    private String role;
}

The service returns:

return new UserResponse(
savedUser.getId(),
savedUser.getName(),
savedUser.getEmail(),
savedUser.getRole().name()
);

Therefore the response contains:

{
"id": 5,
"name": "Test User",
"email": "testuser@shopsphere.com",
"role": "USER"
}

The password is not returned.

9. Login API

Login uses:

POST /api/users/login

Request:

{
"email": "testuser@shopsphere.com",
"password": "test123"
}

The service:

Finds the user by email.

Checks the supplied password against the BCrypt hash.

Returns UserResponse.

Password checking:

passwordEncoder.matches(
request.getPassword(),
user.getPassword()
)

10. CustomUserDetailsService

Spring Security needs a way to find users from our database.

That is why we created:

CustomUserDetailsService.java

It implements:

UserDetailsService

Code:

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found"));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .roles(user.getRole().name())
                .build();
    }
}

What happens here?

Spring Security asks:

"Find this user."
↓
loadUserByUsername(email)
↓
UserRepository
↓
MySQL

The database user is converted into a Spring Security UserDetails.

The information passed to Spring Security is:

Username → email
Password → BCrypt hash
Role     → USER / ADMIN

11. Authentication vs Authorization

This is one of the most important concepts.

Authentication

Authentication asks:

Who are you?

Example:

Email + Password
↓
Are the credentials valid?

If valid:

Authentication successful

Authorization

Authorization asks:

What are you allowed to do?

Example:

Role = USER
↓
Can USER create a product?
↓
No

12. SecurityConfig

Spring Security was configured using:

@Configuration
public class SecurityConfig {

The security filter chain includes:

http
.csrf(csrf -> csrf.disable())
.userDetailsService(customUserDetailsService)
.httpBasic(httpBasic -> {})
.authorizeHttpRequests(auth -> auth
.requestMatchers(
HttpMethod.GET,
"/api/products/**"
).permitAll()
.requestMatchers("/api/products/**")
.hasRole("ADMIN")
.anyRequest().permitAll()
);

Important rules

GET products

.requestMatchers(
HttpMethod.GET,
"/api/products/**"
).permitAll()

Anyone can view products.

Other product operations

.requestMatchers("/api/products/**")
.hasRole("ADMIN")

Product operations such as:

POST
PUT
DELETE

require the ADMIN role.

13. Why permitAll() and hasRole()?

permitAll()

Means:

Everyone is allowed.

Example:

GET /api/products

hasRole("ADMIN")

Means:

Only an authenticated ADMIN can access it.

Example:

POST /api/products

14. HTTP Basic Authentication

For Phase 3, HTTP Basic Authentication was used to understand the Spring Security authentication flow.

Postman:

Authorization
↓
Basic Auth
↓
Username + Password

Example:

Username: admin@shopsphere.com
Password: admin123

Spring Security then uses:

CustomUserDetailsService
↓
UserRepository
↓
MySQL

15. Admin Account

A default admin was created using CommandLineRunner.

@Bean
public CommandLineRunner createAdmin(
PasswordEncoder passwordEncoder,
UserRepository userRepository) {

    return args -> {

        if (!userRepository.existsByEmail(
                "admin@shopsphere.com")) {

            User admin = new User();

            admin.setName("Admin");
            admin.setEmail("admin@shopsphere.com");
            admin.setPassword(
                passwordEncoder.encode("admin123")
            );
            admin.setRole(Role.ADMIN);

            userRepository.save(admin);
        }
    };
}

The application checks whether the admin already exists before creating it.

16. Authorization Testing

ADMIN test

Credentials:

Username: admin@shopsphere.com
Password: admin123
Role: ADMIN

Request:

GET /api/products

Result:

200 OK

The ADMIN can access protected product operations.

USER test

A test user was created:

Username: testuser@shopsphere.com
Password: test123
Role: USER

GET products

GET /api/products

Result:

200 OK

Because product viewing is public.

POST product

POST /api/products

Result:

403 Forbidden

Because the endpoint requires:

ADMIN

17. 401 vs 403

Very important interview concept.

401 Unauthorized

Authentication failed.

Meaning:

"Your identity could not be verified."

Example:

Wrong username
Wrong password
User does not exist

403 Forbidden

Authentication succeeded, but the user does not have permission.

Example:

User is authenticated
Role = USER
Endpoint requires ADMIN
↓
403 Forbidden

The ShopSphere test demonstrated this successfully.

18. Final Phase 3 Flow

                    Client / Postman
                           |
                           ↓
                    HTTP Request
                           |
                           ↓
                  Spring Security
                           |
                           ↓
              CustomUserDetailsService
                           |
                           ↓
                    UserRepository
                           |
                           ↓
                        MySQL
                           |
                 +---------+---------+
                 |                   |
                 ↓                   ↓
          Authentication       User Role
                 |                   |
                 +---------+---------+
                           |
                           ↓
                     Authorization
                           |
              +------------+------------+
              |                         |
              ↓                         ↓
            USER                      ADMIN
              |                         |
    GET products                GET products
    ✅                          ✅
    POST products               POST products
    ❌                          ✅
    PUT products                PUT products
    ❌                          ✅
    DELETE products             DELETE products
    ❌                          ✅

19. Important Files Added/Updated in Phase 3

entity/
├── User.java
└── Role.java

repository/
└── UserRepository.java

service/
├── UserService.java
└── CustomUserDetailsService.java

dto/
├── LoginRequest.java
└── UserResponse.java

controller/
└── UserController.java

config/
└── SecurityConfig.java

20. Phase 3 Checklist

Create User entity

Create Role enum

Create UserRepository

Create registration API

Prevent duplicate email

Encrypt passwords with BCrypt

Force public registration to USER role

Create UserResponse DTO

Create login API

Create CustomUserDetailsService

Connect UserDetailsService to Spring Security

Enable HTTP Basic Authentication

Configure USER / ADMIN authorization

Allow GET products

Restrict product modification to ADMIN

Test ADMIN authentication

Test USER authorization

Verify 403 Forbidden for unauthorized product modification

21. Phase 3 Commit

Recommended Git commit:

git add .
git commit -m "feat: complete user management and role-based authorization"
git push

Commit message:

feat: complete user management and role-based authorization

22. Phase 4 Preview

Phase 3 uses HTTP Basic Authentication.

Phase 4 will introduce JWT (JSON Web Token) authentication.

The basic idea:

Login
↓
Email + Password
↓
Authentication
↓
Generate JWT
↓
Client receives JWT
↓
Client sends JWT with requests
↓
JWT validation
↓
USER / ADMIN
↓
Authorization

The Phase 4 implementation will be built step-by-step so the JWT authentication flow is understood rather than simply copied.