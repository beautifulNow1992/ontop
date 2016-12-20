package it.unibz.inf.ontop.owlrefplatform.core.optimization;

import com.google.common.collect.ImmutableSet;
import it.unibz.inf.ontop.model.ImmutableSubstitution;
import it.unibz.inf.ontop.model.ImmutableTerm;
import it.unibz.inf.ontop.model.Variable;
import it.unibz.inf.ontop.owlrefplatform.core.basicoperations.ImmutableSubstitutionImpl;
import it.unibz.inf.ontop.owlrefplatform.core.optimization.QueryNodeNavigationTools.NextNodeAndQuery;
import it.unibz.inf.ontop.pivotalrepr.*;
import it.unibz.inf.ontop.pivotalrepr.proposal.NodeCentricOptimizationResults;
import it.unibz.inf.ontop.pivotalrepr.proposal.SubstitutionPropagationProposal;
import it.unibz.inf.ontop.pivotalrepr.proposal.UnionLiftProposal;
import it.unibz.inf.ontop.pivotalrepr.proposal.impl.SubstitutionPropagationProposalImpl;
import it.unibz.inf.ontop.pivotalrepr.proposal.impl.UnionLiftProposalImpl;
import it.unibz.inf.ontop.utils.ImmutableCollectors;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static it.unibz.inf.ontop.owlrefplatform.core.optimization.QueryNodeNavigationTools.getDepthFirstNextNode;
import static it.unibz.inf.ontop.owlrefplatform.core.optimization.QueryNodeNavigationTools.getNextNodeAndQuery;
import static it.unibz.inf.ontop.pivotalrepr.NonCommutativeOperatorNode.ArgumentPosition.LEFT;
import static it.unibz.inf.ontop.pivotalrepr.NonCommutativeOperatorNode.ArgumentPosition.RIGHT;

/**
 * Optimizer to extract and propagate bindings in the query up and down the tree.
 * Uses {@link UnionFriendlyBindingExtractor}, {@link SubstitutionPropagationProposal} and {@link UnionLiftProposal}
 *
 */
public class TopDownBindingLiftOptimizer implements BindingLiftOptimizer {

    private final SimpleUnionNodeLifter lifter = new SimpleUnionNodeLifter();
    private final UnionFriendlyBindingExtractor extractor = new UnionFriendlyBindingExtractor();

    @Override
    public IntermediateQuery optimize(IntermediateQuery query) throws EmptyQueryException {
        // Non-final
        NextNodeAndQuery nextNodeAndQuery = new NextNodeAndQuery(
                query.getFirstChild(query.getRootConstructionNode()),
                query);

        //explore the tree lifting the bindings when it is possible
        while (nextNodeAndQuery.getOptionalNextNode().isPresent()) {
            nextNodeAndQuery = liftBindings(nextNodeAndQuery.getNextQuery(),
                    nextNodeAndQuery.getOptionalNextNode().get());

        }

        // remove unnecessary TrueNodes, which may have been introduced during substitution lift
        return new TrueNodesRemovalOptimizer().optimize(nextNodeAndQuery.getNextQuery());
    }

    private NextNodeAndQuery liftBindings(IntermediateQuery currentQuery, QueryNode currentNode)
            throws EmptyQueryException {

        if (currentNode instanceof ConstructionNode) {
            return liftBindingsFromConstructionNode(currentQuery, (ConstructionNode) currentNode);
        }
        else if (currentNode instanceof CommutativeJoinNode) {
            return liftBindingsFromCommutativeJoinNode(currentQuery, (CommutativeJoinNode) currentNode);
        }
        else if (currentNode instanceof LeftJoinNode) {
            return liftBindingsFromLeftJoinNode(currentQuery, (LeftJoinNode) currentNode);
        }
        else if (currentNode instanceof UnionNode) {
            return liftBindingsAndUnion(currentQuery, (UnionNode) currentNode);
        }
        /**
         * Other nodes: does nothing
         */
        else {
            return new NextNodeAndQuery(getDepthFirstNextNode(currentQuery, currentNode), currentQuery);
        }
    }

    /* Lift the bindings of the union to see if it is possible to simplify the tree.
      Otherwise try to lift the union to an ancestor with useful projected variables between its children
      (common with the conflicting bindings of the union).
      */
    private NextNodeAndQuery liftBindingsAndUnion(IntermediateQuery currentQuery, UnionNode initialUnionNode) throws EmptyQueryException {

        Optional<UnionNode> unionNode = Optional.of(initialUnionNode) ;
        QueryNode currentNode = initialUnionNode;


        //extract bindings (liftable bindings and conflicting one) from the union node
        final BindingExtractor.Extraction extraction = extractor.extractInSubTree(
                currentQuery, currentNode);

        //get liftable bindings
        Optional<ImmutableSubstitution<ImmutableTerm>> optionalSubstitution = extraction.getOptionalSubstitution();

        if (optionalSubstitution.isPresent()) {

            //try to lift the bindings up and down the tree
            SubstitutionPropagationProposal<UnionNode> proposal =
                    new SubstitutionPropagationProposalImpl<>(initialUnionNode, optionalSubstitution.get());

            NodeCentricOptimizationResults<UnionNode> results = currentQuery.applyProposal(proposal);
            currentQuery = results.getResultingQuery();
            unionNode = results.getOptionalNewNode();
            currentNode = results.getNewNodeOrReplacingChild()
                    .orElseThrow(() -> new IllegalStateException(
                            "The focus was expected to be kept or replaced, not removed"));

        }

        //if the union node has not been removed
        if (unionNode.isPresent()) {

            //variables of bindings that could not be returned because conflicting or not common in the subtree
            ImmutableSet<Variable> irregularVariables = extraction.getVariablesWithConflictingBindings();

            if(!irregularVariables.isEmpty()) {

                //try to lift the union
                return liftUnionToMatchingVariable(currentQuery, unionNode.get(), irregularVariables);
            }

        }

        return new NextNodeAndQuery(getDepthFirstNextNode(currentQuery, currentNode), currentQuery);


    }


