package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.requireThat
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.training.contract.IOUContract
import net.corda.training.state.IOUState

/**
 * This is the flow which handles issuance of new IOUs on the ledger.
 * Gathering the counterparty's signature is handled by the [CollectSignaturesFlow].
 * Notarisation (if required) and commitment to the ledger is handled by the [FinalityFlow].
 * The flow returns the [SignedTransaction] that was committed to the ledger.
 */
@InitiatingFlow
@StartableByRPC
class IOUIssueFlow(val state: IOUState) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities.get(0)

        val transactionBuilder = TransactionBuilder(notary)
        /*
           ========================================================
           Gets the lender and borrower from the participants list
           ========================================================
           val lender = state.participants.get(0).owningKey
           val borrower = state.participants.get(1).owningKey
           listOf(lender,borrower)
           =======================================================================================
           Converts the participants list to Public Key list based on the participants' public key
           =======================================================================================
           state.participants.map { it.owningKey }
           ===================================================================
           Directly gets the owning key for lender and borrower and list it
           ================================================================
           val lender = state.lender.owningKey
           val borrower = state.borrower.owningKey
           listOf(lender,borrower)
        */

        //using any of the above method to get the list of owning key of the participants
        val issueCommand = Command(IOUContract.Commands.Issue(), state.participants.map { it.owningKey })

        //adding command and output state in transaction
        transactionBuilder.addCommand(issueCommand)
        transactionBuilder.addOutputState(state, IOUContract.IOU_CONTRACT_ID);

        //verify the transaction
        transactionBuilder.verify(serviceHub);

        //partially signed transaction
        val partiallySignedTx = serviceHub.signInitialTransaction(transactionBuilder)

        //get the participants list
        val participantList = state.participants

        //gets the participants on the node who has already signed
        val currentNodeSigner = serviceHub.networkMapCache.getNodeByLegalIdentity(ourIdentity)!!.legalIdentities

        //removes the signed participants from the list
        val otherParticipants = participantList - currentNodeSigner;

        //starts the flow session
        val flowSession = otherParticipants.map { initiateFlow(it) }.toSet()

        //automates the sign process for the other participant
        val collectedSignatureFlow = CollectSignaturesFlow(partiallySignedTx, flowSession)

        //completely signed transaction
        val fullySignedTx = subFlow(collectedSignatureFlow)

        //finalize the transaction
        return subFlow(FinalityFlow(fullySignedTx));
    }
}

/**
 * This is the flow which signs IOU issuances.
 * The signing is handled by the [SignTransactionFlow].
 */
@InitiatedBy(IOUIssueFlow::class)
class IOUIssueFlowResponder(val flowSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an IOU transaction" using (output is IOUState)
            }
        }
        subFlow(signedTransactionFlow)
    }
}