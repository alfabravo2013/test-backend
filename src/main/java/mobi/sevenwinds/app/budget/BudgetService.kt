package mobi.sevenwinds.app.budget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mobi.sevenwinds.app.author.AuthorEntity
import mobi.sevenwinds.app.author.AuthorTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction

object BudgetService {
    suspend fun addRecord(body: BudgetRecordCreateRequest): BudgetRecord = withContext(Dispatchers.IO) {
        transaction {
            val author = body.authorId?.let { id ->
                AuthorEntity.findById(id) ?: throw IllegalArgumentException("Автор id=$id не существует")
            }
            val entity = BudgetEntity.new {
                this.year = body.year
                this.month = body.month
                this.amount = body.amount
                this.type = body.type
                this.author = author
            }

            return@transaction entity.toResponse()
        }
    }

    suspend fun getYearStats(param: BudgetYearParam): BudgetYearStatsResponse = withContext(Dispatchers.IO) {
        transaction {
            val withBudgetRecordFilter = buildFilterFrom(param)

            val itemsQuery = BudgetTable
                .leftJoin(AuthorTable)
                .select { withBudgetRecordFilter }
                .orderBy(BudgetTable.month, SortOrder.ASC)
                .orderBy(BudgetTable.amount, SortOrder.DESC)
                .limit(param.limit, param.offset)

            val data = BudgetEntity.wrapRows(itemsQuery).map { it.toResponse() }

            val total = BudgetTable
                .leftJoin(AuthorTable)
                .select { withBudgetRecordFilter }
                .count()

            val sum = BudgetTable.amount.sum()
            val sumByType = BudgetTable
                .leftJoin(AuthorTable)
                .slice(BudgetTable.type, sum)
                .select { withBudgetRecordFilter }
                .groupBy(BudgetTable.type)
                .associate { resultRow -> resultRow[BudgetTable.type].name to (resultRow[sum] ?: 0) }

            return@transaction BudgetYearStatsResponse(
                total = total,
                totalByType = sumByType,
                items = data
            )
        }
    }

    private fun buildFilterFrom(param: BudgetYearParam): Op<Boolean> {
        val yearFilter = BudgetTable.year eq param.year

        return if (param.authorName != null) {
            val uppercaseName = param.authorName.toUpperCase()
            val authorNameFilter = AuthorTable.fullName.upperCase().like("%$uppercaseName%")
            yearFilter and authorNameFilter
        } else {
            yearFilter
        }
    }
}
