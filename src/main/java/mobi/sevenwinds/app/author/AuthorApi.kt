package mobi.sevenwinds.app.author

import com.papsign.ktor.openapigen.annotations.type.string.pattern.RegularExpression
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route


fun NormalOpenAPIRoute.author() {
    route("/author") {
        route("/add").post<Unit, AuthorCreateResponse, AuthorCreateRequest>(info("Добавить автора")) { _, body ->
            respond(AuthorService.addAuthor(body))
        }
    }
}

data class AuthorCreateRequest(
    @RegularExpression(pattern = "\\S+", "ФИО не должно быть пустым")
    val fullName: String
)

data class AuthorCreateResponse(
    val id: Int,
    val fullName: String,
    val createdAt: String
)