    /*  Lift the union to an ancestor with useful projected variables between its children,
       These variables are common with the bindings of the union. */

    private NextNodeAndQuery liftUnionToMatchingVariable(IntermediateQuery currentQuery, UnionNode currentUnionNode, ImmutableSet<Variable> unionVariables) throws EmptyQueryException {


        Optional<QueryNode> parentNode = lifter.chooseLiftLevel(currentQuery, currentUnionNode, unionVariables);

        if(parentNode.isPresent()){

            UnionLiftProposal proposal = new UnionLiftProposalImpl(currentUnionNode, parentNode.get());
            NodeCentricOptimizationResults<UnionNode> results = currentQuery.applyProposal(proposal);
            currentQuery = results.getResultingQuery();
            currentUnionNode = results.getOptionalNewNode().orElseThrow(() -> new IllegalStateException(
                    "The focus node has to be a union node and be present"));

            return liftBindingsAndUnion(currentQuery, currentUnionNode);
        }

        //no parent with the given variable, I don't lift for the moment

        return new NextNodeAndQuery(getDepthFirstNextNode(currentQuery, currentUnionNode), currentQuery);



    }


    private NextNodeAndQuery liftBindingsFromConstructionNode(IntermediateQuery currentQuery,
                                                              ConstructionNode initialConstrNode)
            throws EmptyQueryException {

        QueryNode currentNode = initialConstrNode;

        Optional<QueryNode> parentNode = currentQuery.getParent(currentNode);
        if(parentNode.isPresent()){
            if (parentNode.get() instanceof UnionNode){
                return new NextNodeAndQuery(getDepthFirstNextNode(currentQuery, currentNode), currentQuery);
            }
        }

        //extract substitution from the construction node
        Optional<ImmutableSubstitution<ImmutableTerm>> optionalSubstitution = extractor.extractInSubTree(
                currentQuery, currentNode).getOptionalSubstitution();

        //propagate substitution up and down
        if (optionalSubstitution.isPresent()) {
            SubstitutionPropagationProposal<QueryNode> proposal =
                    new SubstitutionPropagationProposalImpl<>(currentNode, optionalSubstitution.get());

            NodeCentricOptimizationResults<QueryNode> results = currentQuery.applyProposal(proposal);
            return getNextNodeAndQuery(results);


        }

        return new NextNodeAndQuery(getDepthFirstNextNode(currentQuery, currentNode), currentQuery);
    }


    /** Lift the bindings for the commutative join
     * I extract the bindings (from union and construction nodes ) iterating through its children
     * @param currentQuery
     * @param initialJoinNode
     * @return
     * @throws EmptyQueryException
     */

    private NextNodeAndQuery liftBindingsFromCommutativeJoinNode(IntermediateQuery currentQuery,
                                                                 CommutativeJoinNode initialJoinNode)
            throws EmptyQueryException {

        // Non-final
        Optional<QueryNode> optionalCurrentChild = currentQuery.getFirstChild(initialJoinNode);
        QueryNode currentJoinNode = initialJoinNode;


        while (optionalCurrentChild.isPresent()) {
            QueryNode currentChild = optionalCurrentChild.get();

            Optional<ImmutableSubstitution<ImmutableTerm>> optionalSubstitution = extractor.extractInSubTree(
                    currentQuery, currentChild).getOptionalSubstitution();

            /**
             * Applies the substitution to the child
             */
            if (optionalSubstitution.isPresent()) {
                SubstitutionPropagationProposal<QueryNode> proposal =
                        new SubstitutionPropagationProposalImpl<>(currentChild, optionalSubstitution.get());

                NodeCentricOptimizationResults<QueryNode> results = currentQuery.applyProposal(proposal);
                currentQuery = results.getResultingQuery();
                optionalCurrentChild = results.getOptionalNextSibling();
                Optional<QueryNode> currentNode = results.getNewNodeOrReplacingChild();

                //node has not been removed
                if(currentNode.isPresent()) {
                    currentJoinNode = currentQuery.getParent(
                            currentNode
                                    .orElseThrow(() -> new IllegalStateException(
                                            "The focus was expected to be kept or replaced, not removed")))
                            .orElseThrow(() -> new IllegalStateException(
                                    "The focus node should still have a parent (a Join node)"));
                }
                else {

                    return getNextNodeAndQuery(results);

                }

            }
            else {
                optionalCurrentChild = currentQuery.getNextSibling(currentChild);
            }
        }


        return new NextNodeAndQuery(getDepthFirstNextNode(currentQuery, currentJoinNode), currentQuery);

    }


