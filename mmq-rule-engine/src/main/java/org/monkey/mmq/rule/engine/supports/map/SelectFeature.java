/*
 * Copyright 2021-2021 Monkey Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.monkey.mmq.rule.engine.supports.map;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.SubSelect;
import org.monkey.mmq.rule.engine.ReactorQLContext;
import org.monkey.mmq.rule.engine.ReactorQLMetadata;
import org.monkey.mmq.rule.engine.ReactorQLRecord;
import org.monkey.mmq.rule.engine.feature.FeatureId;
import org.monkey.mmq.rule.engine.feature.FromFeature;
import org.monkey.mmq.rule.engine.feature.ValueMapFeature;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.function.Function;


public class SelectFeature implements ValueMapFeature {

    private final static String ID = FeatureId.ValueMap.select.getId();

    @Override
    public Function<ReactorQLRecord, Publisher<?>> createMapper(Expression expression, ReactorQLMetadata metadata) {
        SubSelect select = ((SubSelect) expression);

        String alias = select.getAlias() != null ? select.getAlias().getName() : null;

        Function<ReactorQLContext, Flux<ReactorQLRecord>> mapper = FromFeature.createFromMapperByFrom(select, metadata);

        return record -> mapper
                .apply(record.getContext()
                        .transfer((table, source) -> source
                                .map(val -> ReactorQLRecord
                                        .newRecord(alias, val, record.getContext())
                                        .addRecords(record.getRecords(false))))
                        .bindAll(record.getRecords(false))
                )
                .map(ReactorQLRecord::getRecord);

    }

    @Override
    public String getId() {
        return ID;
    }
}
