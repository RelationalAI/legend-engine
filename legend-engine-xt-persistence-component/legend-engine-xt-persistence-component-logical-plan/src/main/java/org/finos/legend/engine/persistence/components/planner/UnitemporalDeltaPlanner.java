// Copyright 2022 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.engine.persistence.components.planner;

import org.finos.legend.engine.persistence.components.common.Datasets;
import org.finos.legend.engine.persistence.components.common.Resources;
import org.finos.legend.engine.persistence.components.common.StatisticName;
import org.finos.legend.engine.persistence.components.ingestmode.UnitemporalDelta;
import org.finos.legend.engine.persistence.components.ingestmode.merge.MergeStrategyVisitors;
import org.finos.legend.engine.persistence.components.logicalplan.LogicalPlan;
import org.finos.legend.engine.persistence.components.logicalplan.LogicalPlanFactory;
import org.finos.legend.engine.persistence.components.logicalplan.conditions.And;
import org.finos.legend.engine.persistence.components.logicalplan.conditions.Condition;
import org.finos.legend.engine.persistence.components.logicalplan.conditions.Exists;
import org.finos.legend.engine.persistence.components.logicalplan.conditions.Not;
import org.finos.legend.engine.persistence.components.logicalplan.conditions.Or;
import org.finos.legend.engine.persistence.components.logicalplan.datasets.Dataset;
import org.finos.legend.engine.persistence.components.logicalplan.datasets.Selection;
import org.finos.legend.engine.persistence.components.logicalplan.operations.Create;
import org.finos.legend.engine.persistence.components.logicalplan.operations.Insert;
import org.finos.legend.engine.persistence.components.logicalplan.operations.Operation;
import org.finos.legend.engine.persistence.components.logicalplan.operations.Update;
import org.finos.legend.engine.persistence.components.logicalplan.operations.UpdateAbstract;
import org.finos.legend.engine.persistence.components.logicalplan.values.DiffBinaryValueOperator;
import org.finos.legend.engine.persistence.components.logicalplan.values.FieldValue;
import org.finos.legend.engine.persistence.components.logicalplan.values.Pair;
import org.finos.legend.engine.persistence.components.logicalplan.values.Value;
import org.finos.legend.engine.persistence.components.util.Capability;
import org.finos.legend.engine.persistence.components.util.LogicalPlanUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.finos.legend.engine.persistence.components.common.StatisticName.INCOMING_RECORD_COUNT;
import static org.finos.legend.engine.persistence.components.common.StatisticName.ROWS_DELETED;
import static org.finos.legend.engine.persistence.components.common.StatisticName.ROWS_INSERTED;
import static org.finos.legend.engine.persistence.components.common.StatisticName.ROWS_TERMINATED;
import static org.finos.legend.engine.persistence.components.common.StatisticName.ROWS_UPDATED;

class UnitemporalDeltaPlanner extends UnitemporalPlanner
{
    private final Optional<String> deleteIndicatorField;
    private final List<Object> deleteIndicatorValues;

    private final Optional<Condition> deleteIndicatorIsNotSetCondition;
    private final Optional<Condition> deleteIndicatorIsSetCondition;
    private final Optional<Condition> dataSplitInRangeCondition;

    UnitemporalDeltaPlanner(Datasets datasets, UnitemporalDelta ingestMode, PlannerOptions plannerOptions)
    {
        super(datasets, ingestMode, plannerOptions);

        this.deleteIndicatorField = ingestMode.mergeStrategy().accept(MergeStrategyVisitors.EXTRACT_DELETE_FIELD);
        this.deleteIndicatorValues = ingestMode.mergeStrategy().accept(MergeStrategyVisitors.EXTRACT_DELETE_VALUES);

        this.deleteIndicatorIsNotSetCondition = deleteIndicatorField.map(field -> LogicalPlanUtils.getDeleteIndicatorIsNotSetCondition(stagingDataset(), field, deleteIndicatorValues));
        this.deleteIndicatorIsSetCondition = deleteIndicatorField.map(field -> LogicalPlanUtils.getDeleteIndicatorIsSetCondition(stagingDataset(), field, deleteIndicatorValues));
        this.dataSplitInRangeCondition = ingestMode.dataSplitField().map(field -> LogicalPlanUtils.getDataSplitInRangeCondition(stagingDataset(), field));
    }

