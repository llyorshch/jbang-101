# End-to-end tests for the blog endpoints. Requires a running server:
#
#   hurl --test blog.hurl
#
# base_url defaults to http://localhost:7070 (set in mise.toml). To test another
# server, add --variable base_url=http://localhost:7171
#
# Entries run in order and share captured values. The suite creates its own
# posts and deletes them at the end, so it doesn't assume an empty database.
# A failed run can leave test posts behind: see README.md for using a
# throwaway database.


# --- Pages ----------------------------------------------------------------

# Home page

GET {{base_url}}/
HTTP 200
[Asserts]
header "Content-Type" contains "text/html"
xpath "string(//h1)" == "Blog"

# New post form ("new" must not be treated as a post id)

# Set base_url when running the notebook's Hurl file:
# hurl --test --variable base_url=http://localhost:7070 blog.hurl



GET {{base_url}}/posts/new
HTTP 200
[Asserts]
header "Content-Type" contains "text/html"
xpath "string(//h1)" == "New post"
xpath "string(//form/@method)" == "post"
xpath "string(//form/@action)" == "/posts"
xpath "//form//input[@name='title']" count == 1
xpath "//form//textarea[@name='body']" count == 1


# --- Create and read ------------------------------------------------------

# Creating a post redirects to it

POST {{base_url}}/posts
[Form]
title: First post
body: Line one\nLine two
HTTP 303
[Captures]
first_url: header "Location"
first_id: header "Location" regex "^/posts/(\\d+)$"

# The post shows what was submitted, with a working delete form

GET {{base_url}}{{first_url}}
HTTP 200
[Asserts]
header "Content-Type" contains "text/html"
xpath "string(//title)" == "First post"
xpath "string(//h1)" == "First post"
xpath "string(//p[@class='body'])" == "Line one\nLine two"
xpath "string(//form/@action)" == "/posts/{{first_id}}/delete"

# Surrounding whitespace is stripped

POST {{base_url}}/posts
[Form]
title: \u{20}\u{20}\u{20}Second post\u{20}\u{20}\u{20}
body: \u{20}\u{20}\u{20}Second body\u{20}\u{20}\u{20}
HTTP 303
[Captures]
second_url: header "Location"
second_id: header "Location" regex "^/posts/(\\d+)$"

GET {{base_url}}{{second_url}}
HTTP 200
[Asserts]
xpath "string(//h1)" == "Second post"
xpath "string(//p[@class='body'])" == "Second body"

# The home page lists posts newest first and links to them

GET {{base_url}}/
HTTP 200
[Asserts]
xpath "string(//ul/li[1]/a)" == "Second post"
xpath "string(//ul/li[1]/a/@href)" == "{{second_url}}"
xpath "string(//ul/li[2]/a)" == "First post"
xpath "string(//ul/li[2]/a/@href)" == "{{first_url}}"


# --- HTML escaping --------------------------------------------------------

POST {{base_url}}/posts
[Form]
title: <script>alert('title')</script>
body: <script>alert('body')</script> & "quotes"
HTTP 303
[Captures]
xss_url: header "Location"

# On the post page

GET {{base_url}}{{xss_url}}
HTTP 200
[Asserts]
body not contains "<script>"
body contains "<title>&lt;script&gt;alert(&#39;title&#39;)&lt;/script&gt;</title>"
body contains "<h1>&lt;script&gt;alert(&#39;title&#39;)&lt;/script&gt;</h1>"
body contains "&lt;script&gt;alert(&#39;body&#39;)&lt;/script&gt; &amp; &quot;quotes&quot;"

# On the home page

GET {{base_url}}/
HTTP 200
[Asserts]
body not contains "<script>"
body contains "&lt;script&gt;alert(&#39;title&#39;)&lt;/script&gt;"


# --- Validation -----------------------------------------------------------

# Missing title

POST {{base_url}}/posts
[Form]
body: Some body
HTTP 400
[Asserts]
body contains "title is required"

# Missing body

POST {{base_url}}/posts
[Form]
title: Some title
HTTP 400
[Asserts]
body contains "body is required"

# Blank title

POST {{base_url}}/posts
[Form]
title: \u{20}\u{20}\u{20}
body: Some body
HTTP 400
[Asserts]
body contains "title is required"

# Blank body

POST {{base_url}}/posts
[Form]
title: Some title
body: \u{20}\u{20}\u{20}
HTTP 400
[Asserts]
body contains "body is required"

# No form at all

POST {{base_url}}/posts
HTTP 400

# None of the rejected posts were saved

GET {{base_url}}/
HTTP 200
[Asserts]
body not contains "Some title"


# --- Unknown and invalid ids ----------------------------------------------

# Well-formed id with no post (largest possible value)

GET {{base_url}}/posts/9223372036854775807
HTTP 404

POST {{base_url}}/posts/9223372036854775807/delete
HTTP 404

# Not a number

GET {{base_url}}/posts/abc
HTTP 404

POST {{base_url}}/posts/abc/delete
HTTP 404

# Too large for a long

GET {{base_url}}/posts/99999999999999999999
HTTP 404

POST {{base_url}}/posts/99999999999999999999/delete
HTTP 404

# SQL injection attempt

GET {{base_url}}/posts/1%20OR%201=1
HTTP 404

# Unknown route

GET {{base_url}}/nope
HTTP 404


# --- Delete ---------------------------------------------------------------

# A GET must not delete anything

GET {{base_url}}/posts/{{first_id}}/delete
HTTP 404

GET {{base_url}}{{first_url}}
HTTP 200

# Deleting redirects home

POST {{base_url}}/posts/{{first_id}}/delete
HTTP 303
[Asserts]
header "Location" == "/"

# The post is gone

GET {{base_url}}{{first_url}}
HTTP 404

# Deleting it again fails

POST {{base_url}}/posts/{{first_id}}/delete
HTTP 404

# It's no longer listed, and the other post is untouched

GET {{base_url}}/
HTTP 200
[Asserts]
body not contains "First post"
body contains "Second post"

GET {{base_url}}{{second_url}}
HTTP 200


# --- Clean up -------------------------------------------------------------

POST {{base_url}}/posts/{{second_id}}/delete
HTTP 303

POST {{base_url}}{{xss_url}}/delete
HTTP 303