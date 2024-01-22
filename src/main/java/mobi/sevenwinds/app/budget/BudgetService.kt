package mobi.sevenwinds.app.budget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.transaction

object BudgetService {
    suspend fun addRecord(body: BudgetRecord): BudgetRecord = withContext(Dispatchers.IO) {
        transaction {
            val entity = BudgetEntity.new {
                this.year = body.year
                this.month = body.month
                this.amount = body.amount
                this.type = body.type
            }

            return@transaction entity.toResponse()
        }
    }

    suspend fun getYearStats(param: BudgetYearParam): BudgetYearStatsResponse = withContext(Dispatchers.IO) {
        transaction {
            val withBudgetYear = BudgetTable.year eq param.year
            val itemsQuery = BudgetTable
                .select { BudgetTable.year eq param.year }
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