    @Override
    protected UnitemporalDelta ingestMode()
    {
        return (UnitemporalDelta) super.ingestMode();
    }

    @Override
    public LogicalPlan buildLogicalPlanForIngest(Resources resources, Set<Capability> capabilities)
    {
        List<Operation> operations = new ArrayList<>();
        // Op 1: Milestone Records in main table
        operations.add(getMilestoningLogic());
        // Op 2: Insert records in main table
        operations.add(getUpsertLogic());

        return LogicalPlan.of(operations);
    }

    @Override
    public LogicalPlan buildLogicalPlanForPreActions(Resources resources)
    {
        return LogicalPlan.builder().addOps(
            Create.of(true, mainDataset()),
            Create.of(true, metadataDataset().orElseThrow(IllegalStateException::new).get()))
            .build();
    }

    @Override
    public Map<StatisticName, LogicalPlan> buildLogicalPlanForPreRunStatistics(Resources resources)
    {
        return Collections.emptyMap();
    }

    @Override
    public Map<StatisticName, LogicalPlan> buildLogicalPlanForPostRunStatistics(Resources resources)
    {
        Map<StatisticName, LogicalPlan> postRunStatisticsResult = new HashMap<>();

        if (options().collectStatistics())
        {
            //Incoming dataset record count
            postRunStatisticsResult.put(INCOMING_RECORD_COUNT,
                LogicalPlan.builder()
                    .addOps(LogicalPlanUtils.getRecordCount(stagingDataset(), INCOMING_RECORD_COUNT.get()))
                    .build());

            //Rows terminated = Rows invalidated in Sink - Rows updated
            postRunStatisticsResult.put(ROWS_TERMINATED,
                LogicalPlan.builder()
                    .addOps(Selection.builder()
                        .addFields(DiffBinaryValueOperator.of(getRowsInvalidatedInSink(), getRowsUpdated()).withAlias(ROWS_TERMINATED.get()))
                        .build())
                    .build());

            //Rows inserted (no previous active row with same primary key) = Rows added in sink - rows updated
            postRunStatisticsResult.put(ROWS_INSERTED,
                LogicalPlan.builder()
                    .addOps(Selection.builder()
                        .addFields(DiffBinaryValueOperator.of(getRowsAddedInSink(), getRowsUpdated()).withAlias(ROWS_INSERTED.get()))
                        .build())
                    .build());

            //Rows updated (when it is invalidated and a new row for same primary keys is added)
            postRunStatisticsResult.put(ROWS_UPDATED,
                LogicalPlan.builder().addOps(getRowsUpdated(ROWS_UPDATED.get())).build());

            //Rows Deleted = rows removed(hard-deleted) from sink table
            postRunStatisticsResult.put(ROWS_DELETED,
                LogicalPlanFactory.getLogicalPlanForConstantStats(ROWS_DELETED.get(), 0L));
        }

        return postRunStatisticsResult;
    }

