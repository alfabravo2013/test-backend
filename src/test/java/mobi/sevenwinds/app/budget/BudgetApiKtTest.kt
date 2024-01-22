package mobi.sevenwinds.app.budget

import io.restassured.RestAssured
import mobi.sevenwinds.app.author.AuthorCreateRequest
import mobi.sevenwinds.app.author.AuthorCreateResponse
import mobi.sevenwinds.app.author.AuthorTable
import mobi.sevenwinds.common.ServerTest
import mobi.sevenwinds.common.jsonBody
import mobi.sevenwinds.common.toResponse
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BudgetApiKtTest : ServerTest() {

    @BeforeEach
    internal fun setUp() {
        transaction { BudgetTable.deleteAll() }
        transaction { AuthorTable.deleteAll() }
    }

    @Test
    fun testBudgetPagination() {
        addRecord(BudgetRecordCreateRequest(2020, 5, 10, BudgetType.Приход))
        addRecord(BudgetRecordCreateRequest(2020, 5, 5, BudgetType.Приход))
        addRecord(BudgetRecordCreateRequest(2020, 5, 20, BudgetType.Приход))
        addRecord(BudgetRecordCreateRequest(2020, 5, 30, BudgetType.Приход))
        addRecord(BudgetRecordCreateRequest(2020, 5, 40, BudgetType.Приход))
        addRecord(BudgetRecordCreateRequest(2030, 1, 1, BudgetType.Расход))

        RestAssured.given()
            .queryParam("limit", 3)
            .queryParam("offset", 1)
            .get("/budget/year/2020/stats")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                println("${response.total} / ${response.items} / ${response.totalByType}")

                Assert.assertEquals(5, response.total)
                Assert.assertEquals(3, response.items.size)
                Assert.assertEquals(105, response.totalByType[BudgetType.Приход.name])
            }
    }

    @Test
    fun testStatsSortOrder() {
        addRecord(BudgetRecordCreateRequest(2020, 5, 100, BudgetType.Приход))
        addRecord(BudgetRecordCreateRequest(2020, 1, 5, BudgetType.Приход))
        addRecord(BudgetRecordCreateRequest(2020, 5, 50, BudgetType.Приход))
        addRecord(BudgetRecordCreateRequest(2020, 1, 30, BudgetType.Приход))
        addRecord(BudgetRecordCreateRequest(2020, 5, 400, BudgetType.Приход))

        // expected sort order - month ascending, amount descending

        RestAssured.given()
            .get("/budget/year/2020/stats?limit=100&offset=0")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                println(response.items)

                Assert.assertEquals(30, response.items[0].amount)
                Assert.assertEquals(5, response.items[1].amount)
                Assert.assertEquals(400, response.items[2].amount)
                Assert.assertEquals(100, response.items[3].amount)
                Assert.assertEquals(50, response.items[4].amount)
            }
    }

    @Test
    fun testInvalidMonthValues() {
        RestAssured.given()
            .jsonBody(BudgetRecordCreateRequest(2020, -5, 5, BudgetType.Приход))
            .post("/budget/add")
            .then().statusCode(400)

        RestAssured.given()
            .jsonBody(BudgetRecordCreateRequest(2020, 15, 5, BudgetType.Приход))
            .post("/budget/add")
            .then().statusCode(400)
    }

    @Test
    fun testAddRecordsWithAuthorId() {
        val authorName = "Автор Авторович Авторов"
        val (authorId, createdAt) = addAuthor(AuthorCreateRequest(authorName))

        addRecord(BudgetRecordCreateRequest(2020, 5, 10, BudgetType.Приход, authorId))
        addRecord(BudgetRecordCreateRequest(2020, 5, 20, BudgetType.Приход))

        RestAssured.given()
            .get("/budget/year/2020/stats?limit=100&offset=0")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                println(response.items)

                assertNull(response.items[0].authorName)
                assertNull(response.items[0].createdAt)
                assertEquals(authorName, response.items[1].authorName)
                assertEquals(createdAt, response.items[1].createdAt)
            }
    }

    @Test
    fun testAddRecordsWithInvalidAuthorId() {
        val body = BudgetRecordCreateRequest(2020, 5, 10, BudgetType.Приход, 42)
        RestAssured.given()
            .jsonBody(body)
            .post("/budget/add")
            .then()
            .statusCode(400)
    }

    @Test
    fun testFilterBudgetRecordsByAuthorName() {
        val authorName = "Автор Авторович Авторов"
        val (authorId, _) = addAuthor(AuthorCreateRequest(authorName))

        addRecord(BudgetRecordCreateRequest(2020, 5, 10, BudgetType.Приход, authorId))
        addRecord(BudgetRecordCreateRequest(2020, 5, 20, BudgetType.Приход, authorId))
        addRecord(BudgetRecordCreateRequest(2020, 5, 30, BudgetType.Приход, authorId))
        addRecord(BudgetRecordCreateRequest(2020, 5, 40, BudgetType.Приход, authorId))
        addRecord(BudgetRecordCreateRequest(2020, 5, 50, BudgetType.Приход))

        RestAssured.given()
            .get("/budget/year/2020/stats?limit=3&offset=0&authorName=ВТОР")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                println("${response.total} / ${response.items} / ${response.totalByType}")

                Assert.assertEquals(4, response.total)
                Assert.assertEquals(3, response.items.size)
                Assert.assertEquals(100, response.totalByType[BudgetType.Приход.name])
            }
    }

    private fun addRecord(record: BudgetRecordCreateRequest) {
        RestAssured.given()
            .jsonBody(record)
            .post("/budget/add")
            .toResponse<BudgetRecord>().let { response ->
                assertEquals(record.year, response.year)
                assertEquals(record.month, response.month)
                assertEquals(record.amount, response.amount)
                assertEquals(record.type, response.type)

                val nullableFields = listOf(record.authorId, response.authorName, response.createdAt)
                assertTrue(nullableFields.all { it == null } || nullableFields.none { it == null })
            }
    }

    private fun addAuthor(author: AuthorCreateRequest): Pair<Int?, String?> {
        val response = RestAssured.given()
            .jsonBody(author)
            .post("/author/add")
            .toResponse<AuthorCreateResponse>()

        return Pair(response.id, response.createdAt)
    }
}
