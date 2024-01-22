package mobi.sevenwinds.app.budget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mobi.sevenwinds.app.author.AuthorEntity
import mobi.sevenwinds.app.author.AuthorTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.sum
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
            val withBudgetYear = BudgetTable.year eq param.year

            val itemsQuery = BudgetTable
                .leftJoin(AuthorTable)
                .select { withBudgetYear }
                .orderBy(BudgetTable.month, SortOrder.ASC)
                .orderBy(BudgetTable.amount, SortOrder.DESC)
                .limit(param.limit, param.offset)

            val data = BudgetEntity.wrapRows(itemsQuery).map { it.toResponse() }

            val total = BudgetTable.select { withBudgetYear }.count()

            val sum = BudgetTable.amount.sum()
            val sumByType = BudgetTable.slice(BudgetTable.type, sum)
                .select { withBudgetYear }
                .groupBy(BudgetTable.type)
                .associate { resultRow -> resultRow[BudgetTable.type].name to (resultRow[sum] ?: 0) }

            return@transaction BudgetYearStatsResponse(
                total = total,
                totalByType = sumByType,
                items = data
            )
        }
    }
}
