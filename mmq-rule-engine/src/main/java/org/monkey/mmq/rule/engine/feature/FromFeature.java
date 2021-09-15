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

package org.monkey.mmq.rule.engine.feature;

import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;
import org.monkey.mmq.rule.engine.ReactorQLContext;
import org.monkey.mmq.rule.engine.ReactorQLMetadata;
import org.monkey.mmq.rule.engine.ReactorQLRecord;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * 数据源支持,用于自定义from实现
 *
 * @author solley
 * @see FeatureId.From#of(String)
 * @since 1.0.0
 */
public interface FromFeature extends Feature {

    Function<ReactorQLContext, Flux<ReactorQLRecord>> createFromMapper(FromItem fromItem, ReactorQLMetadata metadata);

    static Function<ReactorQLContext, Flux<ReactorQLRecord>> createFromMapperByFrom(FromItem body, ReactorQLMetadata metadata) {
        if (body == null) {
            return ctx -> ctx.getDataSource(null).map(val -> ReactorQLRecord.newRecord(null, val, ctx));
        }
        AtomicReference<Function<ReactorQLContext, Flux<ReactorQLRecord>>> ref = new AtomicReference<>();

        body.accept(new FromItemVisitorAdapter() {
            @Override
            public void visit(Table table) {
                ref.set(metadata.getFeatureNow(FeatureId.From.table)
                        .createFromMapper(table, metadata));
            }

            @Override
            public void visit(SubSelect subSelect) {
                ref.set(metadata.getFeatureNow(FeatureId.From.subSelect)
                        .createFromMapper(subSelect, metadata));
            }

            @Override
            public void visit(ValuesList valuesList) {
                ref.set(metadata.getFeatureNow(FeatureId.From.values)
                        .createFromMapper(valuesList, metadata));
            }

            @Override
            public void visit(TableFunction tableFunction) {
                ref.set(metadata.getFeatureNow(FeatureId.From.of(tableFunction.getFunction().getName()), tableFunction::toString)
                        .createFromMapper(tableFunction, metadata));
            }

            @Override
            public void visit(ParenthesisFromItem aThis) {
                ref.set(createFromMapperByFrom(aThis.getFromItem(), metadata));
            }
        });
        if (ref.get() == null) {
            throw new UnsupportedOperationException("不支持的查询:" + body);
        }
        return ref.get();
    }

    static Function<ReactorQLContext, Flux<ReactorQLRecord>> createFromMapperByBody(SelectBody body, ReactorQLMetadata metadata) {

        FromItem from = null;
        if (body instanceof PlainSelect) {
            PlainSelect select = ((PlainSelect) body);
            from = select.getFromItem();
        }
        return createFromMapperByFrom(from, metadata);
    }
}