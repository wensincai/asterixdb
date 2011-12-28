package edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import edu.uci.ics.hyracks.algebricks.core.algebra.base.IHyracksJobBuilder;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.IOptimizationContext;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalVariable;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.PhysicalOperatorTag;
import edu.uci.ics.hyracks.algebricks.core.algebra.metadata.IDataSourceIndex;
import edu.uci.ics.hyracks.algebricks.core.algebra.metadata.IMetadataProvider;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.IOperatorSchema;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.IndexInsertDeleteOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.InsertDeleteOperator.Kind;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.OrderOperator.IOrder.OrderKind;
import edu.uci.ics.hyracks.algebricks.core.algebra.properties.ILocalStructuralProperty;
import edu.uci.ics.hyracks.algebricks.core.algebra.properties.IPartitioningProperty;
import edu.uci.ics.hyracks.algebricks.core.algebra.properties.IPartitioningRequirementsCoordinator;
import edu.uci.ics.hyracks.algebricks.core.algebra.properties.IPhysicalPropertiesVector;
import edu.uci.ics.hyracks.algebricks.core.algebra.properties.LocalOrderProperty;
import edu.uci.ics.hyracks.algebricks.core.algebra.properties.OrderColumn;
import edu.uci.ics.hyracks.algebricks.core.algebra.properties.PhysicalRequirements;
import edu.uci.ics.hyracks.algebricks.core.algebra.properties.StructuralPropertiesVector;
import edu.uci.ics.hyracks.algebricks.core.algebra.runtime.jobgen.impl.JobGenContext;
import edu.uci.ics.hyracks.algebricks.core.algebra.runtime.jobgen.impl.JobGenHelper;
import edu.uci.ics.hyracks.algebricks.core.api.constraints.AlgebricksPartitionConstraint;
import edu.uci.ics.hyracks.algebricks.core.api.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.core.utils.Pair;
import edu.uci.ics.hyracks.api.dataflow.IOperatorDescriptor;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.job.JobSpecification;

public class IndexInsertDeletePOperator extends AbstractPhysicalOperator {

    private List<LogicalVariable> primaryKeys;
    private List<LogicalVariable> secondaryKeys;
    private IDataSourceIndex<?, ?> dataSourceIndex;

    public IndexInsertDeletePOperator(List<LogicalVariable> primaryKeys, List<LogicalVariable> secondaryKeys,
            IDataSourceIndex<?, ?> dataSourceIndex) {
        this.primaryKeys = primaryKeys;
        this.secondaryKeys = secondaryKeys;
        this.dataSourceIndex = dataSourceIndex;
    }

    @Override
    public PhysicalOperatorTag getOperatorTag() {
        return PhysicalOperatorTag.INDEX_INSERT_DELETE;
    }

    @Override
    public void computeDeliveredProperties(ILogicalOperator op, IOptimizationContext context) {
        AbstractLogicalOperator op2 = (AbstractLogicalOperator) op.getInputs().get(0).getValue();
        deliveredProperties = (StructuralPropertiesVector) op2.getDeliveredPhysicalProperties().clone();
    }

    @Override
    public PhysicalRequirements getRequiredPropertiesForChildren(ILogicalOperator op,
            IPhysicalPropertiesVector reqdByParent) {
        List<LogicalVariable> scanVariables = new ArrayList<LogicalVariable>();
        scanVariables.addAll(primaryKeys);
        scanVariables.add(new LogicalVariable(-1));
        IPartitioningProperty pp = dataSourceIndex.getDataSource().getPropertiesProvider()
                .computePropertiesVector(scanVariables).getPartitioningProperty();
        List<ILocalStructuralProperty> orderProps = new LinkedList<ILocalStructuralProperty>();
        for (LogicalVariable k : secondaryKeys) {
            orderProps.add(new LocalOrderProperty(new OrderColumn(k, OrderKind.ASC)));
        }
        StructuralPropertiesVector[] r = new StructuralPropertiesVector[] { new StructuralPropertiesVector(pp,
                orderProps) };
        return new PhysicalRequirements(r, IPartitioningRequirementsCoordinator.NO_COORDINATION);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void contributeRuntimeOperator(IHyracksJobBuilder builder, JobGenContext context, ILogicalOperator op,
            IOperatorSchema propagatedSchema, IOperatorSchema[] inputSchemas, IOperatorSchema outerPlanSchema)
            throws AlgebricksException {
        IndexInsertDeleteOperator insertDeleteOp = (IndexInsertDeleteOperator) op;
        IMetadataProvider mp = context.getMetadataProvider();

        JobSpecification spec = builder.getJobSpec();
        RecordDescriptor inputDesc = JobGenHelper.mkRecordDescriptor(op.getInputs().get(0).getValue(), inputSchemas[0],
                context);

        Pair<IOperatorDescriptor, AlgebricksPartitionConstraint> runtimeAndConstraints = null;
        if (insertDeleteOp.getOperation() == Kind.INSERT)
            runtimeAndConstraints = mp.getIndexInsertRuntime(dataSourceIndex, propagatedSchema, primaryKeys,
                    secondaryKeys, inputDesc, context, spec);
        else
            runtimeAndConstraints = mp.getIndexDeleteRuntime(dataSourceIndex, propagatedSchema, primaryKeys,
                    secondaryKeys, inputDesc, context, spec);

        builder.contributeHyracksOperator(insertDeleteOp, runtimeAndConstraints.first);
        builder.contributeAlgebricksPartitionConstraint(runtimeAndConstraints.first, runtimeAndConstraints.second);
        ILogicalOperator src = insertDeleteOp.getInputs().get(0).getValue();
        builder.contributeGraphEdge(src, 0, insertDeleteOp, 0);
    }

    @Override
    public boolean isMicroOperator() {
        return false;
    }

}