    /*
    ------------------
    Upsert Logic:
    ------------------
    staging_columns: columns coming from staging
    milestone_columns: batch_id_in, batch_id_out, batch_time_in_utc, batch_time_out_utc

    INSERT INTO main_table (staging_columns, special_columns)
    SELECT {SELECT_LOGIC} from staging
    WHERE NOT EXISTS
    (batch_id_out = INF) and (DIGEST match) and (PKs match)
     */
    private Insert getUpsertLogic()
    {
        List<Value> columnsToInsert = new ArrayList<>();
        List<Value> stagingColumns = new ArrayList<>(stagingDataset().schemaReference().fieldValues());
        List<FieldValue> mileStoneColumns = transactionMilestoningFields();
        columnsToInsert.addAll(stagingColumns);
        columnsToInsert.addAll(mileStoneColumns);

        List<Value> columnsToSelect = new ArrayList<>(stagingColumns);
        deleteIndicatorField.ifPresent(deleteIndicatorField ->
        {
            LogicalPlanUtils.removeField(columnsToSelect, deleteIndicatorField);
            LogicalPlanUtils.removeField(columnsToInsert, deleteIndicatorField);
        });

        if (ingestMode().dataSplitField().isPresent())
        {
            LogicalPlanUtils.removeField(columnsToSelect, ingestMode().dataSplitField().get());
            LogicalPlanUtils.removeField(columnsToInsert, ingestMode().dataSplitField().get());
        }

        List<Value> milestoneUpdateValues = transactionMilestoningFieldValues();
        columnsToSelect.addAll(milestoneUpdateValues);

        Condition notExistsCondition = Not.of(Exists.of(
            Selection.builder()
                .source(mainDataset())
                .condition(And.builder().addConditions(openRecordCondition, digestMatchCondition, primaryKeysMatchCondition).build())
                .addAllFields(LogicalPlanUtils.ALL_COLUMNS())
                .build()));

        Condition selectCondition;
        if (deleteIndicatorField.isPresent())
        {
            if (dataSplitInRangeCondition.isPresent())
            {
                selectCondition = And.builder().addConditions(dataSplitInRangeCondition.get(), notExistsCondition, deleteIndicatorIsNotSetCondition.get()).build();
            }
            else
            {
                selectCondition = And.builder().addConditions(notExistsCondition, deleteIndicatorIsNotSetCondition.get()).build();
            }
        }
        else
        {
            if (dataSplitInRangeCondition.isPresent())
            {
                selectCondition = And.builder().addConditions(dataSplitInRangeCondition.get(), notExistsCondition).build();
            }
            else
            {
                selectCondition = notExistsCondition;
            }
        }

        Dataset selectStage = Selection.builder().source(stagingDataset()).condition(selectCondition).addAllFields(columnsToSelect).build();
        return Insert.of(mainDataset(), selectStage, columnsToInsert);
    }

    /*
    ------------------
    Milestoning Logic:
    ------------------
    UPDATE main_table (batch_id_out = {TABLE_BATCH_ID} - 1 , batch_time_out_utc = {BATCH_TIME})
    WHERE
    (batch_id_out = INF) and
    EXISTS (select * from staging where (data split in range) and (PKs match) and ((digest does not match) or (delete indicator is present)))
    */
    private Update getMilestoningLogic()
    {
        List<Pair<FieldValue, Value>> updatePairs = keyValuesForMilestoningUpdate();

        Condition digestCondition;
        if (deleteIndicatorIsSetCondition.isPresent())
        {
            digestCondition = Or.builder().addConditions(digestDoesNotMatchCondition, deleteIndicatorIsSetCondition.get()).build();
        }
        else
        {
            digestCondition = digestDoesNotMatchCondition;
        }

        Condition selectCondition;
        if (dataSplitInRangeCondition.isPresent())
        {
            selectCondition = And.builder().addConditions(dataSplitInRangeCondition.get(), primaryKeysMatchCondition, digestCondition).build();
        }
        else
        {
            selectCondition = And.builder().addConditions(primaryKeysMatchCondition, digestCondition).build();
        }

        Condition existsCondition = Exists.of(
            Selection.builder()
                .source(stagingDataset())
                .condition(selectCondition)
                .addAllFields(LogicalPlanUtils.ALL_COLUMNS())
                .build());

        Condition milestoningCondition = And.builder().addConditions(openRecordCondition, existsCondition).build();
        return UpdateAbstract.of(mainDataset(), updatePairs, milestoningCondition);
    }
}