    /** Lift bindings from left node extracting first bindings coming from the left side of its subtree,
     * lift from the right only the bindings with variables that are not common with the left
     * @param currentQuery
     * @param initialLeftJoinNode
     * @return
     * @throws EmptyQueryException
     */
    private NextNodeAndQuery liftBindingsFromLeftJoinNode(IntermediateQuery currentQuery, LeftJoinNode initialLeftJoinNode) throws EmptyQueryException {

        QueryNode currentNode = initialLeftJoinNode;
        Optional<LeftJoinNode> currentJoinNode = Optional.of(initialLeftJoinNode);

        Optional<QueryNode> optionalLeftChild = currentQuery.getChild(currentNode, LEFT);


        //check bindings of the right side if there are some that are not projected in the second, they can be already pushed
        //substitution coming from the left have more importance than the one coming from the right
        if (optionalLeftChild.isPresent()) {

            Optional<ImmutableSubstitution<ImmutableTerm>> optionalSubstitution = extractor.extractInSubTree(
                    currentQuery, optionalLeftChild.get()).getOptionalSubstitution();

            /**
             * Applies the substitution to the join
             */
            if (optionalSubstitution.isPresent()) {
                SubstitutionPropagationProposal<LeftJoinNode> proposal =
                        new SubstitutionPropagationProposalImpl<>(initialLeftJoinNode, optionalSubstitution.get());

                NodeCentricOptimizationResults<LeftJoinNode> results = currentQuery.applyProposal(proposal);
                currentQuery = results.getResultingQuery();
                currentJoinNode = results.getOptionalNewNode();

                if(currentJoinNode.isPresent()){
                    currentNode = currentJoinNode.get();
                }
                else{
                    return getNextNodeAndQuery(results);
                }


            }
        }
        else
            {
            throw new IllegalStateException("Left Join needs to have a left child");
        }

        /* Current join node has not been removed lifting left side bindings.
          Extract the bindings also from the right child */

        if (currentJoinNode.isPresent()){

            Optional<QueryNode> optionalRightChild = currentQuery.getChild(currentNode, RIGHT);

            if (optionalRightChild.isPresent()) {
                QueryNode rightChild = optionalRightChild.get();

                Optional<ImmutableSubstitution<ImmutableTerm>> optionalSubstitution = extractor.extractInSubTree(
                        currentQuery, rightChild).getOptionalSubstitution();

                if(optionalSubstitution.isPresent()){

                //extract variables present only in the right child
                Optional<ImmutableSubstitutionImpl<ImmutableTerm>> substitutionRightMap =
                        getRightChildSubstitutionMap(currentQuery, currentNode, rightChild, optionalSubstitution);

                /**
                 * Propagate the bindings to the join
                 */
                if (substitutionRightMap.isPresent()) {
                    SubstitutionPropagationProposal<QueryNode> proposal =
                            new SubstitutionPropagationProposalImpl<>(currentNode, substitutionRightMap.get());

                    NodeCentricOptimizationResults<QueryNode> results = currentQuery.applyProposal(proposal);

                    return getNextNodeAndQuery(results);

                }
                }

            }
            else
            {
                throw new IllegalStateException("Left Join needs to have a right child");
            }


        }


        return new NextNodeAndQuery(getDepthFirstNextNode(currentQuery, currentNode), currentQuery);


    }

    /**
     * From the given substitutionMap returns only the bindings with variables that are contained in the right child of the left join
     * and do not appear in the left child
     */

    private Optional<ImmutableSubstitutionImpl<ImmutableTerm>> getRightChildSubstitutionMap(IntermediateQuery currentQuery, QueryNode currentNode, QueryNode rightChild, Optional<ImmutableSubstitution<ImmutableTerm>> optionalSubstitution) {

        Optional<QueryNode> optionalLeftChild;
        Set<Variable> onlyRightVariables = new HashSet<>();

        onlyRightVariables.addAll(currentQuery.getVariables(rightChild));
        optionalLeftChild = currentQuery.getChild(currentNode, LEFT);

        if(optionalLeftChild.isPresent()){
            onlyRightVariables.removeAll(currentQuery.getVariables(optionalLeftChild.get()));
        }
        else
        {
            throw new IllegalStateException("Left Join needs to have a left child");
        }

        //Get only the bindings referring to the right variables
        return Optional.of(optionalSubstitution.get()
                    .getImmutableMap().entrySet().stream()
                    .filter(binding -> onlyRightVariables.contains(binding.getKey()))
                    .collect(ImmutableCollectors.toMap()))
                .filter(m -> !m.isEmpty())
                .map(ImmutableSubstitutionImpl::new);
    }

}