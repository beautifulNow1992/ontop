package org.semanticweb.ontop.pivotalrepr.impl;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.semanticweb.ontop.model.*;
import org.semanticweb.ontop.model.impl.OBDADataFactoryImpl;
import org.semanticweb.ontop.owlrefplatform.core.basicoperations.InjectiveVar2VarSubstitution;
import org.semanticweb.ontop.pivotalrepr.*;

/**
 * Renames query nodes according to one renaming substitution.
 */
public class QueryNodeRenamer extends HomogeneousQueryNodeTransformerImpl {

    private final static OBDADataFactory DATA_FACTORY = OBDADataFactoryImpl.getInstance();
    private final InjectiveVar2VarSubstitution renamingSubstitution;

    public QueryNodeRenamer(InjectiveVar2VarSubstitution renamingSubstitution) {
        this.renamingSubstitution = renamingSubstitution;
    }

    @Override
    public FilterNode transform(FilterNode filterNode) {
        return new FilterNodeImpl(renameBooleanExpression(
                filterNode.getOptionalFilterCondition().get()));
    }

    @Override
    public ExtensionalDataNode transform(ExtensionalDataNode extensionalDataNode) {
        return new ExtensionalDataNodeImpl(renameDataAtom(extensionalDataNode.getAtom()));
    }

    @Override
    public LeftJoinNode transform(LeftJoinNode leftJoinNode) {
        return new LeftJoinNodeImpl(renameOptionalBooleanExpression(
                leftJoinNode.getOptionalFilterCondition()));
    }

    @Override
    public UnionNode transform(UnionNode unionNode){
        return unionNode;
    }

    @Override
    public IntensionalDataNode transform(IntensionalDataNode intensionalDataNode) {
        return new IntensionalDataNodeImpl(renameDataAtom(intensionalDataNode.getAtom()));
    }

    @Override
    public InnerJoinNode transform(InnerJoinNode innerJoinNode) {
        return new InnerJoinNodeImpl(renameOptionalBooleanExpression(
                innerJoinNode.getOptionalFilterCondition()));
    }

    @Override
    public ConstructionNode transform(ConstructionNode constructionNode) {
        return new ConstructionNodeImpl(renameDataAtom(constructionNode.getProjectionAtom()),
                renameSubstitution(constructionNode.getSubstitution()),
                renameOptionalModifiers(constructionNode.getOptionalModifiers())
                );
    }

    @Override
    public GroupNode transform(GroupNode groupNode) {
        ImmutableList.Builder<NonGroundTerm> renamedTermBuilder = ImmutableList.builder();
        for (NonGroundTerm term : groupNode.getGroupingTerms()) {
            renamedTermBuilder.add(renamingSubstitution.applyToNonGroundTerm(term));
        }
        return new GroupNodeImpl(renamedTermBuilder.build());
    }

    private Optional<ImmutableQueryModifiers> renameOptionalModifiers(Optional<ImmutableQueryModifiers> optionalModifiers) {
        if (optionalModifiers.isPresent()) {
            return renamingSubstitution.applyToQueryModifiers(optionalModifiers.get());
        }
        else {
            return Optional.absent();
        }
    }

    private ImmutableBooleanExpression renameBooleanExpression(ImmutableBooleanExpression booleanExpression) {
        return renamingSubstitution.applyToBooleanExpression(booleanExpression);
    }


    private DataAtom renameDataAtom(DataAtom atom) {
        ImmutableList.Builder<VariableOrGroundTerm> argListBuilder = ImmutableList.builder();
        for (VariableOrGroundTerm term : atom.getVariablesOrGroundTerms()) {
            argListBuilder.add(renamingSubstitution.applyToVariableOrGroundTerm(term));
        }
        return DATA_FACTORY.getDataAtom(atom.getPredicate(), argListBuilder.build());
    }

    private Optional<ImmutableBooleanExpression> renameOptionalBooleanExpression(
            Optional<ImmutableBooleanExpression> optionalExpression) {
        if (!optionalExpression.isPresent())
            return Optional.absent();

        ImmutableBooleanExpression expression = optionalExpression.get();
        return Optional.of(renameBooleanExpression(expression));
    }

    private ImmutableSubstitution<ImmutableTerm> renameSubstitution(ImmutableSubstitution<ImmutableTerm> substitution) {
        return renamingSubstitution.applyRenaming(substitution);
    }
}
