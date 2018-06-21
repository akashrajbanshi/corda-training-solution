package net.corda.training.state

import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.finance.POUNDS
import java.util.*

/**
 * This is where you'll add the definition of your state object. Look at the unit tests in [IOUStateTests] for
 * instructions on how to complete the [IOUState] class.
 *
 * Remove the "val data: String = "data" property before starting the [IOUState] tasks.
 */
data class IOUState(val amount: Amount<Currency>,
                    val lender: Party,
                    val borrower: Party,
        //val paid: Amount<Currency> = 0.POUNDS,
        //amount.token will give the same currency type as defined in the amount value above
                    val paid: Amount<Currency> = Amount(0, amount.token),
                    override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState {

    /**
     * this holds the data for Party i.e lender and borrower
     */
    override val participants: List<Party> get() = listOf(lender, borrower)

    /**
     *returns the total amount to be paid
     */
    fun pay(payableAmount: Amount<Currency>) = copy(paid = paid + (payableAmount))
    //copy(paid = paid.plus(payableAmount)
    /**
     * returns the lender that has been added to the lender value
     */
    fun withNewLender(additionalLender: Party) = copy(lender = additionalLender)
}